package com.example.fakelife;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Required for SMS role eligibility. Open the real Messages app to read the texts. */
public class ComposeActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Toast.makeText(this, "Fake Life only fills the phone. Use your Messages app to read texts.", Toast.LENGTH_LONG).show();
        finish();
    }
}
