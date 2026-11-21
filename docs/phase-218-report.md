# Phase 218 — Over-limit GeoJSON import preserves the route

## Goal and baseline

The user approved a software-only Phase 218 on 2026-11-21 to verify that over-limit GeoJSON route and field-outline files are rejected without replacing the saved or unsaved route. Baseline `de731b9` on `main`; earlier demo media under `output/` were already untracked and remain outside this phase. RC work remains paused. Model/version unavailable. See D-218-01.

## Change and data path

`RouteDocumentPickerTest.overLimitGeoJsonImportsPreserveSavedAndWorkingDraft` creates two uniquely named GeoJSON documents in the disposable emulator's Downloads provider: a FeatureCollection with 251 Point features and one with a closed 65-vertex keep-in Polygon. Both are below the existing file-size limit and target their respective parser bounds. The test starts from a saved one-point route and a different unsaved latitude edit. After each real DocumentsUI import, it verifies the specific visible error, no replacement preview, unchanged saved route and retained unsaved edit. It restores the emulator's previous draft and recovery copy and deletes only its created document URIs in `finally`.

External file content flows through Android's document provider, the screen's 256,000-character bounded reader, `MissionPlanViewModel.importDraft`, `MissionDraftRouteCodec` and `MissionDraftGeoJsonCodec`. The codec enforces `MissionDraft.MAX_WAYPOINTS` (250) and `MAX_FENCE_VERTICES` (64) before constructing a draft. A rejected import only updates the UI error. No production code, format, dependency, permission, schema, external destination or vehicle command changed.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 4m 52s`; 10 tasks executed, 87 up-to-date. Existing JVM XML reports 533 tests in 84 suites, zero failures/errors/skips; the JVM task reused its result. Lint XML reports zero errors and 27 warnings.
- The disposable API 31 emulator's initial 30-second preflight caught one Google Play services ANR. Evidence is retained under ignored `app/build/phase218-emulator-boot/`. A later quiet 30-second preflight passed before installation. The failed preflight is not counted as a test pass.
- Installed current app and Android test APKs on `emulator-5554` with `adb install -r`, without clearing emulator data.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#overLimitGeoJsonImportsPreserveSavedAndWorkingDraft'` — fresh `OK (1 test)` in 48.108 seconds. Post-run Android health reports zero new crashes/ANRs and no error dialog. Evidence: ignored `app/build/acceptance-20261121-123933-806f2b20/`.
- Reviewed the test and phase records; the prior untracked demo media remain separate. `git diff --check` passed with Git line-ending warnings for the decisions and handover files. A scoped obvious-secret pattern scan of the new test and report found no matches. No assertion, validation or safety gate was weakened.

## Limits and recovery

This covers two synthetic files on one emulator, not every third-party GeoJSON input. It does not establish physical route accuracy, mission upload or rover motion. If the test is interrupted before `finally`, inspect its unique temporary documents and disposable draft before reuse. Reverting this test-only change would not alter production behavior. Owner diff review and the wider release gate remain open. Phase approval does not authorize commit or push.
