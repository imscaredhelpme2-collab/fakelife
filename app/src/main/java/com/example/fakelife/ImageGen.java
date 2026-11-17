package com.example.fakelife;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/** Draws "photos" in code: Logfare can't make images, so it picks a scene, palette and seed. */
final class ImageGen {

    static Bitmap scene(String scene, int c1, int c2, long seed, String caption, String stamp, boolean portrait) {
        int w = portrait ? 960 : 1280;
        int h = portrait ? 1280 : 960;
        Random r = new Random(seed);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String s = scene == null ? "" : scene.toLowerCase();
        switch (s) {
            case "night": night(cv, p, r, w, h, c1, c2); break;
            case "room": room(cv, p, r, w, h, c1, c2); break;
            case "card": card(cv, w, h, c1, c2, caption); break;
            case "paper": paper(cv, p, w, h, caption); break;
            case "pixel": pixel(cv, p, r, w, h, c1, c2); break;
            case "food": food(cv, p, r, w, h, c1, c2); break;
            default: sky(cv, p, r, w, h, c1, c2); break;
        }
        if (!s.equals("card") && !s.equals("paper") && !s.equals("pixel")) grain(b, r, 14);
        if (stamp != null && !stamp.isEmpty()) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(0xFFFF9A2E);
            sp.setTypeface(Typeface.MONOSPACE);
            sp.setTextSize(h / 22f);
            sp.setTextAlign(Paint.Align.RIGHT);
            sp.setShadowLayer(4, 0, 0, 0xAA000000);
            cv.drawText(stamp, w - w / 25f, h - h / 25f, sp);
        }
        return b;
    }

    static byte[] avatar(String name, int color) {
        int n = 256;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        cv.drawRect(0, 0, n, n, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(130);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        String ini = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        cv.drawText(ini, n / 2f, n / 2f + 45, p);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        b.recycle();
        return o.toByteArray();
    }

    static int color(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- scenes

    private static void sky(Canvas cv, Paint p, Random r, int w, int h, int c1, int c2) {
        p.setShader(new LinearGradient(0, 0, 0, h * 0.7f, c1, c2, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float sx = w * (0.25f + r.nextFloat() * 0.5f);
        float sy = h * (0.45f + r.nextFloat() * 0.15f);
        p.setShader(new RadialGradient(sx, sy, w * 0.35f, 0x88FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(sx, sy, w * 0.35f, p);
        p.setShader(null);
        p.setColor(0xFFFFF4D6);
        cv.drawCircle(sx, sy, w * 0.07f, p);
        int base = mix(c2, 0xFF000000, 0.55f);
        for (int layer = 0; layer < 3; layer++) {
            Path path = new Path();
            float y = h * (0.68f + layer * 0.08f);
            path.moveTo(0, h);
            path.lineTo(0, y);
            for (int x = 0; x <= w; x += w / 16) {
                y += (r.nextFloat() - 0.5f) * h * 0.06f;
                y = Math.max(h * 0.58f, Math.min(h * 0.92f, y));
                path.lineTo(x, y);
            }
            path.lineTo(w, h);
            path.close();
            p.setColor(mix(base, 0xFF000000, layer * 0.3f));
            cv.drawPath(path, p);
        }
    }

    private static void night(Canvas cv, Paint p, Random r, int w, int h, int c1, int c2) {
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF04050C, mix(c1, 0xFF000000, 0.5f), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 140; i++) {
            p.setAlpha(80 + r.nextInt(175));
            cv.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.6f, 1 + r.nextFloat() * 2.2f, p);
        }
        p.setAlpha(255);
        p.setColor(0xFFF2EFD8);
        cv.drawCircle(w * (0.2f + r.nextFloat() * 0.6f), h * 0.2f, w * 0.045f, p);
        int bld = mix(c2, 0xFF000000, 0.75f);
        int x = 0;
        while (x < w) {
            int bw = w / 14 + r.nextInt(w / 12);
            int bh = (int) (h * (0.18f + r.nextFloat() * 0.38f));
            p.setColor(bld);
            cv.drawRect(x, h - bh, x + bw, h, p);
            for (int wy = h - bh + 14; wy < h - 14; wy += 30) {
                for (int wx = x + 10; wx < x + bw - 14; wx += 24) {
                    if (r.nextInt(100) < 35) {
                        p.setColor(r.nextBoolean() ? 0xFFFFE08A : 0xFFFFC857);
                        cv.drawRect(wx, wy, wx + 11, wy + 16, p);
                    }
                }
            }
            x += bw + 4;
        }
    }

    private static void room(Canvas cv, Paint p, Random r, int w, int h, int c1, int c2) {
        p.setColor(c1);
        cv.drawRect(0, 0, w, h, p);
        p.setColor(mix(c2, 0xFF000000, 0.35f));
        cv.drawRect(0, h * 0.72f, w, h, p);
        float wx = w * (0.1f + r.nextFloat() * 0.5f), wy = h * 0.1f, ww = w * 0.3f, wh = h * 0.4f;
        p.setShader(new LinearGradient(0, wy, 0, wy + wh, 0xFF7FB2E6, 0xFFE9D9B0, Shader.TileMode.CLAMP));
        cv.drawRect(wx, wy, wx + ww, wy + wh, p);
        p.setShader(null);
        p.setColor(0xFFF5F1E8);
        p.setStrokeWidth(10);
        p.setStyle(Paint.Style.STROKE);
        cv.drawRect(wx, wy, wx + ww, wy + wh, p);
        cv.drawLine(wx + ww / 2, wy, wx + ww / 2, wy + wh, p);
        cv.drawLine(wx, wy + wh / 2, wx + ww, wy + wh / 2, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(mix(c2, 0xFF000000, 0.5f));
        cv.drawRect(w * 0.55f, h * 0.55f, w * 0.95f, h * 0.78f, p);
        p.setColor(mix(c2, 0xFFFFFFFF, 0.25f));
        cv.drawRect(w * 0.55f, h * 0.5f, w * 0.95f, h * 0.58f, p);
        float lx = w * 0.18f, ly = h * 0.64f;
        p.setShader(new RadialGradient(lx, ly, w * 0.3f, 0x88FFE9A8, 0x00FFE9A8, Shader.TileMode.CLAMP));
        cv.drawCircle(lx, ly, w * 0.3f, p);
        p.setShader(null);
        p.setColor(0xFF333333);
        cv.drawRect(lx - 6, ly, lx + 6, h * 0.78f, p);
        p.setColor(0xFFFFE9A8);
        cv.drawRect(lx - 30, ly - 34, lx + 30, ly, p);
    }

    private static void card(Canvas cv, int w, int h, int c1, int c2, String caption) {
        Paint bg = new Paint();
        bg.setColor(c1);
        cv.drawRect(0, 0, w, h, bg);
        int fg = contrast(c1, c2) ? c2 : (lum(c1) > 0.5f ? 0xFF111111 : 0xFFFFFFFF);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(fg);
        tp.setTextSize(h / 13f);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        drawWrapped(cv, caption == null ? "" : caption, tp, w, h, w * 0.1f, Layout.Alignment.ALIGN_CENTER);
    }

    private static void paper(Canvas cv, Paint p, int w, int h, String caption) {
        p.setColor(0xFFF6F0DC);
        cv.drawRect(0, 0, w, h, p);
        p.setColor(0x553E6DB5);
        p.setStrokeWidth(2);
        for (int y = h / 16; y < h; y += h / 16) cv.drawLine(0, y, w, y, p);
        p.setColor(0x88C0392B);
        p.setStrokeWidth(3);
        cv.drawLine(w * 0.12f, 0, w * 0.12f, h, p);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFF1B2A6B);
        tp.setTextSize(h / 17f);
        tp.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        cv.save();
        cv.rotate(-1.2f, w / 2f, h / 2f);
        cv.translate(w * 0.16f, h / 16f * 0.2f);
        StaticLayout sl = StaticLayout.Builder.obtain(caption == null ? "" : caption, 0,
                caption == null ? 0 : caption.length(), tp, (int) (w * 0.78f)).setLineSpacing(0f, 0.95f).build();
        sl.draw(cv);
        cv.restore();
    }

    private static void pixel(Canvas cv, Paint p, Random r, int w, int h, int c1, int c2) {
        p.setColor(mix(c1, 0xFF000000, 0.6f));
        cv.drawRect(0, 0, w, h, p);
        int n = 16;
        float cell = Math.min(w, h) * 0.8f / n;
        float ox = (w - cell * n) / 2f, oy = (h - cell * n) / 2f;
        boolean[][] g = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n / 2; x++) {
            boolean on = r.nextInt(100) < 48;
            g[y][x] = on;
            g[y][n - 1 - x] = on;
        }
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (!g[y][x]) continue;
            p.setColor(r.nextInt(4) == 0 ? c2 : c1);
            cv.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, p);
        }
    }

    private static void food(Canvas cv, Paint p, Random r, int w, int h, int c1, int c2) {
        p.setColor(c1);
        cv.drawRect(0, 0, w, h, p);
        p.setColor(0x22000000);
        for (int i = 0; i < 9; i++) cv.drawRect(0, i * h / 9f, w, i * h / 9f + 6, p);
        float cx = w / 2f + (r.nextFloat() - 0.5f) * w * 0.1f, cy = h / 2f, rad = Math.min(w, h) * 0.34f;
        p.setColor(0x33000000);
        cv.drawCircle(cx + 14, cy + 18, rad, p);
        p.setColor(0xFFF8F6F0);
        cv.drawCircle(cx, cy, rad, p);
        p.setColor(0xFFE9E5DA);
        cv.drawCircle(cx, cy, rad * 0.78f, p);
        int[] cols = {c2, 0xFFE8A33D, 0xFF5E9E4F, 0xFFC4553B};
        for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float d = r.nextFloat() * rad * 0.5f;
            p.setColor(cols[r.nextInt(cols.length)]);
            cv.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, rad * (0.1f + r.nextFloat() * 0.15f), p);
        }
        p.setColor(0xFFCFCFCF);
        cv.drawRect(cx + rad * 1.15f, cy - rad * 0.8f, cx + rad * 1.22f, cy + rad * 0.8f, p);
    }

    // ---------------------------------------------------------------- helpers

    private static void drawWrapped(Canvas cv, String text, TextPaint tp, int w, int h, float margin, Layout.Alignment al) {
        int width = (int) (w - margin * 2);
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, width).setAlignment(al).build();
        cv.save();
        cv.translate(margin, (h - sl.getHeight()) / 2f);
        sl.draw(cv);
        cv.restore();
    }

    private static void grain(Bitmap b, Random r, int amt) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int d = r.nextInt(amt * 2 + 1) - amt;
            int c = px[i];
            int rr = clamp(((c >> 16) & 255) + d), gg = clamp(((c >> 8) & 255) + d), bb = clamp((c & 255) + d);
            px[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static float lum(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    private static boolean contrast(int a, int b) { return Math.abs(lum(a) - lum(b)) > 0.4f; }
}
