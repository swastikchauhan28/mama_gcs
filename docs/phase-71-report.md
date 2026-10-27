# Phase 71 — Waypoint entry with Android keyboard

Date: 2026-10-26
Baseline: `9bb331e73e321f7d53c9cd3c017d704f33d7389b`
Scope: Add/Edit waypoint dialog and a synthetic keyboard UI fixture. Hardware remains on hold.

## Goal and change

Phase 65 showed waypoint coordinate fields and actions at 320 dp / 2× text, but never opened the Android keyboard. Phase 70 established that an IME can cover actions even when Compose reports them displayed. The new test opens Add and Edit, enters valid coordinates with a real emulator IME, presses the longitude field's Done action, then confirms the existing local Add/Edit callback.

Both first-run tests failed: longitude exposed `ImeAction.Default`, so the explicit Done path did not exist. The dialog now exposes Done on longitude and hides the keyboard without changing either coordinate or confirming a waypoint. The first fix still failed because the keyboard controller was obtained outside `AlertDialog`, from the parent window. Moving that lookup inside the dialog content resolved the IME dismissal. The failed runs are retained below; no assertion was weakened.

## Data and trust boundary

`WaypointDialog` owns two `rememberSaveable` strings, each capped at 32 characters. It trims/parses finite decimal degrees and keeps latitude within -90…90 and longitude within -180…180. Done calls only the dialog-local `LocalSoftwareKeyboardController.hide()`; Add/Apply still requires both valid values and sends the existing `MissionPlanAction` callback. No route repository, network, telemetry, permission, dependency, schema, file or vehicle command path changed. The tests use a synthetic draft/waypoint and an in-memory callback list.

The dialog footer is covered while the IME is open in this enlarged landscape fixture. Done makes Add/Apply and Cancel visible. Other keyboards, locales and physical-phone geometries remain unverified. The fixture explicitly sets adjust-resize; the production `MainActivity` already declares adjust-resize in its manifest.

## Verification and retained failures

- Initial app/test build: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL in 1m 1s**, 79 tasks: 7 executed, 72 up-to-date. Both current APKs were installed on disposable `emulator-5554` without clearing data.
- Initial focused run: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.WaypointKeyboardUiTest'` — **2 failures**, both at `performImeAction()` with `ImeAction.Default`. Evidence: `app/build/acceptance-20261026-174902-fef8aefe/`; keyboard-open images retained as `app/build/phase71-add-ime-before-fix.png` and `phase71-edit-ime-before-fix.png`.
- First Done build: same app/test command — **BUILD SUCCESSFUL in 1m 10s**, 79 tasks: 14 executed, 65 up-to-date. The same two tests then **failed** waiting ten seconds for IME dismissal. Evidence: `app/build/acceptance-20261026-175233-af3e300c/`; `app/build/phase71-add-ime-after-first-fix.png` shows entered values and a still-open keyboard. The wrong-window keyboard-controller scope was corrected.
- Final app/test build: same command — **BUILD SUCCESSFUL in 1m 19s**, 79 tasks: 9 executed, 70 up-to-date. The unchanged focused class then **passed, `OK (2 tests)`**, with zero new crashes/ANRs and no error dialog. Evidence: `app/build/acceptance-20261026-175604-0475fff8/`. Both tests verify IME visible before Done, hidden after Done, and the exact in-memory Add/Edit callbacks.
- Related Android run: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#waypointDialogKeepsFieldsAndActionsReachableAtLargeText,com.mamadrones.gcs.MissionWorkflowTest'` — **PASS, `OK (13 tests)`**, Android healthy. Evidence: `app/build/acceptance-20261026-175734-b0d07239/`.
- JVM/lint: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL in 3m 53s**, 45 tasks: 11 executed, 34 up-to-date. XML: 64 suites, **389 tests, 0 failures/errors/skips**. Lint: **0 errors, 28 warnings**, unchanged from the recorded baseline. Reports under `%TEMP%\mama-gcs-phase58-app-build\test-results\testDebugUnitTest` and `reports\lint-results-debug.xml`.
- Full-display final screenshots visually inspected: `app/build/phase71-add-ime.png`, `phase71-add-after-done.png`, `phase71-edit-ime.png`, and `phase71-edit-after-done.png`. The keyboard covers the footer before Done; afterward both values and Add/Apply/Cancel are visible. The fixture uses a 320 dp landscape window, 2× text and the emulator's SwiftShader renderer.

`$adbPath` was `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. The test APK build emitted a deprecation warning for the fixture's `SOFT_INPUT_ADJUST_RESIZE` constant; this does not change production configuration. No whole-app Android suite, real phone or physical-hardware acceptance was run in this phase.

## Review, recovery and status

Review the `WaypointDialog` text content in `MissionScreen.kt`, `WaypointKeyboardUiTest.kt`, and D-071-01. `git diff --check` found no whitespace error. The baseline commit is a source recovery point, not a production-qualified release; reverse the scoped Done/controller/test/documentation diff if necessary. No data migration applies. The owner has not reviewed this diff. Phase 71 remains uncommitted and unpushed; preserve unrelated `desktop.ini`.
