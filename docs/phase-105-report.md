# Phase 105 — Classic Rover HUD receipt freshness

2026-10-29. User approved implementation and offline emulator testing. Baseline `eeca1cb273935fdbb51823f26cdb9cba65c5e906`, clean; model/version unavailable. See D-105-01.

Goal: use current-session elapsed Rover HUD sample and accepted selected-source VFR_HUD evidence in the Classic connection check, keeping other measurement policies unchanged. Receipt does not verify speed, movement or safety.

## Behavior and boundaries

`MavlinkSession` selects the autopilot and records elapsed receipt and accepted message observations. The router passes the existing elapsed timestamp to `VehicleRepositoryImpl`, which keeps one volatile Rover HUD state with calendar and elapsed receipt times; a new session clears it. The Classic assessment lists Rover HUD only when the sample and accepted selected-source VFR_HUD evidence are current after the Classic socket opened. The assessment is a bounded in-memory list of measurement labels; no persistence was added. Other displays and reports retain calendar timestamps; Classic battery and system-status checks retain calendar freshness.

The stream is untrusted: receipt does not authenticate a sender or establish speed, movement, heading accuracy or physical safety. Five seconds is a diagnostic, not a failsafe; hardware and deep-sleep behavior remain unverified. No commands, source filtering, persistence, dependencies, permissions or external destinations changed.

## Verification

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 467 JVM tests passed with no failures/errors/skips; both debug APKs built; lint passed (30 tasks executed, 67 up-to-date; lint report task up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicConnectionCheckUiTest#roverHudReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#gpsReceiptUsesElapsedTimeAndExpiresEvenWithoutFix,com.mamadrones.gcs.ClassicConnectionCheckUiTest#attitudeReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#stagesAndGuidanceRemainReadableInBothThemes,com.mamadrones.gcs.ClassicConnectionPlainLanguageUiTest'`: 6/6 passed on Pixel_3a_API_34_extension_level_7_x86_64, Android health gate passed. Evidence `app/build/acceptance-20261029-124050-e6252dc9/`; stale HUD screenshot visually reviewed. Synthetic controlled-clock checks do not establish hardware behavior.
- Review guide: trace selected-source HUD elapsed receipt through router and repository, then check the sample/accepted-message predicates and tests. Changed files were reviewed for secrets; no dedicated scanner was available, so this is not a repository-wide scan. `git diff --check` passed. Baseline `eeca1cb273935fdbb51823f26cdb9cba65c5e906` is a recovery reference, not a verified release; authorize and review any scoped inverse before applying it. Commit/push require separate authorization.
