# Fake Life (com.example.fakelife)

Logfare AI invents a person and writes their whole fake life onto an Android user:
contacts, texts, call log, calendar, photos (drawn in code), notes, wallpaper.
Then it keeps the story going in **live mode** (new texts, missed calls, reacting to what you read and reply).
Put it on a second Android user and explore "the AI's tablet".

Plain Android framework only (Java, no dependencies).

## Build
- GitHub Actions: push this folder to a repo, run "Build APK", download the `FakeLife-debug` artifact.
- Or any Gradle 8.14.x + JDK 17 setup: `gradle assembleDebug`.
- AGP version is in the root `build.gradle` (8.9.1). Change it if your environment needs another one.

## First run (second user)
1. In user 0 (Termux + rish): `pm create-user AI` -> note the id, then `am start-user ID`.
2. Install the APK, open Fake Life > Setup, put the id in, copy the commands, run them from user 0.
   They install the app + Google Messages for that user, grant the permissions, allow restricted
   settings and make Fake Life the SMS app.
3. Switch to the second user, open Fake Life > Settings: Logfare key, then "Generate persona".
4. Main screen: "Backfill the whole life up to today" (or one chapter at a time).
5. Texts: Android only lets the default SMS app write texts. Fake Life writes them while it holds the
   SMS role (button "Become default SMS app"), then open Messages and tap "Set as default" to read them.
   Texts made while it doesn't hold the role are queued and written next time.
6. Turn on Live mode. Notifications have a Reply box: you type as the phone owner and the AI answers as the contact.

## What the AI can do
contact, sms, call, event (+reminder), photo (sunset, night, room, card, paper, pixel, food; EXIF date + GPS),
note (Documents/Notes/*.txt), wallpaper, alarm, plus live_sms and live_call.

## Notes
- Logfare endpoint defaults to https://logfare.ai/v1, model logfare/auto (OpenAI-compatible, editable).
- Nothing real is ever used: contacts get invented +1555 numbers.
- To wipe everything, delete the second user.
