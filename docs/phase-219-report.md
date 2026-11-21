# Phase 219 — Maximum accepted GeoJSON route import

## Goal and baseline

The user approved a software-only Phase 219 on 2026-11-21 to import and explicitly save a GeoJSON route at the existing 250-waypoint and 64-outline-vertex limits on a disposable emulator. Baseline `4931846` on `main`; the earlier demo media and requested debug APK under `output/` were already untracked and remain separate. RC work remains paused. Model/version unavailable. See D-219-01.

## Change and data path

`RouteDocumentPickerTest.maximumGeoJsonRouteImportsAndSavesExactly` independently builds a GeoJSON FeatureCollection with 250 ordered Point features and one closed 64-vertex keep-in Polygon. The synthetic file is below the existing 256,000-character input bound. Through Android DocumentsUI, the test verifies a preview showing 250 waypoints, confirms the previous saved route remains unchanged before and after replacing the working draft, then explicitly saves. It compares every saved waypoint and outline coordinate exactly and in order against its generated expected lists. It restores the disposable emulator's prior draft and recovery copy and deletes only its uniquely named document URI in `finally`.

External file content crosses from the document provider into the screen's bounded reader, `MissionPlanViewModel.importDraft`, `MissionDraftRouteCodec`, `MissionDraftGeoJsonCodec` and device-local `MissionDraft` storage. The route holds up to 250 waypoints and 64 outline vertices; the preview is volatile until the user confirms, and the saved route changes only after Save draft. This phase adds no production code, dependency, permission, schema, external destination or vehicle command.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 2m 2s`; 10 tasks executed, 87 up-to-date. Existing JVM XML reports 533 tests in 84 suites, zero failures/errors/skips; the JVM task reused its result. Lint XML reports zero errors and 27 warnings.
- The disposable API 31 emulator passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30` before the current APKs were installed with `adb install -r`; no emulator data was cleared.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#maximumGeoJsonRouteImportsAndSavesExactly'` — fresh `OK (1 test)` in 22.905 seconds. The Android health gate reports zero new crashes/ANRs and no error dialog. Evidence: ignored `app/build/acceptance-20261121-125554-b88f35dd/`.
- No app UI layout changed; the focused test asserts the visible preview. Reviewed the scoped test and project records; the existing untracked APK and demo media remain separate. `git diff --check` passed with Git line-ending warnings for the decisions and handover files. A scoped obvious-secret pattern scan of the new test and report found no matches. No assertion, validation or safety gate was weakened.

## Limits and recovery

One synthetic boundary-sized file on one emulator does not establish performance on all Android devices, handling of arbitrary third-party GeoJSON, field measurement accuracy, mission upload or rover motion. If instrumentation stops before `finally`, inspect its unique emulator document and local draft before reuse. Reverting this test-only change would not alter production behavior. Owner diff review and the wider release gate remain open. Phase approval does not authorize commit or push.
