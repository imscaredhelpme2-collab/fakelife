package com.example.fakelife;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** One job that wakes up for delayed live events and for the next AI "tick". */
final class Scheduler {
    static final int JOB_ID = 4242;

    static void rescheduleAll(Context c) {
        long now = System.currentTimeMillis();
        long next = World.nextDue(c);
        if (Store.liveOn(c)) {
            long tick = Store.nextTick(c);
            if (tick == 0L) {
                tick = now + 60_000L;
                Store.put(c, "nexttick", tick);
            }
            next = Math.min(next, tick);
        }
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (next == Long.MAX_VALUE) {
            js.cancel(JOB_ID);
            return;
        }
        long delay = Math.max(1_000L, next - now);
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, LiveJobService.class))
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + 5 * Tm.MIN)
                .setPersisted(true)
                .build();
        js.schedule(info);
    }
}
