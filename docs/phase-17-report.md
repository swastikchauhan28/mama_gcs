# Phase 17 — HC-05 / Bluetooth Classic MAVLink receiver

## Delivered

- Application settings → Bluetooth / HC-05, also reachable from Rover configuration → HC-05 / Classic.
- Explicit refresh of already-paired Classic/dual-mode devices, names and addresses, and confirmation of the chosen radio. Pairing is performed in Android settings; there is no name-based auto-connect, PIN guessing or discovery scan.
- Android 12+ Nearby devices / BLUETOOTH_CONNECT runtime permission. This paired-device flow does not request scan or location permission. Existing BLE discovery retains its separate permissions.
- Secure RFCOMM using the standard SPP service UUID `00001101-0000-1000-8000-00805f9b34fb`. Socket connect/read run off the UI thread. Android pairing is not trusted application-level vehicle identity.
- Twenty-second connection timeout; cancelling or closing the link aborts blocking socket operations. End-of-stream, permission/pairing failure and receive-buffer overflow end the session visibly.
- Stream fragments feed the existing MAVLink 2 parser. The first accepted autopilot heartbeat establishes protocol liveness, and existing source filtering, heartbeat expiry and signing policy apply. Classic input chunks and shared decoder byte/message counters are displayed separately.
- The same ownership gate prevents concurrent UDP, BLE and Classic producers, including during connection setup. Closing an unrelated Classic screen cannot clear another transport's vehicle state. A cancelled, closed MAVLink session cannot later clear a replacement session during its delayed startup cleanup.
- The connection remains while navigating within the app. It closes when the app process goes into the background, including when entering Android settings; reconnect is always manual. It does not survive process death.

There is no application-byte transmit path: no GCS heartbeat, stream-rate request, AT command, motor command or baud configuration. The serial sender must already output supported MAVLink 2 telemetry. Native Bluetooth setup traffic still occurs. MAVLink 1, VESC packets and custom remote-control bytes are not decoded by this receiver.

## Phone acceptance procedure

1. Identify the HC-05 belonging to the remote with the hardware team. In Android Bluetooth settings, pair using the hardware team's configured PIN. Finish discovery and close any other app using the remote.
2. Open Mama GCS → Menu → Application settings → Bluetooth / HC-05.
3. Select Refresh paired devices; allow Nearby devices if requested. Verify both name and address, select Connect for telemetry, then Start receive.
4. A serial OPEN status alone is not telemetry. Check input bytes, accepted MAVLink messages and a fresh heartbeat, then visit Operate and Instruments to compare reported data against the source.
5. If bytes increase without accepted messages, confirm that the remote actually forwards MAVLink 2. If bytes stay at zero, check the sender and bridge configuration. Do not change live rover settings just to make this test pass.
6. Disconnect and reconnect explicitly. Background the app and confirm that the receiver closes. Verify that active UDP/BLE connections block Classic and vice versa.

## Hardware evidence received, not applied as configuration

- User identifies the phone/remote adapter as HC-05, and separately identifies a Flipsky V6 NRF51 motor-data adapter.
- Latest reported Cube wiring: MAIN OUT 1 right motor, MAIN OUT 2 left motor. This supersedes the earlier reported outputs 1/3 but still needs parameter/wiring verification.
- Differential/skid steering; no steering servo.
- Reported 1000/1500/2000 values belong to remote inputs, not verified ESC outputs. Forward/left 2000, neutral/centre 1500, reverse/right 1000.
- Stated maximum speed is 8 km/h (approximately 2.22 m/s). It has not been installed as a validated controller limit.
- PWM motor control and a reported UART data route into TELEM2. The actual endpoint, protocol conversion, baud rate and forwarded motor messages remain unverified.

Missing configuration does not block this receive-only software phase. Motor movement, arm/disarm, emergency stop, mission execution and live VESC integration remain unavailable.

## Validation

The debug app and instrumentation APK compile successfully. The JVM suite passes 135 tests with zero failures, errors or skips. This includes seven new Classic socket tests, four Classic session/controller tests and one MAVLink closed-startup regression test. They cover stream ordering, fragmented heartbeat delivery to the repository, EOF, timeout, permission/pairing errors, cancellation, buffer overflow, disabled transmission and exclusive session ownership. Debug lint passes with zero errors and 29 warnings; none point to the new Classic implementation.

The focused API 34 Pixel emulator run did not complete successfully: three UI cases passed (device confirmation, cancellation and closed-session heartbeat isolation); the competing-link UI case failed with no Compose hierarchy while Android displayed a System UI ANR; the landscape navigation case was aborted by `INSTRUMENTATION_ABORTED: System has crashed`. The crash buffer shows failures in `com.google.android.bluetooth` and a fatal system-server `Lost network stack` exception. These are environment failures, not passing UI acceptance. The competing-link rule is covered by passing JVM tests, but that does not replace rerunning the two unresolved UI checks on a stable emulator. No Bluetooth radio was opened by the UI fixtures. The temporary test emulator was stopped afterward; the pre-existing emulator was left running.

Physical HC-05 reception, Android permission flows and Cube/VESC compatibility still require the actual devices. They are not established by emulator or fake-socket tests. Basemap loading and full-app release acceptance were outside this focused run.

## References

- [Android Bluetooth connections](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [BluetoothSocket cancellation and stream API](https://developer.android.com/reference/android/bluetooth/BluetoothSocket)
