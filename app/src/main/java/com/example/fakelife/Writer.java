package com.example.fakelife;

import android.app.WallpaperManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.TimeZone;

/** Low-level writers into the real Android providers. */
final class Writer {

    // ------------------------------------------------------------ contacts
    static void contact(Context c, String name, String phone, String email, String company,
                        String note, int color) throws Exception {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        if (email != null && !email.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_HOME).build());
        }
        if (company != null && !company.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company).build());
        }
        if (note != null && !note.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note).build());
        }
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, ImageGen.avatar(name, color)).build());
        c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    }

    // ------------------------------------------------------------ call log
    static void call(Context c, String phone, String name, String dir, long at, int secs) {
        int type = CallLog.Calls.INCOMING_TYPE;
        if ("out".equals(dir)) type = CallLog.Calls.OUTGOING_TYPE;
        else if ("missed".equals(dir)) type = CallLog.Calls.MISSED_TYPE;
        ContentValues v = new ContentValues();
        v.put(CallLog.Calls.NUMBER, phone);
        v.put(CallLog.Calls.TYPE, type);
        v.put(CallLog.Calls.DATE, at);
        v.put(CallLog.Calls.DURATION, type == CallLog.Calls.MISSED_TYPE ? 0 : Math.max(1, secs));
        v.put(CallLog.Calls.CACHED_NAME, name);
        v.put(CallLog.Calls.NEW, type == CallLog.Calls.MISSED_TYPE ? 1 : 0);
        v.put("is_read", type == CallLog.Calls.MISSED_TYPE ? 0 : 1);
        c.getContentResolver().insert(CallLog.Calls.CONTENT_URI, v);
    }

    // ------------------------------------------------------------ SMS
    static boolean isSmsDefault(Context c) {
        return c.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(c));
    }

    static void sms(Context c, JSONObject m) throws Exception {
        boolean in = m.optBoolean("in", true);
        boolean unread = m.optBoolean("unread", false);
        long at = m.optLong("at", System.currentTimeMillis());
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.ADDRESS, m.getString("phone"));
        v.put(Telephony.Sms.BODY, m.getString("body"));
        v.put(Telephony.Sms.DATE, at);
        v.put(Telephony.Sms.READ, unread ? 0 : 1);
        v.put(Telephony.Sms.SEEN, unread ? 0 : 1);
        Uri u;
        if (in) {
            v.put(Telephony.Sms.DATE_SENT, at - 2000);
            u = Telephony.Sms.Inbox.CONTENT_URI;
        } else {
            u = Telephony.Sms.Sent.CONTENT_URI;
        }
        Uri r = c.getContentResolver().insert(u, v);
        if (r == null) throw new IOException("SMS insert refused (this app is not the default SMS app)");
    }

    /** Writes everything that was waiting for the SMS role. Returns how many were written. */
    static int flushPending(Context c) {
        if (!isSmsDefault(c)) return 0;
        int n = 0;
        for (JSONObject o : World.pendingTake(c)) {
            try {
                sms(c, o);
                n++;
            } catch (Exception e) {
                World.pendingAdd(c, o);
                Journal.add(c, "SMS flush failed: " + e.getMessage());
                break;
            }
        }
        return n;
    }

    /** Counts unread messages per address (needs READ_SMS or the SMS role). */
    static String unreadSummary(Context c) {
        try (Cursor cur = c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS}, Telephony.Sms.READ + "=0", null, null)) {
            if (cur == null) return "unknown";
            java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
            while (cur.moveToNext()) {
                String a = cur.getString(0);
                JSONObject ct = World.findContact(c, null, a);
                String k = ct != null ? ct.optString("name") : a;
                Integer old = map.get(k);
                map.put(k, old == null ? 1 : old + 1);
            }
            if (map.isEmpty()) return "the user has read every text";
            StringBuilder sb = new StringBuilder("unread texts: ");
            for (java.util.Map.Entry<String, Integer> e : map.entrySet())
                sb.append(e.getKey()).append(" x").append(e.getValue()).append("; ");
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------ calendar
    static long ensureCalendar(Context c) throws Exception {
        long id = Store.l(c, "calid", -1L);
        if (id > 0) {
            try (Cursor cur = c.getContentResolver().query(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
                    new String[]{CalendarContract.Calendars._ID}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) return id;
            }
        }
        Uri u = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Phone")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Calendars.ACCOUNT_NAME, "Phone");
        v.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put(CalendarContract.Calendars.NAME, "mycal");
        v.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "My Calendar");
        v.put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF60408B);
        v.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        v.put(CalendarContract.Calendars.OWNER_ACCOUNT, "Phone");
        v.put(CalendarContract.Calendars.VISIBLE, 1);
        v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        v.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().getID());
        Uri r = c.getContentResolver().insert(u, v);
        if (r == null) throw new IOException("Could not create a local calendar");
        id = ContentUris.parseId(r);
        Store.put(c, "calid", id);
        return id;
    }

    static void event(Context c, String title, long start, long end, String place, String desc,
                      int remindMin) throws Exception {
        long cal = ensureCalendar(c);
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, cal);
        v.put(CalendarContract.Events.TITLE, title);
        v.put(CalendarContract.Events.DTSTART, start);
        v.put(CalendarContract.Events.DTEND, end);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        if (place != null && !place.isEmpty()) v.put(CalendarContract.Events.EVENT_LOCATION, place);
        if (desc != null && !desc.isEmpty()) v.put(CalendarContract.Events.DESCRIPTION, desc);
        Uri r = c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
        if (r == null) throw new IOException("Event insert refused");
        if (remindMin >= 0) {
            ContentValues rv = new ContentValues();
            rv.put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(r));
            rv.put(CalendarContract.Reminders.MINUTES, remindMin);
            rv.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            c.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, rv);
        }
    }

    // ------------------------------------------------------------ photos
    static String photo(Context c, Bitmap bmp, long at, String caption, Double lat, Double lon) throws Exception {
        File tmp = new File(c.getCacheDir(), "p" + at + ".jpg");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, o);
        }
        try {
            ExifInterface ex = new ExifInterface(tmp.getAbsolutePath());
            String d = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date(at));
            ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, d);
            ex.setAttribute(ExifInterface.TAG_DATETIME, d);
            if (caption != null && !caption.isEmpty()) ex.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, caption);
            if (lat != null && lon != null && !lat.isNaN() && !lon.isNaN()) {
                ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(lat));
                ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat >= 0 ? "N" : "S");
                ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(lon));
                ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon >= 0 ? "E" : "W");
            }
            ex.saveAttributes();
        } catch (Exception ignored) {
        }
        String name = "IMG_" + Tm.fileStamp(at) + ".jpg";
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera");
        v.put(MediaStore.Images.Media.DATE_TAKEN, at);
        v.put(MediaStore.Images.Media.DATE_ADDED, at / 1000);
        v.put(MediaStore.Images.Media.DATE_MODIFIED, at / 1000);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("MediaStore refused the photo");
        try (OutputStream out = cr.openOutputStream(uri); FileInputStream in = new FileInputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        done.put(MediaStore.Images.Media.DATE_TAKEN, at);
        cr.update(uri, done, null, null);
        tmp.delete();
        return name;
    }

    private static String dms(double v) {
        v = Math.abs(v);
        int d = (int) v;
        double mf = (v - d) * 60;
        int m = (int) mf;
        int sec = (int) Math.round((mf - m) * 60 * 1000);
        return d + "/1," + m + "/1," + sec + "/1000";
    }

    // ------------------------------------------------------------ notes (text files in Documents/Notes)
    static String note(Context c, String title, String body, long at) throws Exception {
        String safe = title.replaceAll("[^A-Za-z0-9 _.-]", "").trim();
        if (safe.isEmpty()) safe = "note";
        String name = safe + ".txt";
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Files.FileColumns.DISPLAY_NAME, name);
        v.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
        v.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/Notes");
        v.put(MediaStore.Files.FileColumns.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("MediaStore refused the note");
        try (OutputStream out = cr.openOutputStream(uri)) {
            out.write((title + "\n" + Tm.fmt(at) + "\n\n" + body + "\n").getBytes("UTF-8"));
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        return name;
    }

    // ------------------------------------------------------------ wallpaper / alarm
    static void wallpaper(Context c, Bitmap bmp) throws Exception {
        WallpaperManager.getInstance(c).setBitmap(bmp, null, true,
                WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
    }

    static void alarm(Context c, int hour, int minute, String label) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

}
