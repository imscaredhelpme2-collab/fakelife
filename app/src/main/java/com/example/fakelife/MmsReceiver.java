package com.example.fakelife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Required for SMS role eligibility. */
public class MmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) { }
}
