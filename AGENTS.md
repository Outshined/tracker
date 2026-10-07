# Tracker

Android app `org.bohme.tracker`. One phone holds a numeric health log and copies that whole log to one WebDAV file. Specified behavior lives in `docs/DESIGN.md`.

## When you change behavior

Read `docs/DESIGN.md` before editing the store, the JSON codec, seed, metrics, samples, graphs, or backup and restore. Match that contract. When the task changes the contract, update `docs/DESIGN.md` in the same change.

Bluetooth work starts from `docs/BLUETOOTH.md` and `docs/OMRON_BLUETOOTH.md`.

## Layout

- `:app` — Compose UI, `AppViewModel`, `Store`, `JsonCodec`, `ConfigStore`, stats. Sources are under `app/src/main/java/org/bohme/tracker/`.
- `:webdav` — JVM OkHttp client. GET and PUT of one file URL. Keep this module free of Android APIs.
- `tools/webdav_stub.py` — local Basic-auth GET/PUT stand-in for a device.

`.gitignore` lists `docs`, so a new file under `docs/` stays untracked. Edit the tracked contract `docs/DESIGN.md` for behavior changes. `docs/tickets/` is local.

## Invariants

- `filesDir/store.json` is the live store and the dump. A save writes a temp file, fsyncs, copies the last good live file to `store.json.bak`, then atomically renames the temp file into place.
- Seed the four built-in metrics only when `store.json` and `store.json.bak` are both absent.
- Backup and restore move that one JSON document to one file URL. The user confirms first. Backup overwrites the server file. Restore replaces local metrics and samples. Leave backup user-initiated.
- URL, username, and password live in `filesDir/webdav.json`. The dump and `toString()` omit the password.
- After create, a metric's field ids and field count stay fixed. A unit string is a label. Editing it leaves stored numbers as they are.

## Prove the change

From the repo root, with JDK 17+ and `ANDROID_HOME` set. `local.properties` holds `sdk.dir` and is gitignored.

JVM tests, no device:

```
./gradlew :app:testDebugUnitTest :webdav:test
```

Debug APK:

```
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

Compose instrumentation needs a device or emulator:

```
./gradlew :app:connectedDebugAndroidTest
```

The change is done when the JVM tests pass and any contract edit is in `docs/DESIGN.md`. Run the connected tests when the change touches Compose UI and a device is attached.
