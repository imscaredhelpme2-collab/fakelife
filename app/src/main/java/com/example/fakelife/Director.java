package com.example.fakelife;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Talks to Logfare and turns its answers into phone content. */
final class Director {
    static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private static final String ACTION_DOC =
            "OUTPUT FORMAT: reply with ONE JSON object and nothing else (no markdown fences, no commentary).\n"
                    + "{\"summary_update\":\"<full updated 'story so far', max 1400 characters, keep open threads and secrets>\","
                    + "\"actions\":[ ... ]}\n"
                    + "Each action has a \"t\" field. Allowed actions:\n"
                    + " {\"t\":\"contact\",\"name\":\"Maya Ortiz\",\"phone\":\"+15550000123\",\"email\":\"\",\"company\":\"\",\"note\":\"short note\",\"color\":\"#8E5BD1\"}\n"
                    + " {\"t\":\"sms\",\"contact\":\"Maya Ortiz\",\"dir\":\"in\" or \"out\",\"body\":\"text\",\"at\":\"2026-03-14 21:40\",\"unread\":false}\n"
                    + " {\"t\":\"call\",\"contact\":\"Maya Ortiz\",\"dir\":\"in\" or \"out\" or \"missed\",\"at\":\"2026-03-14 21:55\",\"secs\":184}\n"
                    + " {\"t\":\"event\",\"title\":\"Dentist\",\"at\":\"2026-03-20 09:30\",\"end\":\"2026-03-20 10:15\",\"place\":\"\",\"desc\":\"\",\"remind_min\":30}\n"
                    + " {\"t\":\"photo\",\"at\":\"2026-03-14 18:02\",\"scene\":\"sunset|night|room|card|paper|pixel|food\",\"palette\":[\"#hex\",\"#hex\"],\"seed\":12345,\"caption\":\"text\",\"stamp\":true,\"portrait\":false}\n"
                    + "   (photos are drawn by code: 'card' = big caption text like a screenshot/meme, 'paper' = handwritten note on lined paper, 'pixel' = pixel doodle)\n"
                    + " {\"t\":\"note\",\"title\":\"Things to remember\",\"body\":\"text\",\"at\":\"2026-03-12 23:10\"}\n"
                    + " {\"t\":\"wallpaper\",\"scene\":\"night\",\"palette\":[\"#hex\",\"#hex\"],\"seed\":1}\n"
                    + " {\"t\":\"alarm\",\"hour\":6,\"minute\":45,\"label\":\"text\"}\n";

    private static String authorPrompt(Context c) {
        return "You are the invisible author of one fictional person's smartphone. A companion app writes everything you output "
                + "into a real, offline Android phone so a curious explorer can later pick it up and piece the story together.\n"
                + "Make it feel like a REAL phone: mundane things (delivery codes, reminders, group chats, blurry photos, "
                + "half-finished notes) mixed with an unfolding story that has threads, small mysteries and secrets. "
                + "Every contact texts in their own distinct voice. Keep continuity with the story so far.\n"
                + "CONTENT RULES: " + Store.rules(c) + "\n\n" + ACTION_DOC;
    }

    // ---------------------------------------------------------------- persona

    static String generatePersona(Context c, String idea) throws Exception {
        String sys = "You invent fictional people for a story-driven fake smartphone. Reply with ONE JSON object only: "
                + "{\"name\":\"<their name>\",\"bible\":\"<the character bible>\"}. The bible (max 1800 characters) must contain: "
                + "name, age, city (fictional or vague), job, home life, daily routine, how they text and use their phone, "
                + "6 to 10 key people (name, relationship, personality, texting style), 2 or 3 secrets or mysteries that will unfold, "
                + "and the overall tone of the story (cozy, eerie, comedic, thriller...). Rules: " + Store.rules(c);
        String user = idea == null || idea.trim().isEmpty()
                ? "Surprise me. Invent an interesting person whose phone would be fun to explore."
                : "Invent the person from this idea: " + idea.trim();
        JSONObject j = Logfare.parseJson(Logfare.chat(c, sys, user, 1.0, 2500));
        String bible = j.optString("bible", "").trim();
        if (bible.isEmpty()) throw new Exception("No bible in reply");
        Store.put(c, "bible", bible);
        Store.put(c, "persona", j.optString("name", "Unknown"));
        Journal.add(c, "New persona: " + j.optString("name", "?"));
        return bible;
    }

    static void saveRaw(Context c, String reply) {
        try (java.io.FileWriter w = new java.io.FileWriter(new java.io.File(c.getFilesDir(), "last_reply.txt"))) {
            w.write(reply);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- chapters

    /** Generates and writes the next chapter. Returns number of actions that worked. */
    static int chapter(Context c) throws Exception {
        if (Store.bible(c).trim().isEmpty()) throw new IllegalArgumentException("Write or generate a persona first (Settings).");
        long now = System.currentTimeMillis();
        int idx = Store.chapterIdx(c);
        long start = Store.lifeStart(c) + idx * Store.chapterDays(c) * Tm.DAY;
        if (start >= now - Tm.HOUR) throw new IllegalStateException("caught up");
        long end = Math.min(start + Store.chapterDays(c) * Tm.DAY, now - 10 * Tm.MIN);

        StringBuilder u = new StringBuilder();
        u.append("CHARACTER BIBLE:\n").append(Store.bible(c)).append("\n\n");
        u.append("STORY SO FAR:\n").append(Store.summary(c).isEmpty() ? "(nothing yet, this is the very beginning)" : Store.summary(c)).append("\n\n");
        u.append("EXISTING CONTACTS (use these exact names):\n").append(World.roster(c)).append("\n");
        u.append("MOST RECENT THINGS ALREADY ON THE PHONE:\n").append(World.ledgerTail(c, 40)).append("\n");
        u.append("CHAPTER ").append(idx + 1).append(" covers ").append(Tm.fmt(start)).append(" to ").append(Tm.fmt(end)).append(".\n");
        u.append("Every action's \"at\" must fall inside that period (events may be later). Write about ")
                .append(Store.perChapter(c)).append(" actions, in a believable mix, at most ")
                .append(Store.maxPhotos(c)).append(" photos.\n");
        if (World.contactCount(c) == 0)
            u.append("This is the first chapter: begin with 8 to 15 contact actions for the people in the bible, then everything else.\n");
        u.append("Make later texts and calls reference earlier ones when natural. Leave at least one thread unresolved at the end.\n");

        Journal.add(c, "Chapter " + (idx + 1) + ": asking Logfare (" + Tm.day(start) + " to " + Tm.day(end) + ")...");
        JSONObject j;
        String reply = Logfare.chat(c, authorPrompt(c), u.toString(), 0.9, 7000);
        saveRaw(c, reply);
        try {
            j = Logfare.parseJson(reply);
        } catch (Exception first) {
            Journal.add(c, "Reply was not valid JSON (" + first.getMessage() + "). Starts with: " + Logfare.shorten(reply.replace('\n', ' '), 160) + " ... retrying once");
            reply = Logfare.chat(c, authorPrompt(c),
                    u + "\nIMPORTANT: your last reply was not valid JSON. Reply with ONLY the JSON object, keep it shorter.", 0.7, 7000);
            saveRaw(c, reply);
            j = Logfare.parseJson(reply);
        }
        JSONArray arr = j.optJSONArray("actions");
        if (arr == null || arr.length() == 0) throw new Exception("Logfare returned no actions");

        List<JSONObject> contacts = new ArrayList<>();
        List<JSONObject> rest = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;
            if ("contact".equalsIgnoreCase(a.optString("t"))) contacts.add(a); else rest.add(a);
        }
        final long fb = start;
        Collections.sort(rest, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject x, JSONObject y) {
                return Long.compare(Tm.parse(x.optString("at", ""), fb), Tm.parse(y.optString("at", ""), fb));
            }
        });
        List<JSONObject> all = new ArrayList<>(contacts);
        all.addAll(rest);

        int ok = 0, bad = 0;
        int photos = 0;
        Random rnd = new Random();
        for (JSONObject a : all) {
            if ("photo".equalsIgnoreCase(a.optString("t")) && ++photos > Store.maxPhotos(c) + 2) continue;
            try {
                long fallback = start + (long) (rnd.nextDouble() * (end - start));
                String what = Executor.run(c, a, fallback, false);
                ok++;
                Journal.add(c, "+ " + what);
            } catch (SecurityException se) {
                bad++;
                Journal.add(c, "! permission missing for " + a.optString("t") + ": " + se.getMessage());
            } catch (Exception e) {
                bad++;
                Journal.add(c, "! " + a.optString("t") + " failed: " + e.getMessage());
            }
        }
        String sum = j.optString("summary_update", "").trim();
        if (!sum.isEmpty()) Store.put(c, "summary", sum.length() > 1600 ? sum.substring(0, 1600) : sum);
        Store.put(c, "chapter", idx + 1);
        Journal.add(c, "Chapter " + (idx + 1) + " done: " + ok + " written, " + bad + " failed. SMS waiting for the SMS role: " + World.pendingCount(c));
        return ok;
    }

    /** Runs chapters until the story reaches the present (or something fails). */
    static void backfill(Context c) throws Exception {
        for (int guard = 0; guard < 60; guard++) {
            try {
                chapter(c);
            } catch (IllegalStateException done) {
                if ("caught up".equals(done.getMessage())) {
                    Journal.add(c, "Story is caught up to today.");
                    return;
                }
                throw done;
            }
        }
    }

    // ---------------------------------------------------------------- live mode

    /** Delivers due delayed events, and (if live mode is on and it is time, or forced) asks the AI what happens next. */
    static void liveCycle(Context c, boolean force) throws Exception {
        long now = System.currentTimeMillis();
        for (JSONObject a : World.dueTake(c, now)) {
            try {
                Journal.add(c, "LIVE " + Executor.runLive(c, a));
            } catch (Exception e) {
                Journal.add(c, "! live event failed: " + e.getMessage());
            }
        }
        boolean wantTick = force || (Store.liveOn(c) && now >= Store.nextTick(c) - 1000L);
        if (!wantTick) return;
        if (Store.bible(c).trim().isEmpty()) return;

        int minMin = Store.liveMin(c), maxMin = Store.liveMax(c);
        long nextAt = now + (minMin + new Random().nextInt(Math.max(1, maxMin - minMin + 1))) * Tm.MIN;
        try {
            JSONObject j = liveTick(c, now);
            int nm = j.optInt("next_tick_min", 0);
            if (nm > 0) nextAt = now + Math.max(minMin, Math.min(maxMin, nm)) * Tm.MIN;
        } catch (Exception e) {
            Journal.add(c, "! live tick failed: " + e.getMessage());
            nextAt = now + 10 * Tm.MIN;
        }
        Store.put(c, "nexttick", nextAt);
        for (JSONObject a : World.dueTake(c, System.currentTimeMillis())) {
            try {
                Journal.add(c, "LIVE " + Executor.runLive(c, a));
            } catch (Exception e) {
                Journal.add(c, "! live event failed: " + e.getMessage());
            }
        }
    }

    private static JSONObject liveTick(Context c, long now) throws Exception {
        String sys = authorPrompt(c)
                + "\nYOU ARE NOW IN LIVE MODE. Time is passing in real life while someone explores this phone.\n"
                + "Instead of the actions above, prefer these live actions:\n"
                + " {\"t\":\"live_sms\",\"contact\":\"Maya Ortiz\",\"body\":\"text\",\"delay_min\":0}  (a new text arrives and notifies the explorer after delay_min minutes)\n"
                + " {\"t\":\"live_call\",\"contact\":\"Maya Ortiz\",\"delay_min\":3}  (a missed call from them)\n"
                + "You may also use contact, event, note and photo (they happen immediately; events may be in the future).\n"
                + "Also include \"next_tick_min\": minutes until you want to be asked again (" + Store.liveMin(c) + " to " + Store.liveMax(c) + ").\n"
                + "Produce 0 to 3 actions. Doing nothing is fine (empty actions). Stay in character for each sender.";
        JSONArray said = World.userSaidTake(c);
        StringBuilder u = new StringBuilder();
        u.append("CHARACTER BIBLE:\n").append(Store.bible(c)).append("\n\n");
        u.append("STORY SO FAR:\n").append(Store.summary(c)).append("\n\n");
        u.append("EXISTING CONTACTS:\n").append(World.roster(c)).append("\n");
        u.append("RECENT PHONE ACTIVITY:\n").append(World.ledgerTail(c, 25)).append("\n");
        u.append("NOW: ").append(Tm.fmt(now)).append("\n");
        u.append("EXPLORER ACTIVITY: ").append(Writer.unreadSummary(c)).append("\n");
        if (said.length() > 0) {
            u.append("The explorer just typed these replies from the phone (the phone owner's side). Have the right contacts respond in-voice, 1 to 10 minutes later:\n");
            for (int i = 0; i < said.length(); i++) {
                JSONObject o = said.optJSONObject(i);
                if (o != null) u.append("- to ").append(o.optString("to")).append(": ").append(o.optString("text")).append("\n");
            }
        }
        JSONObject j = Logfare.parseJson(Logfare.chat(c, sys, u.toString(), 0.9, 2500));
        String sum = j.optString("summary_update", "").trim();
        if (!sum.isEmpty()) Store.put(c, "summary", sum.length() > 1600 ? sum.substring(0, 1600) : sum);
        JSONArray arr = j.optJSONArray("actions");
        int n = 0;
        if (arr != null) for (int i = 0; i < arr.length() && n < 4; i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;
            String t = a.optString("t", "").toLowerCase();
            n++;
            if (t.equals("live_sms") || t.equals("live_call")) {
                long delay = Math.max(0, a.optLong("delay_min", 0)) * Tm.MIN;
                World.scheduledAdd(c, now + delay, a);
                Journal.add(c, "Live plan: " + t + " " + a.optString("contact") + " in " + (delay / Tm.MIN) + " min");
            } else {
                try {
                    Journal.add(c, "LIVE " + Executor.runLive(c, a));
                } catch (Exception e) {
                    Journal.add(c, "! live " + t + " failed: " + e.getMessage());
                }
            }
        }
        return j;
    }
}
