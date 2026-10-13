# Phase 29 — Read-only MAVLink message inspector

## Delivered

- Analyze tools → Message inspector is available through the existing responsive Analyze category navigation, without opening a telemetry link.
- Search by message name or numeric ID substring, with an optional Only observed messages filter. The full list describes the app's finite decoder subset, not an installed vehicle's capabilities or required streams.
- The eleven supported types are HEARTBEAT, SYS_STATUS, GPS_RAW_INT, ATTITUDE, GLOBAL_POSITION_INT, SERVO_OUTPUT_RAW, RC_CHANNELS, VFR_HUD, BATTERY_STATUS, AUTOPILOT_VERSION and STATUSTEXT.
- Each decoded type has MAVLink 1/2 counts, accepted counts, counts ignored before autopilot selection, counts ignored from other sources, last-decoded time (any source) and last-accepted time (selected autopilot).
- Counters are populated after successful decoding and the existing session's source-admission decision. Raw byte chunks, checksum failures, malformed/unsupported messages, unsupported flags and rejected signed frames do not create rows or accepted observations. Their existing aggregate counters remain under Link & messages.
- Recently decoded traffic from another source cannot refresh the selected autopilot's last-accepted timestamp. A five-second display window distinguishes recent/older accepted messages, with an approximately one-second screen timer. This is not an expected stream rate, stream request or safety timeout.
- Counts are memory-only, with at most one row per supported type. New session creation resets them; disconnect retains last-session counts labeled historical. Immutable snapshots are preserved. No raw payloads, device identities or coordinates are added to the inspector, and its per-message data is not added to report exports.

## Boundaries

Message receipt does not authenticate the rover, validate sensor values, prove each physical hop or grant drive permission. The screen sends no messages, requests, commands or parameter writes. Existing CRC/source selection, signed-frame rejection, exclusive transport ownership and receive-only boundaries are unchanged. The accepted hardware record and 8 km/h maximum are unchanged; unresolved stopping, authority and speed-enforcement work is not bypassed.

No hardware test is performed merely by building or exercising this screen. Physical reception through the user's existing HC-05/T12/R12/Cube route remains a separate acceptance activity. The Phase 28 emulator checks were previously blocked by Android System UI startup failure, not a recorded app test failure.

## Validation

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 5m 54s**.
- **289 JVM tests passed, zero failures/errors/skips**, including 13 new accumulator/projection cases. Existing session integration tests now also verify per-type admission counts, history retention and reconnect resets without transmission. Lint: **0 errors, 30 existing warnings**.
- Targeted API 34 emulator run: **11/11 passed in 89.791 seconds**. Three inspector cases verify search/filtering, historical counts/reset and wall-time expiry; one workspace case verifies navigation without a link. The seven Phase 28 checklist/Classic Bluetooth cases also passed. Evidence: `app/build/acceptance-20261013-164153-9963c61d/instrumentation.txt` (`OK (11 tests)`). This is not a full Android-suite run.
- Visually reviewed `app/build/qa-repair2/phase29-inspector-light.png`, `phase29-inspector-dark.png`, `phase28-check-light.png` and `phase28-check-dark.png`. Landscape fixture captures show readable text with no overlapping rows in both themes. Portrait, enlarged text, physical-phone layouts and real radio reception still need acceptance; these fixtures do not verify basemap loading.
- A fresh, isolated emulator data directory was used; previous AVD data was not wiped. Android System UI reported a startup ANR before APK installation/testing. After inspecting `app/build/qa-repair2/phase29-startup.png`, one **Wait** action allowed recovery. Launcher readiness and the runner's ANR/crash preflight then passed. No error dialogs were dismissed or other UI clients used during the successful run. The emulator was shut down afterward.
- `git diff --check` passed. No physical phone/HC-05/Cube connection, outgoing vehicle command or parameter change was performed. The hardware route remains user-confirmed, not independently certified by these software tests.

## Manual review

1. Use an approved, secured stationary telemetry setup or synthetic source. Keep movement and actuator operation independently inhibited. Open the existing receive-only link explicitly; this screen does not connect for you.
2. Open Menu → Analyze tools → Message inspector. With no session, all types should say NOT OBSERVED. This is not a list of missing hardware.
3. Search `HEARTBEAT` or `0`. Once selected-autopilot heartbeat frames arrive, inspect Accepted and Last accepted, not just Decoded. Search is a substring match; `0` can match more than one numeric ID.
4. Search GPS, ATTITUDE, BATTERY or AUTOPILOT_VERSION and distinguish unobserved, decoded-but-filtered and accepted messages. A rare message may become older without indicating a fault. The app does not ask the Cube to change rates.
5. Turn on Only observed messages. Clear the search to see all observed supported types. For unsupported/rejected input, return to Link & messages; this inspector deliberately does not classify unknown raw traffic as valid telemetry.
6. Disconnect through the relevant link screen: remaining counts must say last-session history. Start a new receive session: prior rows must reset rather than carrying over accepted evidence.
7. Check scrolling, light/dark themes, portrait/landscape and enlarged text. All movement, mission execution and emergency-stop commands must remain unavailable.
