# Phase 216 — Exact GPX route exchange and outline notice

## Goal and baseline

The user approved a software-only Phase 216 on 2026-11-21 to check precise GPX waypoint exchange and the visible notice that GPX omits the local field outline. Baseline `c3b9acf` on `main`. The earlier demo media under `output/` were already untracked and are outside this phase. RC work remains paused. Model/version unavailable. See D-216-01.

## Change and data path

The existing `MissionExchangeTest.gpxRoundTripsOrderedRoutePointsAndEscapedName` now compares high-precision waypoint coordinates exactly through `MissionDraftGpxCodec` encode/decode. It still verifies the escaped route name and absence of a field outline after GPX import.

The existing `RouteDocumentPickerTest.exportBothFormatsAndImportThroughSystemPickerWithCancellation` now uses precise route waypoints and checks that the mission file menu displays “GPX carries route waypoints only. Use GeoJSON to preserve the local outline.” Its existing exact coordinate checks cover the decoded exported file and the reimported, explicitly saved route through Android DocumentsUI. The test restores the previous disposable-emulator draft and recovery state, and removes its uniquely named temporary files in `finally`.

`MissionDraft` stores at most 250 route waypoints. The external GPX file is untrusted input to the existing bounded, validating codec. The file menu and codec remain unchanged. This phase adds no production code, permission, dependency, schema, destination, persistent format or vehicle command. Exact local numeric preservation does not prove physical navigation accuracy.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 3m 49s`; 9 tasks executed, 88 up-to-date. Existing JVM XML reports 533 tests in 84 suites with zero failures; the JVM test task reused its earlier result. Lint XML reports zero errors and 27 warnings.
- The disposable API 31 emulator logged two startup ANR events before the app test. Evidence: ignored `app/build/phase216-emulator-boot/`. A clean no-wipe restart and 30-second quiet health preflight then passed. These startup failures remain part of the record.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.MissionExchangeTest#gpxRoundTripsOrderedRoutePointsAndEscapedName'` — fresh `OK (1 test)`, healthy Android. Evidence: ignored `app/build/acceptance-20261121-115701-bdd8044e/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation'` — fresh `OK (1 test)` in 63.124 seconds, healthy Android. This includes the notice, real system picker, export, import and exact route-value checks. Evidence: ignored `app/build/acceptance-20261121-115728-015d6a4b/`.
- No UI appearance changed, so this phase has no new visual-layout claim. The focused UI test checks the notice is displayed.
- Reviewed the tracked diff and changed-file list: two Android tests and project records only. The prior untracked demo media were left alone. `git diff --check` passed with Git line-ending conversion warnings only. A scoped scan of the changed test files and phase record found no obvious credential values; matching words in the historical handover/decisions were documentation text. The disposable emulator was stopped after testing.

## Limits and recovery

These checks cover synthetic coordinates and one emulator's Downloads provider. They do not prove handling of every third-party GPX file, field measurement precision, mission upload or rover motion. If the emulator stops before the test's `finally`, inspect its disposable draft and uniquely named temporary files before reuse. Reverting this test-only phase would not change production behavior. Owner diff review and the broader release gate remain open. No commit or push is authorized by the Phase 216 approval alone.
