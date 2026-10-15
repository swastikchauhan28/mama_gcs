# Phase 33 — Timed T12 BLE stability check

## Delivered

- The BLE inspection screen has a **Start 10-minute stability test** action. It is enabled only while a GATT connection is open and the app has no competing telemetry link.
- The test reports remaining time while Mama GCS stays in the foreground. It passes after ten continuous minutes with no unexpected GATT disconnect, fails after an unexpected disconnect, and reports stopped if the link is intentionally closed or the app leaves the foreground.
- The test is connection-only. It does not subscribe to a BLE characteristic, inspect payload bytes, send MAVLink, or issue rover commands.
- The previous phase's BLE connection duration, session and unexpected-drop counters remain visible alongside the timed test.

## Limits

- Trial status and connection counters live in the app process and reset after process restart.
- A pass establishes only ten minutes of phone-to-T12 GATT connection continuity with the app foregrounded. It does not validate notification delivery, phone-lock behavior, range, Cube/R12 telemetry, or rover control.

## Validation

- `:app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 3m 10s**. Three existing `hiltViewModel` deprecation warnings remain.
- No automated tests were added or run in this phase. No live remote or vehicle was connected by the build.

## Manual check

1. Connect to `T12_485` with **Connect and inspect GATT**.
2. Start the 10-minute test and keep the phone unlocked with Mama GCS in front.
3. Let it run to **STABILITY TEST PASSED · 10 MINUTES**. If T12 disconnects unexpectedly, capture the failure reason and unexpected-disconnect counter.
4. Repeat after reconnecting to check another independent session.

No Cube or R12 receiver is needed for this remote-only check.
