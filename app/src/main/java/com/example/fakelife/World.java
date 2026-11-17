package com.example.fakelife;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** What the story has put on the phone so far, plus queues. Stored as one small JSON file. */
final class World {
    private static JSONObject cache;

    private static synchronized JSONObject load(Context c) {
        if (cache != null) return cache;
        try {
            File f = new File(c.getFilesDir(), "world.json");
            if (f.exists()) {
                byte[] b = new byte[(int) f.length()];
                try (FileInputStream in = new FileInputStream(f)) {
                    int off = 0;
                    while (off < b.length) {
                        int n = in.read(b, off, b.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                }
                cache = new JSONObject(new String(b, "UTF-8"));
            }
        } catch (Exception ignored) {
        }
        if (cache == null) cache = new JSONObject();
        return cache;
    }

    private static synchronized void save(Context c) {
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), "world.json"))) {
            out.write(load(c).toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private static JSONArray arr(Context c, String key) {
        JSONObject w = load(c);
        JSONArray a = w.optJSONArray(key);
        if (a == null) {
            a = new JSONArray();
            try { w.put(key, a); } catch (Exception ignored) { }
        }
        return a;
    }

    static synchronized void reset(Context c) {
        cache = new JSONObject();
        save(c);
    }

    // ---- contacts ----
    static synchronized JSONObject findContact(Context c, String name, String phone) {
        JSONArray a = arr(c, "contacts");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (name != null && !name.isEmpty() && o.optString("name").equalsIgnoreCase(name.trim())) return o;
            if (phone != null && !phone.isEmpty() && digits(o.optString("phone")).equals(digits(phone))) return o;
        }
        return null;
    }

    static String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9+]", ""); }

    static synchronized void addContact(Context c, JSONObject o) {
        arr(c, "contacts").put(o);
        save(c);
    }

    static synchronized int contactCount(Context c) { return arr(c, "contacts").length(); }

    static synchronized String roster(Context c) {
        JSONArray a = arr(c, "contacts");
        if (a.length() == 0) return "(no contacts yet)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            sb.append("- ").append(o.optString("name")).append(" ").append(o.optString("phone"));
            String co = o.optString("company");
            if (!co.isEmpty()) sb.append(" (").append(co).append(")");
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---- ledger (short lines describing what was added, fed back to the AI) ----
    static synchronized void ledger(Context c, String line) {
        JSONArray a = arr(c, "ledger");
        a.put(line.length() > 140 ? line.substring(0, 140) : line);
        if (a.length() > 400) {
            JSONArray n = new JSONArray();
            for (int i = a.length() - 300; i < a.length(); i++) n.put(a.opt(i));
            try { load(c).put("ledger", n); } catch (Exception ignored) { }
        }
        save(c);
    }

    static synchronized String ledgerTail(Context c, int n) {
        JSONArray a = arr(c, "ledger");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, a.length() - n); i < a.length(); i++) sb.append(a.optString(i)).append('\n');
        return sb.length() == 0 ? "(nothing yet)" : sb.toString();
    }

    // ---- SMS waiting for the SMS role ----
    static synchronized void pendingAdd(Context c, JSONObject o) {
        arr(c, "pending").put(o);
        save(c);
    }

    static synchronized int pendingCount(Context c) { return arr(c, "pending").length(); }

    static synchronized List<JSONObject> pendingTake(Context c) {
        JSONArray a = arr(c, "pending");
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        try { load(c).put("pending", new JSONArray()); } catch (Exception ignored) { }
        save(c);
        return out;
    }

    // ---- delayed live events ----
    static synchronized void scheduledAdd(Context c, long fireAt, JSONObject action) {
        try {
            JSONObject o = new JSONObject();
            o.put("fireAt", fireAt);
            o.put("action", action);
            arr(c, "scheduled").put(o);
            save(c);
        } catch (Exception ignored) {
        }
    }

    static synchronized List<JSONObject> dueTake(Context c, long now) {
        JSONArray a = arr(c, "scheduled");
        JSONArray keep = new JSONArray();
        List<JSONObject> due = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (o.optLong("fireAt") <= now) due.add(o.optJSONObject("action")); else keep.put(o);
        }
        try { load(c).put("scheduled", keep); } catch (Exception ignored) { }
        save(c);
        return due;
    }

    static synchronized long nextDue(Context c) {
        JSONArray a = arr(c, "scheduled");
        long best = Long.MAX_VALUE;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) best = Math.min(best, o.optLong("fireAt"));
        }
        return best;
    }

    static synchronized int scheduledCount(Context c) { return arr(c, "scheduled").length(); }

    // ---- things the user did on the phone, told to the AI at the next live tick ----
    static synchronized void userSaidAdd(Context c, String who, String text) {
        try {
            JSONObject o = new JSONObject();
            o.put("to", who);
            o.put("text", text);
            o.put("at", Tm.fmt(System.currentTimeMillis()));
            arr(c, "usersaid").put(o);
            save(c);
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONArray userSaidTake(Context c) {
        JSONArray a = arr(c, "usersaid");
        JSONArray copy = new JSONArray();
        for (int i = 0; i < a.length(); i++) copy.put(a.opt(i));
        try { load(c).put("usersaid", new JSONArray()); } catch (Exception ignored) { }
        save(c);
        return copy;
    }

    static synchronized int userSaidCount(Context c) { return arr(c, "usersaid").length(); }
}
