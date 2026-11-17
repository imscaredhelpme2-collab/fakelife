package com.example.fakelife;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {
    static final String[] PERMS = {
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS
    };

    private TextView status, cmds;
    private EditText userId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, "Setup");
        Ui.add(col, Ui.text(this,
                "1) Permissions. The call log and SMS permissions are 'restricted' for apps installed outside a store, "
                        + "so if the buttons below don't grant them, use the shell commands (step 2).", 13, Ui.DIM));
        status = Ui.text(this, "", 14, Ui.TEXT);
        Ui.add(col, status);
        Ui.add(col, Ui.button(this, "Request permissions", new View.OnClickListener() {
            @Override
            public void onClick(View v) { requestPermissions(PERMS, 5); }
        }));

        Ui.add(col, Ui.heading(this, "2) Shell commands (run from Termux with rish / Shizuku, as user 0)"), 0, 16, 0, 2);
        Ui.add(col, Ui.text(this,
                "Create the second user once with:  pm create-user AI   (it prints the new user id; then  am start-user ID). "
                        + "Enter that id here:", 13, Ui.DIM));
        userId = Ui.edit(this, "User id", "10", false, false);
        userId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Ui.add(col, userId);
        cmds = Ui.mono(this, "", 11);
        Ui.add(col, cmds);
        userId.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { refresh(); }
        });
        Ui.add(col, Ui.button(this, "Copy commands", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("fakelife", commands()));
                Toast.makeText(SetupActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            }
        }));

        Ui.add(col, Ui.heading(this, "3) Reading the texts"), 0, 16, 0, 2);
        Ui.add(col, Ui.text(this,
                "Android only lets the default SMS app write texts. Fake Life asks for the SMS role, writes everything that is "
                        + "waiting, and then you open your normal Messages app and tap 'Set as default' to hand it back. "
                        + "Texts written while Fake Life doesn't hold the role are queued, never lost.\n\n"
                        + "Messages must be installed for the second user. The pm install-existing line above does that "
                        + "(or install it from the Play Store inside that user).", 13, Ui.TEXT));

        Ui.add(col, Ui.heading(this, "4) Keep it alive"), 0, 16, 0, 2);
        Ui.add(col, Ui.text(this,
                "For live mode, set Fake Life to 'Unrestricted' battery usage in its App info, "
                        + "and allow notifications. Delayed texts use Android's job scheduler, so they can arrive a little late.", 13, Ui.TEXT));
        refresh();
    }

    private String commands() {
        String u = userId.getText().toString().trim();
        if (u.isEmpty()) u = "10";
        String pkg = getPackageName();
        StringBuilder sb = new StringBuilder();
        sb.append("pm install-existing --user ").append(u).append(" ").append(pkg).append("\n");
        sb.append("pm install-existing --user ").append(u).append(" com.google.android.apps.messaging\n");
        for (String p : new String[]{"READ_CONTACTS", "WRITE_CONTACTS", "READ_CALL_LOG", "WRITE_CALL_LOG",
                "READ_CALENDAR", "WRITE_CALENDAR", "READ_SMS", "POST_NOTIFICATIONS"})
            sb.append("pm grant --user ").append(u).append(" ").append(pkg).append(" android.permission.").append(p).append("\n");
        sb.append("appops set --user ").append(u).append(" ").append(pkg).append(" ACCESS_RESTRICTED_SETTINGS allow\n");
        sb.append("cmd role add-role-holder --user ").append(u).append(" android.app.role.SMS ").append(pkg).append("\n");
        sb.append("# optional: stop Doze from delaying live texts\n");
        sb.append("cmd deviceidle whitelist +").append(pkg).append("\n");
        return sb.toString();
    }

    private void refresh() {
        cmds.setText(commands());
        StringBuilder sb = new StringBuilder();
        for (String p : PERMS) {
            boolean ok = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
            sb.append(ok ? "[x] " : "[ ] ").append(p.substring(p.lastIndexOf('.') + 1)).append('\n');
        }
        status.setText(sb.toString());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
