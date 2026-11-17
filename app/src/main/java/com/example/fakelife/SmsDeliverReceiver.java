package com.example.fakelife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Required for SMS role eligibility. This phone has no SIM, so real messages never arrive. */
public class SmsDeliverReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) { }
}
