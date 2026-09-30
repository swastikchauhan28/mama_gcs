# Phase 3 MAVLink HEARTBEAT report

Date: 2026-09-30. Scope: receive-side MAVLink lifecycle and HEARTBEAT-derived vehicle liveness only. No control, command transmission, general telemetry, or automatic reconnect was added.

## Implemented

- An explicit `MavlinkSessionFactory` binds the selected `VehicleTransport` to a session, parser, router, and singleton vehicle-state repository. The transport manager owns and stops/closes this session with the same foreground UDP lifecycle.
- The session subscribes before opening the transport, so datagrams arriving during socket startup are not missed. Parser and selected autopilot identity reset at every session start.
- The incremental MAVLink 2 parser handles HEARTBEAT framing, CRC-extra/checksum validation, and the 9-byte HEARTBEAT payload. It handles chunked input, rejects unsupported incompatibility flags, rejects signed packets because verification keys are not provisioned, and resynchronizes after invalid checksums.
- The first CRC-valid HEARTBEAT with a nonzero system/component ID and non-`MAV_AUTOPILOT_INVALID` autopilot type is selected for the session. Other identities and non-autopilot components are ignored. Socket-open state stays distinct from vehicle liveness.
- A HEARTBEAT timeout degrades vehicle state while the UDP transport may remain open. Stopping the session clears selected vehicle connection state.
- Settings explains that the open action starts the receive session, that HEARTBEAT liveness is not authenticated identity, and that this session sends no vehicle commands.

## Boundaries

Only HEARTBEAT is decoded. Position/GPS, attitude, battery, VESC, spray, hydraulic, health, and mission fields remain unknown. No SITL executable or vehicle endpoint was configured in this environment; automated fake-transport/parser tests validate the receive lifecycle and generated MAVLink frames, but do not replace ArduPilot SITL, radio, or hardware acceptance testing.

Unsigned CRC-valid frames are not authenticated. Fixed-peer UDP filtering is not cryptographic identity. Signed frames are rejected rather than treated as verified. MAVLink signing-key provisioning and verification, replay protection, command outcomes, controls, and secure pairing remain future work.

## Validation

`:app:testDebugUnitTest` passed: 32 tests, 0 failures. `:app:lintDebug`, `:app:assembleDebugAndroidTest`, and `:app:assembleDebug` also completed successfully. Coverage includes incremental/invalid frame handling, signed and incompatibility-flag rejection, parser reset/resynchronization, autopilot selection and identity pinning, session timeout, and transport lifecycle. See the Gradle test report at `app/build/reports/tests/testDebugUnitTest/index.html`.

No emulator/device was attached (`adb devices` returned an empty list), so connected UI tests were not run. No SITL executable or vehicle endpoint was configured either. Previous-phase validation is recorded in [the Phase 2 report](phase-2-report.md); it is not represented as a fresh Phase 3 emulator or SITL run.
