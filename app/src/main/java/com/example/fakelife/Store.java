package com.example.fakelife;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings and small persistent state. */
final class Store {
    static final String DEFAULT_RULES =
            "Keep it PG-13. Everything is fiction: only invented people and invented phone numbers "
                    + "(+1555xxxxxxx). No real people, real addresses or real accounts. No graphic violence, "
                    + "no sexual content, nothing involving real self-harm methods.";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("fakelife", Context.MODE_PRIVATE);
    }

    static String s(Context c, String k, String d) { return p(c).getString(k, d); }
    static void put(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    static int i(Context c, String k, int d) { return p(c).getInt(k, d); }
    static void put(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    static long l(Context c, String k, long d) { return p(c).getLong(k, d); }
    static void put(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }
    static boolean b(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    static void put(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }

    static String endpoint(Context c) { return s(c, "endpoint", "https://logfare.ai/v1"); }
    static String model(Context c) { return s(c, "model", "logfare/auto"); }
    static String apiKey(Context c) { return s(c, "apikey", ""); }
    static String bible(Context c) { return s(c, "bible", ""); }
    static String personaName(Context c) { return s(c, "persona", ""); }
    static String rules(Context c) { return s(c, "rules", DEFAULT_RULES); }
    static String summary(Context c) { return s(c, "summary", ""); }
    static int chapterIdx(Context c) { return i(c, "chapter", 0); }
    static int lifeDays(Context c) { return i(c, "lifedays", 120); }
    static int chapterDays(Context c) { return Math.max(1, i(c, "chapterdays2", 10)); }
    static int maxTokens(Context c) { return Math.max(500, i(c, "maxtokens", 4000)); }
    static int perChapter(Context c) { return Math.max(5, i(c, "perchapter2", 15)); }
    static int maxPhotos(Context c) { return Math.max(0, i(c, "maxphotos", 4)); }
    static boolean liveOn(Context c) { return b(c, "liveon", false); }
    static int liveMin(Context c) { return Math.max(1, i(c, "livemin", 20)); }
    static int liveMax(Context c) { return Math.max(liveMin(c), i(c, "livemax", 180)); }
    static long nextTick(Context c) { return l(c, "nexttick", 0L); }

    static long lifeStart(Context c) {
        long t = l(c, "lifestart", 0L);
        if (t == 0L) {
            t = Tm.midnight(System.currentTimeMillis() - lifeDays(c) * Tm.DAY);
            put(c, "lifestart", t);
        }
        return t;
    }

    static void resetStory(Context c) {
        p(c).edit().remove("chapter").remove("summary").remove("lifestart").remove("nexttick").apply();
        World.reset(c);
    }
}
