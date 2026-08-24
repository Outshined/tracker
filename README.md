# Tracker

Android app (`org.paul.tracker`, minSdk 26) for manual health metrics, graphs, and WebDAV dump/restore.

There is no Gradle wrapper. Use the system Gradle and a local SDK:

```
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export ANDROID_HOME=/home/paul/android
echo "sdk.dir=$ANDROID_HOME" > local.properties
/usr/bin/gradle :app:assembleDebug :app:test :webdav:test
```

## Tests

JVM unit tests are `:app:test` and `:webdav:test` (no device, no live WebDAV).

Instrumented Compose smoke tests need a device or emulator:

```
/usr/bin/gradle :app:assembleDebugAndroidTest
/usr/bin/gradle :app:connectedDebugAndroidTest
```

`assembleDebugAndroidTest` compiles the androidTest APK. `connectedDebugAndroidTest` runs it; it fails if no device is attached.

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
