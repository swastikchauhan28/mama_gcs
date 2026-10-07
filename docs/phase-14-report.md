# Phase 14 — BLE telemetry reliability

Date: 2026-10-07. Scope: foreground, receive-only telemetry reliability and diagnostics. No vehicle commands, automatic reconnect, pairing, or firmware updates.

## Corrections and delivery

- Fixed an app-navigation regression where a connected BLE link was also counted as an active UDP link, disabling BLE notification selection.
- A shared runtime lease admits only one UDP or BLE telemetry producer. Lease release follows session cleanup, and an old lease cannot release a newer owner. Closing UDP completes cleanup before a replacement UDP session can start.
- The MAVLink collector starts before the app enables the selected remote notification descriptor. Repeated connect/subscribe taps cannot overlap operations; switching characteristics requires disconnecting first.
- BLE callbacks run on the main handler. Callbacks from an old GATT connection, another characteristic, or an unrelated descriptor do not update the active stream. Service and characteristic instance IDs distinguish duplicate UUIDs.
- Discovery lists only notify/indicate characteristics with a Client Characteristic Configuration Descriptor. Notification setup errors, permission failures, timeouts, and remote disconnects close the GATT connection and allow explicit retry. Pending connection attempts can be cancelled.
- BLE diagnostics distinguish GATT connected, subscribing, waiting for bytes, received bytes, and the MAVLink heartbeat state. Notification count, byte count, and last-byte age are visible. Notifications are chunks, not MAVLink messages; receiving chunks does not establish a valid heartbeat or authenticated identity.
- A full receive buffer terminates the link rather than silently losing serial fragments. Scanner device handles remain bounded to the visible scan results, and stale scan callbacks are ignored.
- Corrected `VFR_HUD` wire offsets: altitude 8, climb 12, heading 16, throttle 18. Heading 360 is displayed as 0 degrees. MAVLink 2 trailing-zero truncation is restored for all supported base payloads after validating the original transmitted CRC; partial STATUSTEXT IDs and battery extension cells also restore their zero high bytes.

Protocol references: [official MAVLink serialization rules](https://mavlink.io/en/guide/serialization.html) and [generated common VFR_HUD wire definition](https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_vfr_hud.h).

## Validation

Validation completed:

- `:app:testDebugUnitTest`: 80 tests passed, no failures/errors/skips.
- `:app:assembleDebug`: passed; debug APK generated.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:connectedDebugAndroidTest`, restricted to `com.mamadrones.gcs.FoundationUiTest`: all 9 tests passed on the Pixel 3a Android 14 / API 34 emulator.
- `:app:lintDebug`: passed with 0 errors and 28 warnings (dependency-version notices, the API-specific manifest flag, and Compose modifier-parameter ordering).
- `git diff --check`: passed.

The first emulator run found a stale dashboard assertion that matched both GROUND SPEED and HUD SPEED. The assertion now targets GROUND SPEED exactly; the complete nine-test suite passed on rerun. The older navigation test's VESC Bluetooth label was also updated to BLE MAVLink.

Regression coverage includes shared-link ownership/release, concurrent admission, failed UDP open, canonical VFR_HUD payload values, every two-chunk split of that frame, truncated base payloads, original-wire CRC rejection, and short/partially extended STATUSTEXT. Compose tests cover BLE selection through app navigation, waiting-for-bytes wording, blocked resubscription, and cancelling a pending connection.

Android GATT callbacks require device integration testing. Compilation and JVM tests do not validate Bluetooth radio behavior, Android permission revocation, or Cube Orange compatibility. No physical acceptance is claimed.

## Phone acceptance checklist

1. Use a safe stationary test setup with the rover's independent physical safety system. The app emergency stop and all commands remain unavailable.
2. Confirm the radio carries raw **MAVLink 2** bytes over BLE notifications/indications. Obtain its device name, service UUID, characteristic UUID, Cube serial-port wiring/baud, and Rover firmware version from the hardware team. Do not select a VESC characteristic just because it advertises BLE.
3. Close UDP in Link Setup. Open Systems → BLE MAVLink, allow Nearby devices (or Location on Android 11 and earlier), scan, and connect to the confirmed radio.
4. Select its confirmed characteristic. Expect “subscribed / waiting for bytes” until notifications arrive. Then confirm notification/byte counters increase and last-byte age stays recent.
5. Confirm the heartbeat becomes CONNECTED and the observed system/component IDs and dashboard values match the Cube. Compare mode, armed state, GPS coordinates, speed, heading, battery, and VFR_HUD against an independent trusted telemetry display. Increasing byte counters alone is not success.
6. Stop only the telemetry stream while leaving BLE connected. Expect last-byte age to increase and heartbeat liveness to degrade. Restore the stream and verify recovery without invented values.
7. Disconnect/power off only the BLE radio when safe. Expect a link error and loss of connected vehicle status. Reconnect explicitly; counters and session identity should start fresh. Test cancel during connection and repeated taps; no duplicate receivers should appear.
8. Disconnect BLE, open the existing SITL UDP setup, and check telemetry. Close UDP and reconnect BLE. Neither link should start while the other owns a session.
9. Background the app. It must close foreground links; returning must not reconnect automatically. Test Bluetooth off/on and permission revocation using Android settings, then reopen/regrant explicitly.

If bytes arrive but heartbeat does not, confirm MAVLink 2 framing, unsigned-versus-signed policy, UART baud and notify UUID. This app still rejects signed MAVLink frames because key verification is not implemented; do not weaken a live vehicle's security just to satisfy this test. Use an approved test configuration.

VESC telemetry, spray/hydraulic adapters, authentication, command safety, mission upload/execution, and licensed offline map regions remain separate pending work.
