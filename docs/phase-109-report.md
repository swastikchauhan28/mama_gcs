# Phase 109 — Independent MAVLink interoperability fixtures

2026-10-29. User approved implementation and emulator testing. Baseline `c500ba2d4f97e3b6e9dceefa21ecf7e8e9ea26cc`, clean at start; model/version unavailable. See D-109-01.

## Goal and design

Validate synthetic MAVLink 1 and 2 frames generated independently of the application's checksum and serializer. The fixtures cover HEARTBEAT, RC_CHANNELS and SYS_STATUS, including MAVLink 2 truncated trailing zeros, bad checksums and every possible byte split. The Android test feeds them into the in-memory Classic transport, then the real parser, session, router, repository, assessment and Compose connection panel. The transport asserts no sends. No production code or dependencies changed.

Fixtures are eight fixed hex-encoded synthetic frames, system 42 and component 1, stored identically in JVM test resources and Android test assets. Both files have SHA-256 `a76a47a0e5e5f0f442c303b6c79b0e822c1da0ed507d71646bd6750acf065c5a`. The tests read but do not persist them; they contain no device data. Their generator is `scripts/generate-phase109-fixtures.py` and uses the common dialect from [PyMAVLink](https://github.com/ArduPilot/pymavlink), version 2.4.49. The Python 3.10 Windows wheel `pymavlink-2.4.49-cp310-cp310-win_amd64.whl` was downloaded into a temporary directory, checked against [PyPI version metadata](https://pypi.org/pypi/pymavlink/2.4.49/json), SHA-256 `dcf50ea5da306025dd5ddd45ed603e5f8a06c292dd22a143c9ff4292627ca338`, and unpacked without global installation. The generator output matched both fixture copies. Reproduce with that verified wheel unpacked on `PYTHONPATH`, then `python scripts/generate-phase109-fixtures.py`; the script itself does not download packages.

## Verification

- `./gradlew.bat -I <temp>/mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 476 JVM tests, zero failures/errors/skips; production debug assembly up-to-date; Android test APK built. Lint passed; lint report task was up-to-date.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicMavlinkPathUiTest#independentPymavlinkV1AndV2FramesReachClassicPanel,com.mamadrones.gcs.ClassicMavlinkPathUiTest#completeClassicReceivePathRejectsOtherSourcesExpiresAndReconnects'`: 2 passed on Pixel_3a_API_34_extension_level_7_x86_64; Android health check passed. Evidence: `app/build/acceptance-20261029-135952-91a6e948/instrumentation.txt` and `android-health.txt`; the v1/v2 captured panels were visually reviewed and show fresh heartbeat and RC evidence.

## Limits and recovery

These are synthetic, unsigned fixtures for three message types, not exhaustive protocol conformance or physical hardware verification. They do not establish the real Cube-to-phone path, sensor accuracy, source authentication or permission for rover movement. Hardware-team investigation of missing Cube heartbeat remains open. For rollback, review the clean starting revision and revert only Phase 109 test/fixture/document changes after authorization; the baseline is not asserted to be a verified release. Human review should check the generator provenance, assertions, duplicate fixture consistency and scope before committing.
