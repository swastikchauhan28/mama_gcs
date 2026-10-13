# Phase 27 — Read-only rover speed warning

## Delivered

- Operate/Map has a compact speed monitor; Drive and All telemetry show the detailed monitor.
- Displays ground speed in km/h against the operator-confirmed **8 km/h** maximum. It distinguishes below, at and above the maximum using the unrounded value; displayed numbers have two decimal places. Even a value rounded to `8.00` can correctly be above the threshold.
- Uses existing `GLOBAL_POSITION_INT` horizontal speed only, not VESC RPM or a silent fallback to another source. Conversion is m/s × 3.6. The Operate speed instrument now uses km/h; existing detailed m/s instruments remain available.
- Requires a connected vehicle, valid non-negative finite speed, and independent heartbeat/speed timestamps no older than 5 seconds. Missing, future, negative, old or invalid timestamps/values produce UNKNOWN, never zero. Disconnected/degraded/error states hide the previous speed comparison.
- An approximately one-second on-screen timer expires readings even without new telemetry. The 5-second window is a display policy, not a tested safety deadline. Screen/background scheduling and delayed telemetry mean this is not a real-time safety system.
- Above-limit observations use warning text and colour. Below-limit or zero readings do not claim safe operation or a verified stop. No warning sounds, historical alarm log or background notification are added.

## Boundaries

This is **not a speed limiter or emergency stop**. It neither brakes nor transmits commands, changes Cube parameters, grants drive permission, changes the safety gate, or resolves the accepted file's 14.4 km/h cruise target. All movement and mission execution controls remain unavailable. No physical tests or hardware configuration changes are performed. The earlier user-confirmed telemetry-reception note is retained without treating it as independent command/safety validation.

## Validation

- Final `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 1m 14s**, following the initial successful 6m 42s run. The final run includes the compact instrument-strip layout adjustment.
- **263 JVM tests, zero failures/errors/skips**, including 10 new speed-observation cases. Lint: **0 errors, 30 existing warnings**.
- Targeted API 34 emulator suite: **8/8 passed in 231.424 seconds**. Four new speed-monitor cases cover boundary states, disconnect, wall-time expiry without new messages, and locked drive actions. Existing landscape-overlap, initial-dashboard safety and portrait/landscape workspace regressions passed. Evidence: `app/build/acceptance-20261013-114957-c8760a55/instrumentation.txt` (`OK (8 tests)`). The full Android suite was not rerun.
- Visually reviewed `app/build/qa-repair2/phase27-speed-light.png`, `phase27-speed-dark.png`, `phase27-operate-landscape.png` and `phase27-operate-portrait.png`. Warning text is readable in both themes; the compact monitor fits the captured layouts and the existing horizontally scrollable instrument strip. Basemaps were blank in the workspace captures; tile-loading readiness is not validated by these tests. Larger fonts, smaller screens and physical-phone review remain outstanding.
- The emulator developed an Android System UI startup ANR after APK installation. The runner refused to begin tests. After inspecting `app/build/qa-repair2/phase27-startup.png`, one **Wait** action allowed recovery; the next readiness check passed. No error dialogs were dismissed or competing UI automation attached during the successful run. The isolated emulator was shut down afterward.
- `git diff --check` passed. No real phone/rover test, parameter upload or movement command was performed. Software test results do not validate physical speed accuracy, stop behaviour or limit enforcement.

## Manual acceptance

1. With an approved stationary/simulated telemetry source, check the monitor shows km/h and its warning-only label. Do not drive the rover above the limit to test this display.
2. Use simulated values below, at and above 8 km/h; verify the status and two-decimal reading. Compare against source velocity, not rounded text.
3. Stop simulated speed messages but retain heartbeat: after the freshness window the monitor must become unavailable. Repeat for missing heartbeat.
4. Disconnect: the monitor must hide old readings. Verify drive actions remain disabled in all cases.
5. Check portrait/landscape, large text and both themes. Physical reception, speed calibration, braking, failsafes and speed-limit enforcement require separate hardware acceptance.
