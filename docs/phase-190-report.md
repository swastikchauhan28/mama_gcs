# Phase 190 — Warning-history visual acceptance

Date: 2026-11-17. Baseline: `43fe9a9`, clean `main` worktree. Scope: disposable-emulator verification only; no app or hardware changes.

## Goal

Run the Phase 189 synthetic `AutopilotWarningUiTest` and inspect its Diagnostics warning-history screenshot, including filter and session labels. The existing app and Android-test APKs had built successfully in Phase 189; its 501 JVM tests passed and lint reported 0 errors / 20 warnings. Those are prior results, not fresh Phase 190 runs.

## Attempts and result

1. Started the verified disposable Pixel 3a API 34 AVD without wiping its data, using `-gpu swiftshader_indirect` as documented in `TESTING.md`. Android booted and showed no error dialog initially. The required `Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30` failed because one new `am_anr` event named `com.google.android.gms.persistent` during the observation. No APK installation or instrumentation followed.
2. Preserved that finding, stopped the disposable emulator and started a new no-wipe attempt with the same graphics setting. After boot settling, the health preflight failed immediately: the focused window was `Application Not Responding: com.android.systemui`. The event log also contained System UI and Google Play services ANRs. Host free physical memory was about 890 MB at inspection. The emulator was stopped without dismissing the dialog or clearing data.

At this point, testing was blocked. The memory pressure was a plausible contributor, not a proven cause. No Cube, vehicle command, operator phone or saved route was involved.

## 2026-11-17 follow-up — visual acceptance completed

After the user closed unused Chrome windows and OneDrive, a fresh no-wipe `-gpu swiftshader_indirect` emulator start passed the 30-second health preflight. The already-built Phase 189 app and Android-test APKs installed on the verified disposable AVD. `Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.AutopilotWarningUiTest` returned `OK (4 tests)` in 18.081 seconds. The runner recorded 0 new crash lines, 0 new ANR events, no error dialog and a healthy post-run Android state in `app/build/acceptance-20261117-103800-a13f68ca/`. Its GPU detector reported unavailable because it did not match this process launch form; direct process inspection confirmed `-gpu swiftshader_indirect`.

The captured image at `app/build/reports/phase-190-warning-history.png` was inspected: the selected Warnings only chip, `CURRENT SESSION · 1 / 2 shown`, full compass warning and reported-severity caveat are visible without clipping or an overlay. The same UI test asserts that the routine text is hidden by the filter and that the retained warning receives a historical label after disconnect. This is targeted synthetic UI evidence, not a full app regression or a live Cube check.

## Next safe check

Phase 189's targeted visual check is complete. Keep the failed preflights visible as historical evidence. Live Cube `STATUSTEXT` receipt and fault accuracy remain unverified while hardware is unavailable. No app code changed in Phase 190; this follow-up records acceptance only. The user separately requested commit and push of this record.
