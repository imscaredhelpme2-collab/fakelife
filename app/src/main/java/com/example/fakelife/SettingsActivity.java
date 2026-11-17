package com.example.fakelife;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText endpoint, model, key, bible, rules, idea, lifeDays, chapterDays, perChapter, maxPhotos, liveMin, liveMax;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final LinearLayout col = Ui.page(this, "Persona & settings");

        Ui.add(col, Ui.heading(this, "Logfare"));
        endpoint = Ui.edit(this, "API base URL", Store.endpoint(this), false, false);
        model = Ui.edit(this, "Model", Store.model(this), false, false);
        key = Ui.edit(this, "API key", Store.apiKey(this), false, true);
        Ui.add(col, endpoint);
        Ui.add(col, model);
        Ui.add(col, key);

        Ui.add(col, Ui.heading(this, "The person whose phone this is"), 0, 14, 0, 2);
        Ui.add(col, Ui.text(this, "Let Logfare invent them (type an idea, or leave empty to be surprised):", 13, Ui.DIM));
        idea = Ui.edit(this, "e.g. a night-shift lighthouse keeper who keeps getting texts from a dead number", "", false, false);
        Ui.add(col, idea);
        Ui.add(col, Ui.button(this, "Generate persona with Logfare", new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                saveAll(false);
                v.setEnabled(false);
                Toast.makeText(SettingsActivity.this, "Asking Logfare...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String err = null;
                        try {
                            Director.generatePersona(getApplicationContext(), idea.getText().toString());
                        } catch (Exception e) {
                            err = e.getMessage();
                        }
                        final String fe = err;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                v.setEnabled(true);
                                if (fe != null) Toast.makeText(SettingsActivity.this, "Failed: " + fe, Toast.LENGTH_LONG).show();
                                else bible.setText(Store.bible(SettingsActivity.this));
                            }
                        });
                    }
                }).start();
            }
        }));
        Ui.add(col, Ui.text(this, "Character bible (you can edit it by hand too):", 13, Ui.DIM), 0, 10, 0, 2);
        bible = Ui.edit(this, "Character bible", Store.bible(this), true, false);
        bible.setMinLines(10);
        Ui.add(col, bible);

        Ui.add(col, Ui.heading(this, "Content rules sent to the AI"), 0, 14, 0, 2);
        rules = Ui.edit(this, "Rules", Store.rules(this), true, false);
        rules.setMinLines(3);
        Ui.add(col, rules);

        Ui.add(col, Ui.heading(this, "Story pacing"), 0, 14, 0, 2);
        lifeDays = num("How many days back the story starts", Store.lifeDays(this));
        chapterDays = num("Days covered by each chapter", Store.chapterDays(this));
        perChapter = num("Things added per chapter (about)", Store.perChapter(this));
        maxPhotos = num("Max photos per chapter", Store.maxPhotos(this));
        Ui.add(col, Ui.text(this, "Changing 'days back' only matters after you press Reset story on the main screen.", 12, Ui.DIM));
        Ui.add(col, lifeDays);
        Ui.add(col, chapterDays);
        Ui.add(col, perChapter);
        Ui.add(col, maxPhotos);

        Ui.add(col, Ui.heading(this, "Live mode (minutes between AI check-ins)"), 0, 14, 0, 2);
        liveMin = num("Shortest wait", Store.liveMin(this));
        liveMax = num("Longest wait", Store.liveMax(this));
        Ui.add(col, liveMin);
        Ui.add(col, liveMax);

        Ui.add(col, Ui.button(this, "Save", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll(true);
                finish();
            }
        }), 0, 18, 0, 4);
    }

    private EditText num(String hint, int v) {
        EditText e = Ui.edit(this, hint, String.valueOf(v), false, false);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setContentDescription(hint);
        return e;
    }

    private int n(EditText e, int d) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return d; }
    }

    private void saveAll(boolean toast) {
        Store.put(this, "endpoint", endpoint.getText().toString().trim());
        Store.put(this, "model", model.getText().toString().trim());
        Store.put(this, "apikey", key.getText().toString().trim());
        Store.put(this, "bible", bible.getText().toString());
        Store.put(this, "rules", rules.getText().toString());
        Store.put(this, "lifedays", n(lifeDays, 120));
        Store.put(this, "chapterdays", n(chapterDays, 14));
        Store.put(this, "perchapter", n(perChapter, 30));
        Store.put(this, "maxphotos", n(maxPhotos, 4));
        Store.put(this, "livemin", n(liveMin, 20));
        Store.put(this, "livemax", n(liveMax, 180));
        if (toast) Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAll(false);
    }
}
