# Phase 28 — HC-05 receive checklist

## Delivered

- Application settings → Bluetooth / HC-05 now includes an in-page connection check. Existing pairing, explicit device confirmation, receive-only session ownership and disconnect/cancel controls are preserved.
- Four stages distinguish the Bluetooth socket, recent bytes at the decoder, a fresh selected-autopilot heartbeat and recent measurement messages. Socket `OPEN` is not the same as telemetry reception.
- Guidance covers closed/connecting/error states, mismatched session evidence, no bytes, stale bytes, missing/stale heartbeat and heartbeat-only reception. It points to existing decoder diagnostics without automatically changing radio settings or disabling signing.
- Only the active Classic decoder session is considered. Session-start/socket-open boundaries exclude earlier samples; disconnected, other-link, missing, negative and future timestamps cannot count as live evidence.
- Byte, heartbeat and measurement timestamps use a 5-second display window and an approximately one-second on-screen timer. Current bytes cannot substitute for a missing heartbeat, and heartbeat/accepted-message counts alone cannot establish measurement reception.
- Measurement groups checked are GPS, position, attitude, battery, Rover HUD, system status and RC input. Each group needs a recent receipt timestamp. Not every group must be present. A received message can still report invalid/unknown sensor data or no GPS fix; the panel explicitly distinguishes receipt from sensor validity.
- The existing serial-link heartbeat row now uses the same current-session assessment instead of borrowing the global connection label.

## Boundaries

This is a display-only diagnostic helper. It does not independently prove the physical Cube–R12–T12–HC-05 wiring, authenticate a vehicle, verify sensor accuracy or authorize operation. The user's earlier confirmation of that telemetry path remains in the hardware record and is not being requested again.

No new transport, outgoing heartbeat, stream-rate request, baud-rate change, parameter write, command sender, safety override, background reception or automatic reconnect is introduced. Movement, mission execution and emergency-stop commands remain unavailable. Five seconds is a UI freshness policy, not an approved hardware failsafe timeout. No real hardware is contacted during software validation.

## Validation

- Final `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 3m 56s**, after the initial successful 9m 35s run. Final APK includes the wording correction to connection-cancel guidance.
- **276 JVM tests, zero failures/errors/skips**, including 13 new current-session/freshness cases. Lint: **0 errors, 30 existing warnings**. `git diff --check` passed.
- Three new Android UI tests compile: light/dark checklist and guidance rendering, heartbeat-only reception, and screen timer expiry/disconnect. The four existing Classic Bluetooth screen tests also compile against the updated signature. **These seven tests were not executed in this phase.**
- The isolated API 34 emulator completed boot with an Android **System UI ANR before APK installation or instrumentation**. `/sdcard` screen capture was denied; a subsequent capture through `/data/local/tmp` hung. No dialog was blindly dismissed, no app tests were started, and no visual pass is claimed. The isolated emulator was stopped using its own ADB emulator endpoint. Startup logs: `app/build/qa-repair2/emulator-phase28-stdout.txt` and `emulator-phase28-stderr.txt` (local build evidence, not committed).
- Remaining validation: run `ClassicConnectionCheckUiTest` and `ClassicBluetoothScreenTest` on a stable emulator; visually check both themes, portrait/landscape and large text; then perform the stationary physical receive walkthrough below. No physical phone/remote/Cube test or command transmission has been performed in this phase. Software results are not hardware or safety certification.

## Manual connection check

1. Have the hardware team secure the stationary rover and independently prevent motor/actuator operation. Leave it disarmed; do not use the app's unavailable emergency-stop control as protection.
2. Power the existing Cube/R12/T12 setup using the hardware team's procedure. Keep the phone near the T12. Do not rewire or change the accepted parameter settings for this check.
3. Close other phone apps using the remote's Bluetooth connection. Turn on phone Bluetooth and pair the identified HC-05 in Android settings if it is not already paired. Use the hardware team's PIN if Android requests one; do not guess a different device.
4. In Mama GCS open Application settings → Bluetooth / HC-05. Close another active UDP/BLE telemetry session if the app reports one. Allow Nearby devices permission if requested and tap Refresh paired devices.
5. Select your identified HC-05 by name and address, tap Connect for telemetry, then confirm Start receive. This starts reception only; it does not arm or command the rover.
6. Keep Mama GCS in the foreground. In HC-05 connection check, observe Bluetooth socket OPEN, recent bytes RECEIVED, heartbeat FRESH and one or more named measurement groups. The route printed on screen is the expected route, not per-hop detection.
7. If a stage is missing, follow its on-screen guidance. For bytes without a fresh heartbeat, inspect Decoder guidance and counters. Do not alter baud rates, signing or failsafes merely to make the checklist pass. Share the displayed status/error with the hardware team.
8. Open All telemetry and compare available live readings with the stationary vehicle. Unknown values or no GPS fix can remain even when messages are arriving. Receipt does not validate those sensors.
9. Tap Disconnect Classic. The checklist must stop claiming live evidence. Reconnecting requires explicit selection/confirmation; leaving the app in the background closes reception. This is not a movement, radio-loss failsafe or emergency-stop test.
10. To save diagnostic evidence, use Analyze tools → Link & messages → Prepare telemetry report, review it and explicitly save it. No report is automatically sent or shared.
