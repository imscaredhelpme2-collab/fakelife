package com.example.fakelife;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Tiny programmatic UI kit (no XML layouts, no libraries). */
final class Ui {
    static final int BG = 0xFF14121C, CARD = 0xFF211E2E, TEXT = 0xFFE9E5F2, DIM = 0xFF9A94AD,
            ACC = 0xFFE1D384, BTN = 0xFF60408B;

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Sets a scrolling dark page as the content view and returns the column to add views to. */
    static LinearLayout page(Activity a, String title) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 16);
        col.setPadding(pad, pad, pad, pad * 2);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.setContentView(sv);
        TextView t = text(a, title, 24, ACC);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        add(col, t, 0, 0, 0, 8);
        return col;
    }

    static TextView text(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static TextView mono(Context c, String s, int sp) {
        TextView t = text(c, s, sp, TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    static TextView heading(Context c, String s) {
        TextView t = text(c, s, 15, ACC);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BTN);
        g.setCornerRadius(dp(c, 10));
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }

    static EditText edit(Context c, String hint, String value, boolean multiline, boolean secret) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, 8));
        e.setBackground(g);
        int p = dp(c, 10);
        e.setPadding(p, p, p, p);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(4);
            e.setGravity(Gravity.TOP);
        } else if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return e;
    }

    static void add(LinearLayout col, View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        Context c = col.getContext();
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        col.addView(v, lp);
    }

    static void add(LinearLayout col, View v) { add(col, v, 0, 4, 0, 4); }
}
