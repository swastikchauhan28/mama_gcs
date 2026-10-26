# UI Phases 60–70 verification

Date: 2026-10-26
Source baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01` plus the uncommitted Phase 60–70 changes.
Scope: the new desktop/mobile layout, mission editing, route library and search UI. No physical hardware or live service was used.

## Results

- `:app:testDebugUnitTest` executed: 64 suites, **389 tests passed**, zero failures, errors or skips. XML: `%TEMP%\mama-gcs-phase58-app-build\test-results\testDebugUnitTest`.
- `:app:lintDebug` completed with **0 errors and 28 warnings** (the previously recorded warning baseline). Report: `%TEMP%\mama-gcs-phase58-app-build\reports\lint-results-debug.html`.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` completed as **up-to-date** tasks, using the Phase 70 APKs already built from the same source. The combined Gradle run was `BUILD SUCCESSFUL in 3m 23s`, 97 tasks: 13 executed, 84 up-to-date.
- On disposable `emulator-5554`, the first Android batch passed **16/16** tests from `RoverWorkspaceUiTest`, `VisualLayoutRegressionTest` and `MissionLibrarySearchUiTest`. Evidence: `app/build/acceptance-20261026-171124-5a823d35/`.
- The second Android batch passed **27/27** tests from `MissionLibraryUiTest`, `MissionWorkflowTest` and `FoundationUiTest`. Evidence: `app/build/acceptance-20261026-171558-c03d0d49/`.
- Both Android health gates found zero new crashes or ANRs and no post-run error dialog. The 43 Android tests cover the new Phase 60–70 screens and related route persistence, import/export and foundation regressions; this is a targeted acceptance run, not the entire app test inventory.
- Fresh synthetic screenshots were visually inspected: `app/build/regression-phase65-waypoint-dialog.png`, `regression-phase66-waypoint-actions.png`, `regression-phase67-route-library.png`, and `regression-phase70-library-after-done.png`. The waypoint form, actions, route library and filtered result after Done were usable in the tested enlarged landscape fixture. The Phase 66 capture is a cropped detail, so it does not establish the entire screen layout.
- `git diff --check` found no whitespace errors. It emitted only Windows LF-to-CRLF notices.

## Exact commands

The SDK `adb.exe` was taken from `%LOCALAPPDATA%\Android\Sdk\platform-tools`, and the local Gradle init script redirected build output to `%TEMP%`.

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.RoverWorkspaceUiTest,com.mamadrones.gcs.VisualLayoutRegressionTest,com.mamadrones.gcs.MissionLibrarySearchUiTest'
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.MissionLibraryUiTest,com.mamadrones.gcs.MissionWorkflowTest,com.mamadrones.gcs.FoundationUiTest'
git diff --check
```

Expected outcome for these checks: Gradle succeeds; JVM and Android test counts have zero failures; lint has zero errors; Android health has zero new crash/ANR and no error dialog; diff check has no whitespace errors. These expected results were met.

## Limits and next step

The Phase 70 enlarged landscape fixture still requires the keyboard Done action before its filtered result card is reachable; keyboard-open occlusion is documented in the Phase 70 report. Other screen sizes, locales, IMEs and a physical phone were not checked here. The physical BLE, Cube, receiver and remote path remain untested and on hold by user request. Lint warnings remain. No production readiness or hardware acceptance is inferred.

Review the accumulated Phase 60–70 diff and these evidence files before delivery. At the time of this test run, Phases 60–70 were uncommitted and unpushed. Preserve unrelated `desktop.ini`.
