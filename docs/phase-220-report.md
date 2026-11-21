# Phase 220 — Oversized route file rejection

## Goal and baseline

The user approved a software-only Phase 220 on 2026-11-21 to test the shared GeoJSON/GPX import size limit through Android DocumentsUI, with no change to saved or unsaved route data after rejection. Tracked baseline `4931846` on `main`; Phase 219 was already uncommitted. The requested debug APK and prior demo media under `output/` were separate untracked deliverables. RC work remains paused. Model/version unavailable. See D-220-01.

## Change and data path

The new `RouteDocumentPickerTest.oversizedRouteFilesPreserveSavedAndWorkingDraft` creates a valid-shaped GeoJSON file and a valid-shaped GPX file, each exactly 256,001 ASCII characters. The padding is inside a JSON string and an XML comment, so the test targets the screen reader's 256,000-character limit rather than a syntax error. Against a saved one-point route and a different unsaved latitude edit, it imports each file through the real system picker and verifies the visible size-limit error, no replacement preview, unchanged saved route and retained unsaved edit. It restores the emulator's prior draft and recovery copy and deletes only its uniquely named document URIs in `finally`.

External documents cross from Android's provider into the screen's 4,096-character-chunk bounded reader. Reading stops once the cumulative character count exceeds 256,000, before `MissionDraftRouteCodec` receives content. `MissionPlanViewModel` then displays the read failure without replacing the pending or saved route. This phase adds no production code, dependency, permission, schema, external destination or vehicle command.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 4m 46s`; 10 tasks executed, 87 up-to-date. Existing JVM XML reports 533 tests in 84 suites, zero failures/errors/skips; the JVM task reused its result. Lint XML reports zero errors and 27 warnings. This build included the still-uncommitted Phase 219 test.
- The first disposable API 31 launch recorded Google Play services and System UI ANRs, including an active System UI dialog. A no-wipe restart again reached an active System UI dialog. The alternate disposable API 34 launch also recorded System UI and service ANRs and exited during its attempted no-wipe restart. These preflight failures are retained under ignored `app/build/phase220-emulator-boot/`; none is counted as a test pass.
- A later clean launch of `MamaGcs_Phase209_API31` passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30`. The current app and test APKs were installed with `adb install -r`, without clearing emulator data.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#oversizedRouteFilesPreserveSavedAndWorkingDraft'` — fresh `OK (1 test)` in 30.754 seconds. Post-run Android health reports zero new crashes/ANRs and no error dialog. Evidence: ignored `app/build/acceptance-20261121-132050-727d4a85/`.
- No app UI layout changed; the focused test asserts that the error is displayed. Reviewed the scoped Phase 220 test and project records alongside the preserved Phase 219 diff. `git diff --check` passed with Git line-ending warnings for the decisions and handover files. A scoped obvious-secret pattern scan of the test and report found no matches. No assertions, validation or safety checks were weakened.

## Limits and recovery

This covers two synthetic ASCII files on one emulator and does not prove every document provider, encoding edge case, physical route precision, mission upload or rover motion. If the test stops before `finally`, inspect its unique emulator files and local draft before reuse. Reverting this test-only phase would not alter production behavior. Owner diff review and the wider release gate remain open. Phase approval does not authorize commit or push.
