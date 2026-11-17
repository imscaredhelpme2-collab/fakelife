package com.example.fakelife;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_SMS_ROLE = 77;
    private TextView status, log;
    private Button liveBtn;
    private final java.util.ArrayList<Button> workButtons = new java.util.ArrayList<>();

    private interface Job { void run() throws Exception; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, "Fake Life");
        Ui.add(col, Ui.text(this, "Logfare writes a whole fake life onto this phone. Then you explore it.", 13, Ui.DIM));
        status = Ui.text(this, "", 14, Ui.TEXT);
        Ui.add(col, status, 0, 10, 0, 6);

        Ui.add(col, Ui.heading(this, "Prepare"), 0, 8, 0, 0);
        Ui.add(col, Ui.button(this, "Setup: permissions and shell commands", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SetupActivity.class)); }
        }));
        Ui.add(col, Ui.button(this, "Persona, Logfare key and settings", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        }));

        Ui.add(col, Ui.heading(this, "Build the past"), 0, 12, 0, 0);
        workButtons.add(Ui.button(this, "Generate next chapter", new View.OnClickListener() {
            public void onClick(View v) {
                background("Chapter", new Job() { public void run() throws Exception { Director.chapter(getApplicationContext()); } });
            }
        }));
        workButtons.add(Ui.button(this, "Backfill the whole life up to today", new View.OnClickListener() {
            public void onClick(View v) {
                background("Backfill", new Job() { public void run() throws Exception { Director.backfill(getApplicationContext()); } });
            }
        }));
        for (Button x : workButtons) Ui.add(col, x);

        Ui.add(col, Ui.heading(this, "Texts"), 0, 12, 0, 0);
        Ui.add(col, Ui.button(this, "Become default SMS app (then write queued texts)", new View.OnClickListener() {
            public void onClick(View v) { requestSmsRole(); }
        }));
        Ui.add(col, Ui.button(this, "Write queued texts now", new View.OnClickListener() {
            public void onClick(View v) {
                int n = Writer.flushPending(getApplicationContext());
                Toast.makeText(MainActivity.this, n + " texts written" + (Writer.isSmsDefault(MainActivity.this) ? "" : " (not the SMS app yet)"), Toast.LENGTH_LONG).show();
                refresh();
            }
        }));
        Ui.add(col, Ui.button(this, "Open Messages (to hand the SMS role back)", new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No Messages app found for this user", Toast.LENGTH_LONG).show();
                }
            }
        }));

        Ui.add(col, Ui.heading(this, "Live"), 0, 12, 0, 0);
        liveBtn = Ui.button(this, "", new View.OnClickListener() {
            public void onClick(View v) { toggleLive(); }
        });
        Ui.add(col, liveBtn);
        workButtons.add(Ui.button(this, "Live tick now (ask Logfare what happens next)", new View.OnClickListener() {
            public void onClick(View v) {
                background("Live tick", new Job() { public void run() throws Exception { Director.liveCycle(getApplicationContext(), true); } });
            }
        }));
        Ui.add(col, workButtons.get(2));

        Ui.add(col, Ui.heading(this, "Housekeeping"), 0, 12, 0, 0);
        Ui.add(col, Ui.button(this, "Reset story (keeps what is already on the phone)", new View.OnClickListener() {
            public void onClick(View v) { confirmReset(); }
        }));
        Ui.add(col, Ui.button(this, "Clear log", new View.OnClickListener() {
            public void onClick(View v) { Journal.clear(getApplicationContext()); refresh(); }
        }));
        Ui.add(col, Ui.text(this, "To wipe everything the app made, delete the second user in Android settings.", 12, Ui.DIM));

        Ui.add(col, Ui.heading(this, "Log"), 0, 14, 0, 2);
        log = Ui.mono(this, "", 11);
        Ui.add(col, log);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Journal.setListener(new Runnable() { public void run() { refresh(); } });
        Notifier.channel(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Journal.setListener(null);
    }

    private void refresh() {
        String persona = Store.personaName(this);
        boolean role = Writer.isSmsDefault(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Persona: ").append(persona.isEmpty() ? (Store.bible(this).isEmpty() ? "(none yet, see Settings)" : "(custom)") : persona).append('\n');
        sb.append("Chapters written: ").append(Store.chapterIdx(this))
                .append("   Contacts: ").append(World.contactCount(this)).append('\n');
        sb.append("SMS role: ").append(role ? "HELD by Fake Life" : "not held").append("   Queued texts: ").append(World.pendingCount(this)).append('\n');
        sb.append("Live mode: ").append(Store.liveOn(this) ? "ON" : "off")
                .append("   Planned events: ").append(World.scheduledCount(this));
        status.setText(sb.toString());
        liveBtn.setText(Store.liveOn(this) ? "Live mode: ON (tap to turn off)" : "Live mode: off (tap to turn on)");
        log.setText(Journal.text(this, 60));
    }

    private void background(final String label, final Job job) {
        if (!Director.BUSY.compareAndSet(false, true)) {
            Toast.makeText(this, "Already working, wait for the log to finish", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Button x : workButtons) x.setEnabled(false);
        Journal.add(this, label + " started");
        new Thread(new Runnable() {
            public void run() {
                try {
                    job.run();
                } catch (Throwable t) {
                    Journal.add(getApplicationContext(), "! " + label + " stopped: " + t.getMessage());
                } finally {
                    Director.BUSY.set(false);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            for (Button x : workButtons) x.setEnabled(true);
                            refresh();
                        }
                    });
                }
            }
        }).start();
    }

    private void toggleLive() {
        boolean on = !Store.liveOn(this);
        Store.put(this, "liveon", on);
        if (on) Store.put(this, "nexttick", System.currentTimeMillis() + 60_000L);
        Scheduler.rescheduleAll(this);
        Journal.add(this, on ? "Live mode on: first check-in in about a minute" : "Live mode off");
        refresh();
    }

    private void requestSmsRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
            Toast.makeText(this, "SMS role not available on this device", Toast.LENGTH_LONG).show();
            return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            onSmsRoleHeld();
            return;
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_SMS_ROLE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SMS_ROLE) {
            if (Writer.isSmsDefault(this)) onSmsRoleHeld();
            else Toast.makeText(this, "Role was not granted", Toast.LENGTH_LONG).show();
            refresh();
        }
    }

    private void onSmsRoleHeld() {
        int n = Writer.flushPending(getApplicationContext());
        Journal.add(this, "SMS role held. " + n + " queued texts written. Open Messages and tap 'Set as default' to hand it back.");
        Toast.makeText(this, n + " texts written. Now open Messages and set it as default again.", Toast.LENGTH_LONG).show();
        refresh();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset story?")
                .setMessage("Forgets the story so far, the contact list and the queues so a new persona can start. "
                        + "Things already written to the phone stay there.")
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Store.resetStory(getApplicationContext());
                        Store.put(MainActivity.this, "liveon", false);
                        Scheduler.rescheduleAll(MainActivity.this);
                        Journal.add(MainActivity.this, "Story reset");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
