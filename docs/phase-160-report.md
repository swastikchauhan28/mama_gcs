# Phase 160 — mission-planning regression (in progress)

Date: 2026-11-04. User approved the end-to-end emulator regression. Baseline clean `eabbcdd9cc846200146723c9c4c0713157c37529`; model/version unavailable. See D-160-01.

## Scope

Extended the existing disposable-emulator real-app DocumentsUI journey to compare options A (2 m / 10%) and B (1.25 m / 25%) after generating a coverage preview and before the existing save, export and reimport checks. Existing tests assert the saved/exported/imported route coordinates and outline. The test temporarily replaces only the disposable emulator draft and uses a uniquely named temporary file; interruption can affect cleanup. No production code, hardware command or user phone data was changed.

## Verification so far

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 88 tasks, 9 executed and 79 up-to-date. The updated test compiled; no fresh JVM suite or full regression ran.
- Three no-wipe disposable-emulator starts failed the health gate before the mission test: first preflight found one ANR, second found a System UI ANR dialog, third found one ANR. Evidence under ignored `app/build/phase160/` includes `preflight.txt`, `preflight-retry.txt`, `retry-window.txt`, `preflight-third.txt`, and saved exit/window diagnostics. No dialog was dismissed. Emulator stopped.
- The revised mission test has not run and its comparison assertions remain unverified. No screenshots or end-to-end results were captured in Phase 160. Emulator startup failures are not evidence of a mission-planning defect.

## Next

On a healthy disposable emulator, install the built APKs and run `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute'`. Review the synthetic screenshot, preserve any failures, and record actual results. This is software-only and does not verify physical coverage or rover mission execution.


## 2026-11-04 follow-up — completed emulator journey

After the user closed unused applications, the existing no-wipe disposable emulator passed a 30-second health preflight (`app/build/phase160/preflight-user-retry.txt`, ignored). Installed the previously built debug and instrumentation APKs with data-preserving updates. The documented `RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute` returned `OK (1 test)` in 92.798 seconds with Android healthy. The runner reported its GPU option was unavailable. Evidence: `app/build/phase160/user-retry-test.txt` and `app/build/acceptance-20261104-103519-be558297/` (ignored).

The assertions covered two synthetic waypoints, a three-point outline, preview generation, both compared tool-setting results, save, GeoJSON export via the Android picker, import, and retained saved/exported/imported coordinates. The test cleanup restores the previous emulator draft and deletes the unique test file when it completes. Reviewed `app/build/phase160/user-retry-coverage.png`: it shows the synthetic field diagram and modeled pass results. The previous ANRs remain undiagnosed; a healthy run does not establish emulator stability. This targeted run is not a full regression or physical coverage/mission execution verification.
