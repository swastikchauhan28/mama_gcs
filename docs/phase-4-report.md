# Phase 4 telemetry report

Date: 2026-09-30. Scope: receive and display the Phase 4 MAVLink telemetry allowlist. The session remains receive-only; no message-rate requests, vehicle commands, map rendering, health verdicts, or hardware subsystem telemetry were added.

## Implemented

- Added MAVLink 2 CRC and payload decoding for `GPS_RAW_INT`, `GLOBAL_POSITION_INT`, `ATTITUDE`, `SYS_STATUS`, `BATTERY_STATUS`, and `STATUSTEXT`, alongside the existing `HEARTBEAT` decoder. MAVLink 2 truncated extension payloads are accepted only within each message's defined minimum/maximum length; malformed base lengths and bad CRCs remain rejected.
- Added domain state for GPS raw measurements, filtered global position, attitude in radians, autopilot sensor/status bitmaps, per-battery instances, and a bounded in-memory status-text list. MAVLink IDs, units, sentinels, and conversions are kept in the data mapper rather than the UI.
- Routed telemetry only after a valid autopilot HEARTBEAT selects the session identity, and only when each message's system/component IDs match that pinned source. A new session clears prior telemetry to avoid displaying old measurements as current.
- Converted coordinates from degrees-E7, MSL altitude from millimeters, horizontal speed from north/east centimeters-per-second, heading from centidegrees, attitude radians, and battery values from protocol units. Direction is not guessed from speed.
- Unknown/sentinel values remain null: no GPS fix, invalid satellite/HDOP, unknown heading, battery sentinels, and non-finite or out-of-range attitude angles are not converted to zero. Position coordinates outside geographic bounds are withheld.
- SYS_STATUS aggregate battery readings stay separate from BATTERY_STATUS readings keyed by battery ID; multiple packs are not collapsed into an invented total. Temperature remains unknown when not reported.
- Added bounded STATUSTEXT chunk reassembly (8 partial messages, 1,000 bytes each, 5-second partial expiry) and retain at most 50 complete messages in memory. Severity is shown as reported; it does not trigger a health verdict.
- Dashboard/diagnostics cards display supported telemetry and age since each message group was last received. Age is informational; without configured sensor rates or hardware thresholds, the app does not invent a universal stale/healthy cutoff.

## Boundaries and assumptions

The autopilot must already be streaming the supported MAVLink messages. This phase does not request message intervals because that requires MAVLink transmission. VFR_HUD, health evaluation, live map markers, persistent diagnostic logs, VESC, spray, and hydraulic telemetry are not implemented. No SITL, radio, vehicle, or Android emulator was available for live integration acceptance; generated-frame, parser, repository, and fake-transport tests are not a substitute for that validation.

Unsigned frames are still unauthenticated, and signed frames remain rejected without a provisioned verifier. GPS_RAW_INT is the raw GPS sensor sample; GLOBAL_POSITION_INT is retained separately as the filtered global estimate. Sensor bitmaps and STATUSTEXT are exposed as reported, not interpreted as a safety/health determination.

## Validation

`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-build-cache --no-daemon` passed. All 46 unit tests passed with no failures or errors. The suite covers supported message CRC/payload layouts, conversion and invalid-value handling, battery IDs, source pinning, bounded STATUSTEXT reassembly, and the existing Phase 3 transport/session cases. The debug APK and Android-test APK compiled; instrumentation was not run because no emulator was available. See `app/build/reports/tests/testDebugUnitTest/index.html`.
