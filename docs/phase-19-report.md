# Phase 19 — MAVLink 1/2 receive compatibility

## Delivered

The shared receive parser now recognizes MAVLink 1 and unsigned MAVLink 2 per frame on UDP, BLE and Bluetooth Classic. No transport selection or Cube configuration change is required in the app. This phase supersedes Phase 17's MAVLink-2-only limitation; it does not prove which format the user's remote sends.

- MAVLink 1 header offsets, checksum and exact base payload lengths for the existing eight supported messages: HEARTBEAT, SYS_STATUS, GPS_RAW_INT, ATTITUDE, GLOBAL_POSITION_INT, VFR_HUD, BATTERY_STATUS and STATUSTEXT.
- Fragmented, concatenated and mixed-version input through the existing bounded pending-frame buffer. CRC failures trigger resynchronization. As before, a corrupt length can delay recovery until enough bytes arrive to complete that candidate; raw noise and incomplete input need not yield a parser result.
- Version 1 truncation and extension bytes are rejected; absent battery/status-text extensions remain unavailable/default protocol values. Version 2 zero restoration, supported extensions and rejection of signed/unsupported-flag candidates are preserved.
- Separate `Decoded MAVLink 1` and `Decoded MAVLink 2` rows in the shared decoder panel. These count CRC-valid supported decoded frames, including messages later excluded by source filtering, not all received packets. They are not inferred from HEARTBEAT's `mavlink_version` payload field. Counts remain as last-session history on disconnect and reset with a new session.
- First valid autopilot source selection, competing-source filtering and heartbeat expiry still apply across both versions. No source switch is triggered by a version change.

## Boundaries

All transports remain receive-only. No GCS heartbeat, stream-rate request, protocol negotiation, control command, SBUS decoder, VESC decoder, pairing or signing verification is added. The CRC checks corruption, not authenticity; unsigned MAVLink 1/2 data is not trusted vehicle identity. Do not turn off a live rover's signing to make this receiver accept it.

The user identifies the receiver as Skydroid R12 / Multi Link V1.0 and confirms its SBUS connection to Cube RCIN. Photos appear to show a separate four-wire TELEM1 connection, but exact pin continuity, serial configuration and forwarded data remain unverified. Remote model, phone-facing interface, installed firmware and current Cube parameter file are still needed. SBUS control wiring alone does not establish a phone-to-Cube MAVLink path.

## Acceptance procedure

1. Use an approved simulator or hardware-team-secured stationary receive-only setup. Do not change the rover's protocol or move its wiring just for this test.
2. Open the appropriate existing UDP, BLE or Classic receiver. Check input bytes, version counters and a fresh accepted autopilot heartbeat in Analyze tools → Link & messages.
3. Compare displayed instruments to the source. A socket opening or a version counter increment does not by itself prove selected-vehicle liveness.
4. Stop the source and confirm heartbeat loss. Close and reopen the receive session to confirm version counters reset. Controls remain unavailable.

## Validation

The debug app and instrumentation APK build successfully. All 166 JVM tests pass with zero failures, errors or skips. Sixteen new checks cover a fixed wire vector with an independently calculated CRC, every heartbeat split, all eight supported base messages, invalid lengths, mixed-version streams, CRC recovery, parser reset, missing extensions, signed-frame rejection, source selection, heartbeat expiry and diagnostic projection. Debug lint succeeds and `git diff --check` passes.

Verified with JDK 17: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`.

No instrumentation tests were executed on an emulator or phone in this phase. No physical radio compatibility, live Cube/remote protocol or device UI acceptance is claimed. Existing emulator and Android Studio processes were left untouched.

## Protocol reference

[Official MAVLink packet serialization](https://mavlink.io/en/guide/serialization.html) describes version-specific framing, checksum coverage, full version-1 payloads and version-2 truncation. This remains an explicitly limited telemetry decoder, not a full MAVLink implementation.
