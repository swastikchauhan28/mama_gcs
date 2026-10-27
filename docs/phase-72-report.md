# Phase 72 — Portrait waypoint keyboard check

Date: 2026-10-27

Baseline: `9bb331e73e321f7d53c9cd3c017d704f33d7389b` plus uncommitted Phase 71

Scope: test-only portrait Add/Edit variants, evidence, and handover. Hardware remains on hold.

## Goal and design

Confirm the existing waypoint Add/Edit keyboard path at 320 dp and 2× text in portrait. Synthetic coordinates and a synthetic route feed the screen; the test only records in-memory `MissionPlanAction` callbacks. The test fixture starts in landscape by its debug manifest. Each new variant requests portrait, waits for portrait configuration, asserts each full-display capture is taller than wide, then restores the previous orientation. The two Phase 71 landscape cases remain in the class. No production app behavior, permission, dependency, network, storage, telemetry, or vehicle path changed.

The first run of the unchanged two-test class passed `OK (2 tests)` with Android healthy, but its new full-display captures were 2220×1080 landscape. Evidence: `app/build/acceptance-20261027-101323-13d13c90/` and the initially pulled `app/build/phase72-portrait-*.png`. This was **not** portrait acceptance. Inspection found `app/src/debug/AndroidManifest.xml` fixes `FixtureTestActivity` to landscape, so the AVD's portrait idle orientation was insufficient. D-072-01 records the correction before the test edit.

## Verification

- Built the test-only change with `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 1m 22s**, 60 tasks, 7 executed and 53 up-to-date. The current app APK was already installed; the rebuilt test APK was installed with `adb -s emulator-5554 install -r`, without clearing data.
- Ran `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.WaypointKeyboardUiTest'`: **PASS, `OK (4 tests)`**. Evidence: `app/build/acceptance-20261027-101859-9f970295/instrumentation.txt` and `android-health.txt`. No new crash/ANR or error dialog; post-run boot complete. `$adbPath` was `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Pulled and visually inspected `app/build/phase72-add-ime.png`, `phase72-add-after-done.png`, `phase72-edit-ime.png`, `phase72-edit-after-done.png`. Each measured **1080×2220**. In both keyboard-open views, the coordinate fields and Add/Apply/Cancel footer are visible above the IME. Done hides the keyboard; the confirmation footer remains visible. The tests also assert exact local Add and Edit callbacks.
- The emulator process command line requested `-gpu swiftshader_indirect`, but the acceptance runner recorded `GPU launch option: unavailable`. Renderer identification is therefore not independently established by this gate. The build warned about the existing test-only deprecated `SOFT_INPUT_ADJUST_RESIZE` constant. JVM and lint were not repeated because Phase 72 changes only Android instrumentation tests and documents; their Phase 71 results remain historical, not fresh Phase 72 coverage.

## Review and status

Review the two new test methods and the orientation helper in `WaypointKeyboardUiTest.kt`; check that the portrait screenshot dimension assertion cannot silently accept a landscape capture. No assertion was weakened. The baseline revision is a source recovery point, not a production-qualified release. The Phase 72 test/documentation edit can be reversed without data migration. The owner has not reviewed this diff. Phase 71 and 72 changes remain uncommitted and unpushed; unrelated `desktop.ini` is preserved. Other phones, IMEs, locales, the whole Android suite and physical hardware remain unverified.
