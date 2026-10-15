# BLE stability validation — 2026-10-15

## Scope

Phases 32–35: foreground BLE connection counters, ten-minute trials, local result history and interrupted-run recovery. Physical testing uses the user's confirmed T12_485 remote and Nothing Phone (3a), Android API 36, through wireless ADB. No R12 receiver or Cube is present. The physical acceptance code only scans and discovers GATT services; it does not subscribe to notifications or send MAVLink or actuator commands.

## Repairs found during review and testing

- Active connection and trial durations use Android's monotonic elapsed clock. Changing the calendar clock cannot prematurely pass a running trial. Calendar timestamps remain available for saved history; elapsed recovery after process termination still depends on calendar timestamps.
- Save the active-run marker before exposing RUNNING. Disable starting another trial during storage operations, and display storage failures instead of silently discarding them.
- Manual and background disconnects finish a running trial immediately. Manual stop records its own reason rather than borrowing an earlier disconnect reason.
- Reconnecting resets received-byte/notification statistics while retaining the process-lifetime stability counters.
- Storage tests use separate temporary DataStore files and retain the operator's real history.
- UI tests wait for the fixture screen and scroll the Start button back into view after layout changes.

## Build and automated checks

- Production debug APK built and installed with `adb install -r`; existing app data retained.
- JVM suite: **312 tests passed**, zero failures, errors or skips.
- Android lint: **zero errors, 31 warnings**.
- Initial test APK compilation could not resolve an existing navigation extension. Recompiling the shared helper resolved it; the test APK then built successfully.
- First targeted phone run: all six storage tests passed; two of four UI tests passed. One UI test raced activity startup and passed in isolation; the other tapped without restoring visibility after changing the layout. The test synchronization fixes are described above.
- Final targeted phone run: **10/10 passed in 8.154 seconds** (six storage tests and four screen tests).

## Physical T12 connection results

The opt-in `remoteStopBackgroundReconnectAndTenMinuteContinuity` test passed on the real phone in **618.82 seconds**:

- Manual stop saved STOPPED with `Stopped manually`; GATT remained connected.
- Backgrounding closed the GATT link, saved STOPPED and recorded `App moved to background`.
- A full ten-minute foreground trial saved PASSED with a duration of at least 600,000 ms.
- Three separate GATT connections succeeded; unexpected disconnect count remained zero.
- Notification/byte counts remained zero and no receive characteristic was selected.
- The test disconnected its link on completion and removed its temporary keep-screen-on flag.

Screenshots of the PASSED screen and three successful connections were captured. The PASSED screen was visually reviewed: status, repeat-test action and saved-history section are legible. The main header still indicates no rover connection because no autopilot heartbeat was received.

## Interruption and remaining hardware coverage

- A separate opt-in test connected to the real remote, started a trial, confirmed its start marker and deliberately terminated the app process. Its `Process crashed` instrumentation result is the expected termination of this test harness, not an unexplained application crash.
- Reopening the app recovered that exact trial once as **INTERRUPTED**; the recovery assertion and visible history check passed in **3.274 seconds**.
- A second app launch passed the same exact-once check in **3.655 seconds**; no duplicate result was added.
- Remote power-off could not be completed because the user could not switch off the T12. That exploratory test expected FAILED, but the app went into the background and correctly saved **STOPPED** after 45.619 seconds with reason `App moved to background`; the test's expected-FAILED assertion timed out. This is not evidence of successful remote-loss detection. The opt-in power-loss test remains available for a later controlled run.
- No Cube/R12 end-to-end reception, notifications, telemetry payloads or commands were tested.

## Evidence

Generated logs and screenshots are kept locally under `app/build/ble-stability-acceptance-20261015/` (ignored by Git). JVM results are under `app/build/test-results/testDebugUnitTest/`; lint results are under `app/build/reports/lint-results-debug.*`.

## Interpretation

GATT continuity is evidence that Android maintained a connection to the remote. It does not establish notification delivery, MAVLink reception, radio range, receiver/Cube connectivity or rover operation. Phone-lock/background continuity is not a supported mode: backgrounding intentionally closes BLE and stops the trial.
