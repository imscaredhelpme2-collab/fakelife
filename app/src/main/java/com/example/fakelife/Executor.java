package com.example.fakelife;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

/** Turns one action from the AI into real writes on the phone. */
final class Executor {
    private static final int[] AVATAR = {0xFF8E5BD1, 0xFF3D8FD6, 0xFFD6653D, 0xFF3DA66B, 0xFFC4457A, 0xFFB08A1E, 0xFF5568C9, 0xFF2E9AA3};

    /** Makes sure a contact exists (on the phone and in the world file) and returns it. */
    static JSONObject ensureContact(Context c, String name, String phone) throws Exception {
        JSONObject ex = World.findContact(c, name, phone);
        if (ex != null) return ex;
        if (name == null || name.trim().isEmpty()) name = phone == null || phone.isEmpty() ? "Unknown" : phone;
        if (phone == null || phone.trim().isEmpty())
            phone = String.format("+1555%07d", 100 + Math.abs(name.toLowerCase().hashCode()) % 900000);
        JSONObject o = new JSONObject();
        o.put("name", name.trim());
        o.put("phone", phone.trim());
        o.put("color", AVATAR[Math.abs(name.hashCode()) % AVATAR.length]);
        Writer.contact(c, name.trim(), phone.trim(), "", "", "", o.getInt("color"));
        World.addContact(c, o);
        return o;
    }

    static String run(Context c, JSONObject a, long fallbackAt, boolean live) throws Exception {
        String t = a.optString("t", "").toLowerCase();
        long now = System.currentTimeMillis();
        long at = Tm.parse(a.optString("at", ""), fallbackAt);
        if (!t.equals("event") && !t.equals("alarm") && at > now - 60_000L) at = now - 60_000L - (long) (Math.random() * 3 * Tm.HOUR);
        if (live) at = Math.min(at, now);

        switch (t) {
            case "contact": {
                String name = a.optString("name", "").trim();
                if (name.isEmpty()) throw new Exception("contact without name");
                if (World.findContact(c, name, a.optString("phone", "")) != null) return "contact exists: " + name;
                String phone = a.optString("phone", "").trim();
                if (phone.isEmpty()) phone = String.format("+1555%07d", 100 + Math.abs(name.toLowerCase().hashCode()) % 900000);
                int color = ImageGen.color(a.optString("color", ""), AVATAR[Math.abs(name.hashCode()) % AVATAR.length]);
                Writer.contact(c, name, phone, a.optString("email", ""), a.optString("company", ""),
                        a.optString("note", ""), color);
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("phone", phone);
                o.put("company", a.optString("company", ""));
                o.put("color", color);
                World.addContact(c, o);
                World.ledger(c, "contact: " + name + " " + a.optString("note", ""));
                return "contact " + name;
            }
            case "sms": {
                JSONObject ct = ensureContact(c, a.optString("contact", ""), a.optString("phone", ""));
                String body = a.optString("body", "").trim();
                if (body.isEmpty()) throw new Exception("empty sms");
                boolean in = !"out".equalsIgnoreCase(a.optString("dir", "in"));
                JSONObject m = new JSONObject();
                m.put("phone", ct.getString("phone"));
                m.put("in", in);
                m.put("body", body);
                m.put("at", at);
                m.put("unread", a.optBoolean("unread", false));
                deliverSms(c, m);
                World.ledger(c, "[" + Tm.fmt(at) + "] sms " + (in ? "from " : "to ") + ct.getString("name") + ": " + body);
                return "sms " + (in ? "<- " : "-> ") + ct.getString("name");
            }
            case "call": {
                JSONObject ct = ensureContact(c, a.optString("contact", ""), a.optString("phone", ""));
                String dir = a.optString("dir", "in").toLowerCase();
                Writer.call(c, ct.getString("phone"), ct.getString("name"), dir, at, a.optInt("secs", 60));
                World.ledger(c, "[" + Tm.fmt(at) + "] call " + dir + " " + ct.getString("name") + " " + a.optInt("secs", 0) + "s");
                return "call " + dir + " " + ct.getString("name");
            }
            case "event": {
                String title = a.optString("title", "").trim();
                if (title.isEmpty()) throw new Exception("event without title");
                long start = Tm.parse(a.optString("at", ""), now + Tm.HOUR);
                long end = Tm.parse(a.optString("end", ""), start + Tm.HOUR);
                if (end <= start) end = start + Tm.HOUR;
                Writer.event(c, title, start, end, a.optString("place", ""), a.optString("desc", ""),
                        a.has("remind_min") ? a.optInt("remind_min", 15) : -1);
                World.ledger(c, "[" + Tm.fmt(start) + "] event: " + title);
                return "event " + title;
            }
            case "photo": {
                String scene = a.optString("scene", "sunset");
                int[] pal = palette(a.optJSONArray("palette"));
                Bitmap b = ImageGen.scene(scene, pal[0], pal[1], a.optLong("seed", at),
                        a.optString("caption", ""), a.optBoolean("stamp", false) ? Tm.stamp(at) : null,
                        a.optBoolean("portrait", false));
                Double lat = a.has("lat") ? a.optDouble("lat") : null;
                Double lon = a.has("lon") ? a.optDouble("lon") : null;
                String name = Writer.photo(c, b, at, a.optString("caption", ""), lat, lon);
                b.recycle();
                World.ledger(c, "[" + Tm.fmt(at) + "] photo (" + scene + "): " + a.optString("caption", ""));
                return "photo " + name;
            }
            case "note": {
                String title = a.optString("title", "Note");
                Writer.note(c, title, a.optString("body", ""), at);
                World.ledger(c, "[" + Tm.fmt(at) + "] note: " + title);
                return "note " + title;
            }
            case "wallpaper": {
                int[] pal = palette(a.optJSONArray("palette"));
                Bitmap b = ImageGen.scene(a.optString("scene", "night"), pal[0], pal[1], a.optLong("seed", 1), "", null, true);
                Writer.wallpaper(c, b);
                b.recycle();
                World.ledger(c, "wallpaper changed: " + a.optString("scene", ""));
                return "wallpaper";
            }
            case "alarm": {
                Writer.alarm(c, a.optInt("hour", 7), a.optInt("minute", 0), a.optString("label", "Alarm"));
                World.ledger(c, "alarm " + a.optInt("hour", 7) + ":" + a.optInt("minute", 0));
                return "alarm";
            }
            default:
                throw new Exception("unknown action type '" + t + "'");
        }
    }

    /** Writes the message now if we hold the SMS role, otherwise queues it. */
    static void deliverSms(Context c, JSONObject m) throws Exception {
        if (Writer.isSmsDefault(c)) Writer.sms(c, m); else World.pendingAdd(c, m);
    }

    /** Delayed live actions, run at their fire time. */
    static String runLive(Context c, JSONObject a) throws Exception {
        String t = a.optString("t", "").toLowerCase();
        long now = System.currentTimeMillis();
        if (t.equals("live_sms")) {
            JSONObject ct = ensureContact(c, a.optString("contact", ""), a.optString("phone", ""));
            String body = a.optString("body", "").trim();
            if (body.isEmpty()) throw new Exception("empty live sms");
            JSONObject m = new JSONObject();
            m.put("phone", ct.getString("phone"));
            m.put("in", true);
            m.put("body", body);
            m.put("at", now);
            m.put("unread", true);
            deliverSms(c, m);
            Notifier.sms(c, ct.getString("name"), ct.getString("phone"), body, now);
            World.ledger(c, "[" + Tm.fmt(now) + "] LIVE sms from " + ct.getString("name") + ": " + body);
            return "live sms from " + ct.getString("name");
        }
        if (t.equals("live_call")) {
            JSONObject ct = ensureContact(c, a.optString("contact", ""), a.optString("phone", ""));
            Writer.call(c, ct.getString("phone"), ct.getString("name"), "missed", now, 0);
            Notifier.missedCall(c, ct.getString("name"), ct.getString("phone"));
            World.ledger(c, "[" + Tm.fmt(now) + "] LIVE missed call from " + ct.getString("name"));
            return "live missed call from " + ct.getString("name");
        }
        return run(c, a, now, true);
    }

    private static int[] palette(JSONArray a) {
        int c1 = 0xFF3D5A9E, c2 = 0xFFE89B5B;
        if (a != null) {
            if (a.length() > 0) c1 = ImageGen.color(a.optString(0), c1);
            if (a.length() > 1) c2 = ImageGen.color(a.optString(1), c2);
        }
        return new int[]{c1, c2};
    }
}
