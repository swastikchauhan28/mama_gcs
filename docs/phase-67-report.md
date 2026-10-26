# Phase 67 — Enlarged-text local route library

Date: 2026-10-26
Baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`
Scope: instrumentation test and working records only; no production code changed.

## Goal

Check the local route library dialog at 320 dp and 2× text with three synthetic entries, including a long route name. Verify dialog bounds, name readability, list scrolling, Close, and the Open/Delete confirmation actions. The fixture uses a local `MissionPlanUiState`; callbacks are collected in memory. It does not use the mission repository, persist a route, open a document picker, connect telemetry or send vehicle commands.

## Finding

The long route name wraps without ellipsis. The dialog and its Close action stay in the window. The route list scrolls vertically to later entries; Open and Delete for the first and last entries can be reached and confirmed. Their actions reach only the in-memory test callback. Existing scrolling was sufficient, so no production layout change was warranted.

## Verification

- Build: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL**, final run `60 actionable tasks: 6 executed, 54 up-to-date`.
- Focused acceptance: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#missionLibraryDialogKeepsLongRoutesAndActionsReachableAtLargeText'` — **PASS, `OK (1 test)`**, Android health gate passed, SwiftShader AVD. The init script redirects Gradle output to `%TEMP%\mama-gcs-phase58-app-build`; the test APK was installed from there.
- The runner's `-Adb` argument pointed to the Android SDK platform-tools `adb.exe` under `%LOCALAPPDATA%`.
- Captured screenshot and visually inspected: `app/build/phase67-route-library.png` (synthetic dialog content only).
- Final instrumentation and Android-health evidence, including the added one-line action-label assertions: `app/build/acceptance-20261026-154848-bc694fe8/`. The earlier `154523` run passed before those final assertions and is retained in build evidence.

## Failed attempts retained

- `acceptance-20261026-152828-18fec0a6` and `acceptance-20261026-153128-3545c2bd`: launched an old APK from `app/build`, producing zero tests; the latter class-level run showed only two old tests. These are not passes.
- `acceptance-20261026-153610-1f1ced3f`: the old APK had been moved aside, but Gradle was still directed to the separate temp build path; no test APK was created at the repository path.
- `acceptance-20261026-153832-7eb483ec`: the fresh APK discovered the test, but screenshot capture targeted two Compose roots. Changed capture to the dialog node.
- `acceptance-20261026-154301-ce3c18cc`: expanded file actions required scrolling before the Library button could be tapped. Updated the test to scroll it into view.
- Final fresh-APK run passed. No failed attempt is counted toward acceptance.

## Limits and next step

Evidence covers one synthetic English fixture at one emulator width and text scale. It proves dialog geometry and local UI callback behavior, not persistence, other locales/devices or hardware operation. No full Android inventory, lint or physical-hardware check was run. Review `missionLibraryDialogKeepsLongRoutesAndActionsReachableAtLargeText` in `app/src/androidTest/java/com/mamadrones/gcs/VisualLayoutRegressionTest.kt`. Phase 67 is uncommitted; commit/push was not requested.
