# Omron Bluetooth — plan and test approach

This is a plan, not an implementation. Tracker v1 ingest is already `SampleWriter.upsert(Sample)` with `source = "bluetooth"` and caller-supplied ids. Omron work should write that path only.

## What Omron actually speaks

Consumer Omron cuffs that pair with **OMRON Connect** are BLE. There are two families:

1. **Bluetooth SIG Blood Pressure Profile (BLP)** — GATT service `0x1810`, characteristic Blood Pressure Measurement `0x2A35` (indications). Official Omron “telehealth” units (e.g. BP9300T) document this. Pairing is required to read `2A35`. Payload is systolic / diastolic / MAP / pulse / flags / timestamp.

2. **Omron proprietary “BLEsmart” protocol** — most retail HEM-*T / Intelli IT cuffs (HEM-7155T, HEM-7322T, HEM-7600T/EVOLV, HEM-7530T/BP7900, …). Advertised name like `BLEsmart_XXXXXXXXXXXX`. After pairing they do **not** stream BLP; the app writes chunked command frames to custom characteristics and reads EEPROM-style records (XOR checksum, command types `0100` read / `0f00` end). Open-source maps: [omblepy](https://github.com/userx14/omblepy), [hass-omron](https://github.com/eigger/hass-omron), [ichernev/omron-rs7-intelli-it](https://github.com/ichernev/omron-rs7-intelli-it). Official OMRON Connect Android SDK exists but is closed and app-bound.

**A cuff pairs with one BLE central at a time.** OMRON Connect, Home Assistant, and Tracker cannot share a pairing. Forget the device in Android Settings (and the cuff) before switching apps.

Retail cuffs you own are almost certainly family 2. Do not implement only BLP and hope.

## App work (once we know the model)

Keep it small. New code lives next to ingest, not a second store.

| Piece | Job |
|---|---|
| Manifest | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+); `neverForLocation` if we can; `ACCESS_FINE_LOCATION` only if scan requires it on the minSdk we hit |
| `OmronSource` | Scan → bond → GATT state machine → parse records → `Sample(id, metricId=blood_pressure, recordedAt=cuff time, source=bluetooth, values={systolic,diastolic,pulse})` → `SampleWriter.upsert` |
| Sample `id` | Deterministic: `omron:{address}:{epochSeconds}` so retries do not duplicate (KD-8) |
| UI | Settings: “Scan Omron”, last error, last sync time. No background sync in the first cut |
| Tests | Fake GATT (byte fixtures from a snoop) → parsed `Sample`s. No live radio in CI |

Out of scope for first Omron cut: weight scales, ECG, Health Connect, official SDK, two-user cuff slots beyond “user 1”.

## Can we use the **host** Bluetooth stack?

**Not from the Android app process.** The app talks to Android’s stack (Fluoride / Gabeldorsche), not Linux BlueZ. Those are different hosts on different HCI sockets.

| Environment | What the app sees | Host BlueZ? |
|---|---|---|
| **Physical phone** | Real Android BT + the cuff | Irrelevant |
| **AVD emulator** | Virtual controller (**Netsim / Root Canal**), not the PC’s adapter. Default AVD often has **no** working BT until launched with packet-streamer/Netsim. Does **not** use BlueZ. | No |
| **Emulator + USB dongle** | Possible via [Bumble HCI bridge](https://google.github.io/bumble/platforms/android.html): `bumble-hci-bridge` presents the dongle as the emulator’s controller (`-packet-streamer-endpoint`). Android then owns the dongle; BlueZ on the host must **not** hold it (`btmgmt power off` / unbind). Emulator **33.1.4+**. LE-only dongles often fail Android’s stack. | Dongle only, not the built-in laptop radio if Android grabbed a second adapter |
| **Waydroid** | Nested Android with its own BT stack. **No first-class BlueZ passthrough.** USB dongle passthrough is the usual workaround and is flaky; Wi-Fi/BT combo chips on the laptop are worse. | Not in practice |
| **Host-side Python (BlueZ)** | `omblepy` / `bleak` on the PC talks to the cuff, then we HTTP/WebDAV or `adb` the samples in. **Does not exercise Android BLE permissions or GATT.** Useful to learn the protocol and to log real EEPROM dumps. | Yes — this is the only clean “leverage BlueZ” path |

**Recommendation:** implement and unit-test against recorded GATT bytes. For a live cuff, use a **physical phone** first. For a live cuff on the desktop without a phone: host `omblepy` (BlueZ) to prove the protocol, **or** emulator + spare USB BLE dongle + Bumble (Android stack, not BlueZ). Do not plan on Waydroid + laptop Bluetooth.

## Test plan (when we implement)

1. **No radio (CI)** — fixtures: advertisement name, bond, characteristic UUID map, one EEPROM dump → N samples. Assert ids stable, `source=bluetooth`, values finite, `recordedAt` from cuff clock.
2. **HCI snoop** — on a phone with OMRON Connect (cuff unpaired from Tracker): Developer options → HCI snoop, one sync, `adb bugreport`. Parse with Wireshark / `bumble-show --format snoop`. This is how we pin the proprietary commands for *your* HEM model.
3. **Host BlueZ smoke** — `bluetoothctl` / `omblepy -d HEM-<your model>` on the laptop. Confirms the cuff is BLE, the name, and that we can dump records. Output becomes more fixtures.
4. **Phone integration** — install debug APK, pair, one measurement, confirm Tracker row `118/76 mmHg, 72 bpm` with `source` shown. Unpair OMRON Connect first.
5. **Emulator (optional)** — only if we have a dongle and Bumble; otherwise skip. Netsim can host a **fake** Omron peripheral (Bumble GATT script) so UI scan/pair works without hardware. That is the best automated “UI + BLE” test. It still does not use the host’s BlueZ radio.

## Prerequisites before writing GATT code

- Exact model printed on the cuff (HEM-####T / BP####).
- One HCI snoop of a successful OMRON Connect sync **or** an `omblepy` dump from this machine.
- Decision: phone-first vs dongle+emulator.

Until those exist, do not vendor a guessed proprietary parser. The ingest seam can stay as-is.
