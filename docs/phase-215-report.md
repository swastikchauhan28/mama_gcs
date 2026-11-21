# Phase 215 — Exact GeoJSON route and outline exchange

## Goal and baseline

The user approved a software-only Phase 215 on 2026-11-21 to verify that precise route and field-outline coordinates survive GeoJSON export and import. Baseline `328cdd8` on `main`; the earlier demo media under `output/` were already untracked and were not changed. RC hardware work remained paused. Model/version unavailable. See D-215-01.

## Change and data path

`MissionExchangeTest.geoJsonRetainsPreciseRouteAndOutlineCoordinatesExactly` now checks high-precision coordinate values through `MissionDraftGeoJsonCodec.encode/decode` with exact Double pair equality. The existing `RouteDocumentPickerTest.createFieldPreviewSaveExportAndReimportLocalRoute` fixture now enters precise route and outline coordinates through the actual mission UI. Its existing exact comparisons check the saved draft, decoded exported file and reimported draft after Android DocumentsUI. The test restores the previous disposable-emulator draft/recovery and removes its uniquely named temporary Downloads file in `finally`.

`MissionDraft` holds at most 250 route waypoints and 64 outline vertices. The external GeoJSON file is untrusted input to the existing bounded, validating codec; this phase adds no new file format, destination, dependency, permission, schema, persistent data or vehicle command. No production app code needed changing. Exact numeric file preservation is not field measurement accuracy.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 2m 18s`; 10 tasks executed, 87 up-to-date. Existing JVM XML reports 533 tests in 84 suites, zero failures/errors/skips; this JVM task was not a fresh full rerun. Both debug APKs were available; lint XML reports zero errors and 27 warnings.
- The existing disposable API 31 emulator passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30` before installing the current debug app and Android test APKs with `adb install -r`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.MissionExchangeTest#geoJsonRetainsPreciseRouteAndOutlineCoordinatesExactly'` — `OK (1 test)` and healthy Android. Evidence: ignored `app/build/acceptance-20261121-113955-2ec4497e/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute'` — `OK (1 test)` and healthy Android. This exercised UI entry, coverage preview, local save, real DocumentsUI export/import and exact route/outline assertions. Evidence: ignored `app/build/acceptance-20261121-114014-cad9a6ab/`.
- No UI appearance changed; visual review is not a release claim for this test-only phase. Reviewed the changed-file list and full tracked diff: two Android tests and project records only; the demo media remain separate untracked files. An obvious credential-pattern scan of the changed files found no matches. `git diff --check` passed with Git LF/CRLF conversion warnings only.

## Limits and recovery

The tests cover synthetic coordinates and one disposable emulator's local file provider. They do not establish behavior for every third-party GeoJSON file, physical survey precision, coverage in a field, mission upload or rover motion. The picker uses the app's existing map provider. An emulator interruption before `finally` could leave a test draft or temporary file, so inspect and clean only that uniquely named fixture before reuse. Owner diff review and the broader release gate remain open. No rollback was executed; reverting this test-only phase would not change production behavior. The user separately requested commit and push after completion.
