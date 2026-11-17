package com.example.fakelife;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONObject;

/** Handles the Reply box on a live text notification: the user "types as the phone owner". */
public class ReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Bundle r = RemoteInput.getResultsFromIntent(i);
        if (r == null) return;
        CharSequence cs = r.getCharSequence("reply");
        String name = i.getStringExtra("name");
        String phone = i.getStringExtra("phone");
        if (cs == null || phone == null) return;
        String text = cs.toString().trim();
        if (text.isEmpty()) return;
        try {
            JSONObject m = new JSONObject();
            m.put("phone", phone);
            m.put("in", false);
            m.put("body", text);
            m.put("at", System.currentTimeMillis());
            Executor.deliverSms(c, m);
            World.userSaidAdd(c, name == null ? phone : name, text);
            World.ledger(c, "[" + Tm.fmt(System.currentTimeMillis()) + "] USER replied to " + name + ": " + text);
            Journal.add(c, "You replied to " + name + ": " + text);
        } catch (Exception e) {
            Journal.add(c, "Reply failed: " + e.getMessage());
        }
        Notifier.cancelSms(c, phone);
        if (Store.liveOn(c)) {
            Store.put(c, "nexttick", System.currentTimeMillis() + 20_000L);
            Scheduler.rescheduleAll(c);
        }
    }
}
