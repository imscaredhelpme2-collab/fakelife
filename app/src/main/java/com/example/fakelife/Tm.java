package com.example.fakelife;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small time helpers. All times are local time. */
final class Tm {
    static final long MIN = 60_000L;
    static final long HOUR = 60 * MIN;
    static final long DAY = 24 * HOUR;

    private static final String[] FORMATS = {
            "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
    };

    static long parse(String s, long fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.endsWith("Z")) s = s.substring(0, s.length() - 1);
        for (String f : FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    static String fmt(long t) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(t));
    }

    static String day(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    /** Old digital camera date stamp, like '26 03 14. */
    static String stamp(long t) {
        return new SimpleDateFormat("''yy MM dd", Locale.US).format(new Date(t));
    }

    static String fileStamp(long t) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(t));
    }

    static long midnight(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
