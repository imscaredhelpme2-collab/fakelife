package com.example.fakelife;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

/** Running log shown on the dashboard. */
final class Journal {
    private static final ArrayList<String> lines = new ArrayList<>();
    private static boolean loaded = false;
    private static volatile Runnable listener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void setListener(Runnable r) { listener = r; }

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        File f = new File(c.getFilesDir(), "journal.txt");
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String s;
            while ((s = r.readLine()) != null) lines.add(s);
        } catch (Exception ignored) {
        }
        while (lines.size() > 300) lines.remove(0);
    }

    static synchronized void add(Context c, String msg) {
        load(c);
        String line = Tm.fmt(System.currentTimeMillis()).substring(5) + "  " + msg.replace('\n', ' ');
        lines.add(line);
        while (lines.size() > 300) lines.remove(0);
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), "journal.txt"), true)) {
            w.write(line + "\n");
        } catch (Exception ignored) {
        }
        final Runnable l = listener;
        if (l != null) main.post(l);
    }

    static synchronized String text(Context c, int last) {
        load(c);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.size() - last); i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        return sb.toString();
    }

    static synchronized void clear(Context c) {
        load(c);
        lines.clear();
        new File(c.getFilesDir(), "journal.txt").delete();
    }
}
