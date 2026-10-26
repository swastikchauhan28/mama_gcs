# Phase 63 — enlarged mobile category tabs

Date: 2026-10-26. Baseline `6d0665d37e37cf157d5719d33b4781e2d55fcd01`, with prior uncommitted Phases 60–62 and unrelated untracked `desktop.ini` preserved.

## Goal and result

Phase 58 checks automatic selected-tab reveal at normal text and 360 dp. Phase 63 extends the existing Compose geometry test to 320 dp and 2× text, selecting Bluetooth / HC-05, Accounts & audit, and RC & PWM in turn. For each selection it verifies the full chip remains inside the visible category strip, its text is not ellipsized, and the exit and safety shortcuts remain visible. All assertions passed on the existing layout; no production layout change was needed. See [D-063-01](DECISIONS.md#d-063-01-check-selected-mobile-tabs-at-enlarged-text).

The test holds only the current route in transient Compose state and injects font scale/width for its disposable UI fixture. It opens no transport, writes no app data, and issues no rover command.

## Verification

- `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL in 55s` (6 executed, 54 up-to-date). This compiled the changed Android test source; no production source was changed.
- On the disposable SwiftShader Pixel 3a API 34 emulator, `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#enlargedMobileCategoryFitsAndSafetyStaysReachable'`: `OK (1 test)` in 9.26 s. Android boot completed, zero new crash/ANR evidence and no error dialog. Evidence: `app/build/acceptance-20261026-141717-44fb1f46/` (`instrumentation.txt`, `android-health.txt`).
- The existing `selectedMobileWorkspaceTabIsRevealedAfterNavigation` and full software suite were not rerun in this phase. Phase 62's 134-method result remains the latest broader suite, before this test-only edit.

## Limits and handoff

One emulator width and three English tab labels were checked. This does not establish behavior on every device, locale or larger accessibility scale, and it is not physical phone, radio or Cube acceptance. No dependency, permission, schema, secret, external destination, build configuration, app behavior or Git history changed. Review the added assertions in `VisualLayoutRegressionTest.kt`; Phase 63 is uncommitted and unpushed. The only rollback is to remove this test and its records if the focused coverage is no longer wanted.
