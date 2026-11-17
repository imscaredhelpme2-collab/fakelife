package com.example.fakelife;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

/** Live notifications for new texts and missed calls. */
final class Notifier {
    static final String CHANNEL = "fakelife_msgs";

    static void channel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Messages and calls",
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
    }

    private static PendingIntent openMessages(Context c) {
        Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (c.getPackageManager().resolveActivity(i, 0) == null) i = new Intent(c, MainActivity.class);
        return PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void sms(Context c, String name, String phone, String body, long at) {
        channel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Person them = new Person.Builder().setName(name).build();
        Person me = new Person.Builder().setName("Me").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(me).addMessage(body, at, them);

        Intent ri = new Intent(c, ReplyReceiver.class).putExtra("name", name).putExtra("phone", phone);
        PendingIntent rp = PendingIntent.getBroadcast(c, Math.abs(phone.hashCode()), ri,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput input = new RemoteInput.Builder("reply").setLabel("Reply").build();
        Notification.Action act = new Notification.Action.Builder(
                Icon.createWithResource(c, android.R.drawable.ic_menu_send), "Reply", rp)
                .addRemoteInput(input).build();

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setStyle(style)
                .addAction(act)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(openMessages(c))
                .setAutoCancel(true)
                .setWhen(at)
                .build();
        nm.notify(phone, 1, n);
    }

    static void missedCall(Context c, String name, String phone) {
        channel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle("Missed call")
                .setContentText(name)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setAutoCancel(true)
                .build();
        nm.notify(phone, 2, n);
    }

    static void cancelSms(Context c, String phone) {
        c.getSystemService(NotificationManager.class).cancel(phone, 1);
    }
}
