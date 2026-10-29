# Phase 111 — Independent multi-source selection

2026-10-29. User approved implementation and emulator testing. Baseline `61324c3de37b427d3045de23fb7d7c8e388019ed`, clean; model/version unavailable. See D-111-01.

## Goal and design

Exercise pre-heartbeat rejection, exact system and component filtering, selected-source RC and system-status values, and new-session reselection using independently generated synthetic frames. The test uses an in-memory Classic transport and production parser, session, router, repository, RC presentation projection, connection assessment and Compose panel. A test transport fails on any send. Production trust logic, persistence, permissions, dependencies and hardware remain unchanged.

The fixed synthetic fixture set uses the previously verified and unpacked PyMAVLink 2.4.49 wheel, common dialect, as documented in [Phase 109](phase-109-report.md). Version 1 and 2 frames for systems/components 99/1 and 42/2 each contain heartbeat, RC and SYS_STATUS; Phase 109 provides the selected system/component 42/1 frames. Other-source RC is 1100/1900 microseconds and battery is 30%, distinct from the selected fixture's 1500/1700 and 67%. The fixture files are bounded test-only data, contain no real device data and are duplicated for JVM and Android readers; both SHA-256 `0f70912e21103a7598d02b8dfcde4706923ef454ecb12e70d748f5651aab09cd`. Both matched the output of `scripts/generate-phase111-fixtures.py` using the unpacked wheel on `PYTHONPATH`. The generator performs no download or file writes.

## Verification

- `./gradlew.bat -I <temp>/mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 478 JVM tests, 0 failures/errors/skips; production app assembly up-to-date and Android test APK built. Lint passed; lint report task was up-to-date.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentSourcesRemainIsolatedAndReconnectSelectsAnew,com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentRadioOnlyAndMixedStreamsStayDistinct,com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentPymavlinkV1AndV2FramesReachClassicPanel'`: 3 passed on Pixel_3a_API_34_extension_level_7_x86_64; Android health gate passed. Evidence: `app/build/acceptance-20261029-144622-ba9fcb38/instrumentation.txt` and `android-health.txt`; four selected/reselected connection panels reviewed. Source IDs and values were asserted in test state and RC presentation projection, not visible in those panel screenshots.

## Limits and review

The tests cover specific unsigned synthetic streams, not real hardware, full MAVLink conformance, sender authentication, sensor validity or physical safety. The hardware team continues to locate the missing Cube heartbeat. Review expected source IDs and values, pre-heartbeat and other-source counters, reconnection, no-send guard, fixture provenance and unchanged production behavior. The baseline is an unexecuted recovery reference, not a verified release; any scoped reversal requires authorization.
