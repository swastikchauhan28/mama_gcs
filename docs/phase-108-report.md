# Phase 108 — Synthetic Classic MAVLink path integration

2026-10-29. User approved implementation and offline emulator testing. Baseline `44c154f6bfbf525e0d39b8d4f39e68869e32ec6e`, clean; model/version unavailable. See D-108-01.

Goal: test the Classic decoder-to-panel path using an in-memory transport and synthetic frames, including selected-source filtering, freshness, recovery and reconnect. No physical hardware or commands.

## Test path and limits

The emulator test writes checksum-valid and deliberately corrupt synthetic frames into a test-only in-memory Classic `VehicleTransport`. Production `MavlinkParser`, `MavlinkSession`, selected-source routing, `VehicleRepositoryImpl`, `ClassicConnectionCheck` and `ClassicConnectionCheckPanel` process them. A bounded test fixture checks a fragmented heartbeat, CRC rejection, an unrelated source, all seven listed measurement groups, elapsed expiry while heartbeat remains current, partial recovery and a clean reconnect. The transport fails and records any attempted send. A separate JVM session integration test checks the same receipt path with a virtual clock; an existing real-clock screen test checks expiry and disconnect. No production code, Bluetooth, network, persistence, parameter writes, actuator commands or dependency changes were made.

Test frames are generated in memory using the production checksum function; a matching checksum is not authentication, and this test does not independently establish MAVLink specification conformance. Controlled fixture clocks do not establish Android deep-sleep behavior or physical radio/Cube operation, sensor validity, GPS accuracy, battery health or safe movement. The five-second window is an app diagnostic, not a failsafe.

## Verification

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: fresh JVM run passed 473 tests, zero failures/errors/skips; debug app build was up to date and Android test APK built; lint passed (17 tasks executed, 80 up-to-date; lint report task up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicMavlinkPathUiTest,com.mamadrones.gcs.ClassicConnectionCheckUiTest#screenExpiresEvidenceWithoutIncomingUpdatesAndDisconnectClearsIt'`: 2/2 passed on Pixel_3a_API_34_extension_level_7_x86_64 with Android health gate passed; evidence `app/build/acceptance-20261029-132429-fedc3a36/`. Synthetic live and stale screenshots were visually reviewed. The first screenshot pull attempted a `.png` extension not present on the generated files; both were then retrieved successfully using their actual filenames.
- Review guide: inspect in-memory transport send guard, frame generation, selected-source rejection, receipt labels, controlled-time expiry, reconnect and screenshot evidence. Changed files reviewed for secrets; no dedicated scanner was available, so this is not a repository-wide secret scan. Baseline `44c154f6bfbf525e0d39b8d4f39e68869e32ec6e` is a recovery reference, not a verified release; authorize and review any scoped inverse before applying it. Commit/push require separate authorization.
