# Phase 110 — Independent radio-only and mixed-stream checks

2026-10-29. User approved implementation and emulator testing. Baseline `700e45f4871327ba2c70c7d9ad5c8e122a116ef3`, clean. Model/version unavailable. See D-110-01.

## Goal and scope

Independently generated RADIO_STATUS frames must not become Cube-heartbeat evidence; a separately received valid heartbeat must be recognized. Test-only: no production behavior, dependencies, persistence, permissions, hardware or outbound commands changed. The emulator test exercises the in-memory Classic transport, real parser, session, router, repository, connection assessment and actual panel. It checks corrupt radio, fragmented radio, radio-only state, radio plus heartbeat, diagnostics and fresh-session reset for both wire versions; its transport fails any send. The JVM test checks independently generated radio field values, checksum rejection and every frame split.

The two synthetic common-dialect fixtures use PyMAVLink 2.4.49 from the already verified temporary wheel described in [Phase 109](phase-109-report.md); no download or production dependency was added. Source system/component are synthetic 68/68; fixture radio values are 95/100 RSSI, 80 TX buffer, 4/5 noise, 17 errors and 23 corrected packets. Fixtures are test-only, bounded, contain no hardware data, and are duplicated for JVM and Android asset readers. Both file SHA-256 values are `275236f6ba55b15d979706e1d7b4521f0c7fb8802da4b69d78338c043374724b`. `scripts/generate-phase110-fixtures.py` prints reproducible fixtures from the verified unpacked wheel on `PYTHONPATH`; it does not download or write files. Both copies matched its output.

## Verification

- `./gradlew.bat -I <temp>/mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: JVM: 477 tests, 0 failures/errors/skips; production app assembly up-to-date; Android test APK built. Lint passed; lint report task was up-to-date.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentRadioOnlyAndMixedStreamsStayDistinct,com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentPymavlinkV1AndV2FramesReachClassicPanel,com.mamadrones.gcs.ClassicConnectionCheckUiTest'`: 14 passed, Android health gate passed on Pixel_3a_API_34_extension_level_7_x86_64. Evidence: `app/build/acceptance-20261029-141800-0b1e57e4/instrumentation.txt` and `android-health.txt`. Radio-only and mixed screenshots for both versions were visually reviewed; the radio diagnosis title was asserted by the test below the visible screenshot crop.

## Limits and review

Synthetic unsigned frames do not establish physical Cube-to-phone transmission, radio source authentication, sensor validity or safe control; the hardware team is still localizing the missing heartbeat. This does not establish exhaustive MAVLink interoperability. Review generator provenance, fixture consistency, assertions and no-send guard; no production dependency or device data should be present. Changed files were inspected for secret exposure; this is not a repository-wide secret scan. Baseline is a recovery reference, not a verified release; any scoped reversal requires authorization.
