# Phase 102 — Classic position receipt freshness

2026-10-29. User approved implementation and offline emulator testing. Baseline `6be569b64258a6fe82d6e34caad55ef266a72134`, clean; model/version unavailable. See D-102-01.

Goal: use current-session elapsed selected-source position sample and accepted GLOBAL_POSITION_INT evidence in the Classic connection check, while preserving other measurement policies. This is receipt evidence, not a GPS fix, accuracy or a failsafe.

## Behavior and boundaries

`MavlinkSession` selects a source and records elapsed receive time and accepted message observations; the repository retains the selected-source position sample. `ClassicConnectionCheck` requires current elapsed timestamps for both the sample and accepted GLOBAL_POSITION_INT after this Classic socket opened. The existing assessment remains an in-memory bounded list of measurement labels; no data or locations are persisted by this change. Its position receipt label does not establish valid coordinates, GPS fix, accuracy or trusted identity. Other measurement labels retain calendar-time freshness; telemetry reports retain their existing calendar timestamps.

Inputs are untrusted, the existing five-second display window is not a failsafe, and hardware and deep-sleep behavior are not independently verified. There are no new commands, dependencies, permissions, destinations or source-selection changes. Missing evidence fails closed.

## Verification

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: 458 JVM tests passed with 0 failures/errors/skips; both debug APKs built; lint passed (27 tasks executed, 70 up-to-date; lint report task up-to-date).
- First targeted emulator run: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicConnectionCheckUiTest,com.mamadrones.gcs.ClassicConnectionPlainLanguageUiTest'`: 8/9 passed, including the new position scenario. The existing `rcReceiptExpiresIndependentlyAndRecovers` timed out waiting for initial FRESH RC state; root cause not established. Android health gate found no crash/ANR. Evidence `app/build/acceptance-20261029-115500-d0a5f4e6/`; position stale screenshot reviewed. The exact unmodified RC test passed 1/1 after lint, Android healthy: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.ClassicConnectionCheckUiTest#rcReceiptExpiresIndependentlyAndRecovers'`; evidence `app/build/acceptance-20261029-120018-897fd075/`. The 8/9 first-run result remains a failure; its cause is not established.
- Review guide: check the elapsed position sample plus accepted-observation predicates, unchanged other measurement policies and UI wording. No hardware action or new external behavior. Changed files were reviewed for secrets, but no dedicated secret scanner was available; this is not a repository-wide scan. Baseline `6be569b64258a6fe82d6e34caad55ef266a72134` is a recovery reference, not a verified production release; review and authorize any scoped inverse before applying it. Commit/push require separate authorization.
