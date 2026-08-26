# Tracker

Android app (`org.bohme.tracker`, minSdk 26) for manual health metrics, graphs, and WebDAV dump/restore.

## Install on a phone

The installable file is the **debug APK** (sideload; debug-signed):

`app/build/outputs/apk/debug/app-debug.apk`

Copy that file to the phone (USB, Drive, `adb push`) or install over USB:

```
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone: enable **Developer options** → **USB debugging**. First sideload may need **Install unknown apps** for the app you use to open the APK.

## Build

Needs a JDK 17+ (`JAVA_HOME`) and an Android SDK. From the repo root:

```
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk   # or another JDK 17+
export ANDROID_HOME=/home/paul/android          # SDK root with platform-tools
echo "sdk.dir=$ANDROID_HOME" > local.properties # gitignored; once per clone

./gradlew :app:assembleDebug
```

Do **not** use Arch's `/usr/bin/gradle` (it is missing `gradle-public-api-legacy`). `./gradlew` uses Gradle **9.7.0**. If the environment has `GRADLE_HOME=/usr/share/java/gradle`, the wrapper unsets it.

APK after a successful build:

`app/build/outputs/apk/debug/app-debug.apk`

## Tests

JVM unit tests (no device, no live WebDAV):

```
./gradlew :app:testDebugUnitTest :webdav:test
```

Instrumented Compose tests need a device or emulator:

```
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` fails if no device is attached.

## WebDAV

Backup and Restore use HTTP Basic against a **file** URL (not a directory). Nextcloud shape:

`https://<host>/remote.php/dav/files/<user>/<file>`

Example: `https://nas.example/remote.php/dav/files/paul/tracker.json`

Backup confirms overwrite of the server file. Restore confirms replace of all local metrics and samples (not a merge). A remote dump with 0 samples asks for a second confirm before replace.

## Units

First-run seed units (labels only; edit via Edit metric; no conversion):

- Weight: `lb`
- Blood glucose: `mg/dL`
- Blood pressure: `mmHg` plus pulse `bpm`
- BHB: `mmol/L`

## TLS and LAN HTTP

Self-signed servers need **Allow insecure TLS**. User-installed CAs are not trusted.

`usesCleartextTraffic` is enabled for LAN `http://` URLs. Prefer https.

## Local store

`filesDir/store.json`. Seed of the four built-in metrics happens only when **both** `store.json` and `store.json.bak` are absent. An existing file (including `{}`) is not re-seeded. Unreadable files are Corrupt, not a silent seed.

## Ingest

Bluetooth is not implemented. `SampleWriter` is the ingest seam for sample upserts.
