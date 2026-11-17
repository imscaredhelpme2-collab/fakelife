package com.example.fakelife;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Required for SMS role eligibility. */
public class QuickReplyService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
