Simple health metrics tracker, no cloud required but does do backups over WebDAV.  Tell it what you want to track, feed in data, get back graphs over time.  I'm using to track stats while keeping a keto diet, and because I'm *ahem* old, blood pressure.
Nothing else really seemed to be what I wanted, or pushed cloud stuff that I don't want.  This is as simple as I could think to make it.

100% Grok vibe-coded.  I've been doing software for enough decades that I'm OK describing something in detail and getting decent results, so frankly I'm not looking past that.
I've only begun using this at all, so it may explode still.  We'll see.

# Tracker

Android app for one person to log numeric health readings, graph them, and copy the whole log to a WebDAV file. The phone holds the live data. Backup and restore are manual, confirmed copies of that one file.

Version 0.1, application id `org.bohme.tracker`, minSdk 26. Install is a sideload of the debug APK.

It stores and graphs the numbers you enter.

## The app

The bottom bar has **Metrics** and **Graphs**. The top bar has **Today** and a settings gear.

- **Today** shows every metric's fields and saves the ones you fill in.
- **Metrics** lists metrics and opens one to add, edit, or delete samples.
- **Graphs** plots one or more metrics over a time range, with optional daily, weekly, and monthly means. Fields on one metric share one axis.
- **Settings** adds, edits, and deletes metrics, and runs WebDAV backup and restore.

First launch seeds four metrics when both store files are absent. A unit is a label. Editing a unit leaves stored numbers as they are.

| Metric | Unit |
|---|---|
| Weight | lb |
| BHB | mmol/L |
| Blood glucose | mg/dL |
| Blood pressure | mmHg, plus pulse in bpm |

## Build

JDK 17 or newer (`JAVA_HOME`) and an Android SDK with platform 36 (`ANDROID_HOME`). Once per clone:

```
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

`local.properties` is gitignored.

```
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a phone

Enable Developer options and USB debugging. Then:

```
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Opening the APK from a file manager may ask you to allow that app to install unknown apps.

## WebDAV

Backup and restore use HTTP Basic against a file URL. Create the parent folder on the server first. Nextcloud shape:

```
https://<host>/remote.php/dav/files/<user>/<file>
```

Example: `https://nas.example/remote.php/dav/files/paul/tracker.json`

Use an app password in the password field. **Backup now** overwrites that server file with the local log. **Restore now** replaces every local metric and sample with the server file. A server file with 0 samples asks a second time when the phone still has samples.

Self-signed servers need **Allow insecure TLS**. User-installed CAs are not trusted. Cleartext HTTP is enabled for a LAN URL. Prefer https.

On Nextcloud, leave file versions enabled. Tracker restores the current file only. To return to an older copy, restore that version in Nextcloud so it is the current file, then Restore now in Tracker.

## Data on the phone

The live log is `filesDir/store.json`. Each save writes a temp file, syncs it, copies the previous good file to `store.json.bak`, and renames the temp file into place. If `store.json` is unreadable and `.bak` is readable, the app recovers from `.bak`. If both are unreadable, the log is Corrupt: backup stays off until Restore or Reset. Reset keeps the unreadable bytes aside and seeds the built-in metrics again.

An existing `store.json`, including an empty catalog, is left as it is.

The WebDAV URL and password are `filesDir/webdav.json`. The health dump omits them. Android backup of app data is off.

## Tests

JVM tests, with no device and no live server:

```
./gradlew :app:testDebugUnitTest :webdav:test
```

Instrumented Compose tests need a device or emulator. They fail when none is attached:

```
./gradlew :app:connectedDebugAndroidTest
```

`tools/webdav_stub.py` is a local GET/PUT stand-in for the client the app speaks.

## Project

Kotlin, Jetpack Compose, Material 3. Modules are `:app` and `:webdav` (OkHttp GET and PUT of one file). `docs/DESIGN.md` is the behavior contract.
