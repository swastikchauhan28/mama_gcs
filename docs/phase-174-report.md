# Phase 174 — synthetic MAVLink resynchronization

Date: 2026-11-05. User approved investigation and simulated MAVLink testing. Clean baseline `a00001f897b5c6ae38c4ea7e4a1891906884171e`; model `gpt-6.1-terra`. See D-174-01 and the live observations in [Phase 172](phase-172-report.md).

## Reproduction and change

A synthetic unsupported-message payload contained a false unsigned MAVLink 2 header with incompatible flags. Its declared length overlapped the next valid heartbeat. On the baseline, the parser skipped the false candidate's declared length and failed to emit that heartbeat: `MavlinkParserTest` ran 20 tests with one expected reproduction failure. The failure XML is preserved locally at ignored `app/build/phase174/parser-reproduction-failed.xml`.

`MavlinkParser.feed` now checks a flagged, unsigned candidate's checksum when its message ID has known CRC metadata. If the CRC is invalid, it resumes searching one byte later. A CRC-valid incompatible candidate, a candidate with unknown CRC metadata, or any signed candidate still keeps its entire frame boundary and is rejected. The parser still accepts supported unsigned messages only after the existing CRC and payload checks; the MAVLink session still applies the existing autopilot source selection and five-second heartbeat timeout. No command, connection or UI behavior changed.

The untrusted input path is network/Bluetooth bytes → bounded transport chunks → parser's `pending` suffix (at most 280 bytes) → decoded message or diagnostic result → session source selection → vehicle state. The pending array is in memory and reset per session. A corrupt unsigned candidate can be rescanned and may contain another CRC-valid frame; unsigned CRC is not authentication. A malicious peer can forge unsigned traffic or cause denial of service. Unknown-CRC and false signed candidates can still consume later messages because their boundaries cannot safely be verified by this rule. Future incompatible flags may change framing semantics; this focused rule is not proof of universal compatibility.

## Verification

- Initial reproduction: `./gradlew.bat :app:testDebugUnitTest --tests com.mamadrones.gcs.data.mavlink.MavlinkParserTest --no-daemon --max-workers=1 --console=plain` failed only the new reproduction on the baseline, as expected.
- Targeted parser, MAVLink 1 and session tests passed after the initial fix: 55 tests in three suites, no failures/errors/skips.
- Final `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed: 97 tasks, 24 executed and 73 up-to-date. JVM XML results: 491 tests in 79 suites, zero failures, errors or skips. Lint: 0 errors and 27 warnings. Android test APK was built but instrumented tests were not run.
- New tests exercise every two-chunk split and bytewise input, valid incompatible and unknown-CRC boundaries, signed-plus-incompatible and embedded signed frames, and a synthetic session in which recovery refreshes liveness but a real timeout still degrades it. Existing signed rejection and other parser tests remain enabled.

No phone, Cube, live stream, actuator or vehicle command was used. The synthetic reproduction is an app defect but does not prove it caused the real TCP flicker or the observed counters. Review the parser and security diff, and compare a suitable stream with an independent decoder before live validation. No production-qualified rollback artifact is established; rollback is a reviewed inverse of this focused source/test/docs diff. Commit and push require a separate request.
