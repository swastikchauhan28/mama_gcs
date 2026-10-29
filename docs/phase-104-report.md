# Phase 104 — Classic GPS receipt freshness

2026-10-29. User approved implementation and offline emulator testing. Baseline `d6071123c7b332d3b7c34d5514730a538a3dbaee`, clean; model/version unavailable. See D-104-01.

Goal: use current-session elapsed GPS sample and accepted selected-source GPS_RAW_INT evidence in the Classic connection check, keeping other measurement policies unchanged. Receipt does not imply a GPS fix or accuracy.

## Behavior and boundaries

`MavlinkSession` selects the autopilot and records elapsed receive time and accepted message observations. The router passes the existing elapsed timestamp to `VehicleRepositoryImpl`, which keeps one volatile GPS state with calendar and elapsed receipt times; a new session clears it. The Classic assessment lists GPS only when the sample and accepted selected-source GPS_RAW_INT evidence are current after the Classic socket opened. The assessment is a bounded in-memory list of measurement labels; this change adds no persistence. Other displays and reports retain calendar timestamps; Classic battery, rover HUD and system-status checks retain calendar freshness.

The stream is untrusted: receipt does not authenticate a sender or establish GPS fix, accuracy, physical position or safety. A fresh NO_FIX message is still receipt evidence. Five seconds is a diagnostic, not a failsafe; hardware and deep-sleep behavior remain unverified. No commands, source filtering, persistence, dependencies, permissions or external destinations changed.

## Verification

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 464 JVM tests passed with no failures/errors/skips; both debug APKs built; lint passed (30 tasks executed, 67 up-to-date; lint report task up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicConnectionCheckUiTest#gpsReceiptUsesElapsedTimeAndExpiresEvenWithoutFix,com.mamadrones.gcs.ClassicConnectionCheckUiTest#positionReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#attitudeReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#stagesAndGuidanceRemainReadableInBothThemes,com.mamadrones.gcs.ClassicConnectionPlainLanguageUiTest'`: 6/6 passed on Pixel_3a_API_34_extension_level_7_x86_64, Android health gate passed. Evidence `app/build/acceptance-20261029-122811-fc433752/`; stale GPS screenshot visually reviewed. Synthetic controlled-clock checks do not establish physical hardware behavior.
- Review guide: trace the selected-source GPS elapsed timestamp through router and repository, then check sample/accepted-observation predicates and NO_FIX tests. Changed files were reviewed for secrets; no dedicated scanner was available, so this is not a repository-wide scan. `git diff --check` passed. Baseline `d6071123c7b332d3b7c34d5514730a538a3dbaee` is a recovery reference, not a verified release; authorize and review any scoped inverse before applying it. Commit/push require separate authorization.
