# Phase 106 — Classic system-status receipt freshness

2026-10-29. User approved implementation and offline emulator testing. Baseline `dd63885e2022d3e36268d1c4defb157a9fd1395a`, clean; model/version unavailable. See D-106-01.

Goal: use current-session elapsed system-status sample and accepted selected-source SYS_STATUS evidence in the Classic connection check, keeping the separate battery measurement policy unchanged. Receipt does not verify sensor health or safety.

## Behavior and boundaries

`MavlinkSession` selects the autopilot and records elapsed receive time and accepted observations. The router already passes this timestamp to `VehicleRepositoryImpl`; the repository now retains it in volatile system-status state alongside the calendar timestamp. A new session clears it. The Classic assessment lists System status only when its sample and accepted selected-source SYS_STATUS evidence are current after the Classic socket opened. The assessment is a bounded in-memory list of labels; no persistence was added. Existing reports retain calendar timestamps, and the separate Classic battery check remains calendar-based.

The stream is untrusted: receipt does not authenticate a sender or establish sensor health or physical safety. Five seconds is a diagnostic, not a failsafe; hardware and deep-sleep behavior remain unverified. No commands, source filtering, persistence, dependencies, permissions or external destinations changed.

## Verification

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 470 JVM tests passed with no failures/errors/skips; both debug APKs built; lint passed (30 tasks executed, 67 up-to-date; lint report task up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicConnectionCheckUiTest#systemStatusReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#roverHudReceiptUsesElapsedTimeAndExpiresIndependently,com.mamadrones.gcs.ClassicConnectionCheckUiTest#gpsReceiptUsesElapsedTimeAndExpiresEvenWithoutFix,com.mamadrones.gcs.ClassicConnectionCheckUiTest#stagesAndGuidanceRemainReadableInBothThemes,com.mamadrones.gcs.ClassicConnectionPlainLanguageUiTest'`: 6/6 passed on Pixel_3a_API_34_extension_level_7_x86_64, Android health gate passed. Evidence `app/build/acceptance-20261029-125535-3e01edd7/`; stale system-status screenshot visually reviewed. Synthetic controlled-clock checks do not establish hardware behavior.
- Review guide: trace selected-source SYS_STATUS elapsed receipt into system-status state; check sample/accepted-message predicates and the unchanged separate battery timing. Changed files were reviewed for secrets; no dedicated scanner was available, so this is not a repository-wide scan. `git diff --check` passed. Baseline `dd63885e2022d3e36268d1c4defb157a9fd1395a` is a recovery reference, not a verified release; authorize and review any scoped inverse before applying it. Commit/push require separate authorization.
