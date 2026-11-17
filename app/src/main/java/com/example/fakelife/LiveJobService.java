package com.example.fakelife;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

public class LiveJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                Director.liveCycle(c, false);
            } catch (Throwable t) {
                Journal.add(c, "Live job error: " + t.getMessage());
            } finally {
                jobFinished(params, false);
                Scheduler.rescheduleAll(c);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
