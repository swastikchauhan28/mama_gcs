# Phase 168 — mission-planning regression

Date: 2026-11-04. User approved Phase 168 and disposable-emulator testing. Clean baseline `087b7138ea4fa4c22e71aaba65427ae68ece555a`; model/version unavailable. Decision D-168-01.

## Scope and change

Ran existing complementary synthetic tests across route creation, edit/reorder and save/reopen; field creation, edit/reorder and crossing recovery; coverage generation/comparison; and GeoJSON export/import via Android DocumentsUI. These are separate tests, not one combined uninterrupted journey. Changed only the document-picker test cleanup to clear recovery when originally absent and assert previous draft and recovery restoration. The test uses a unique temporary export and removes it in finally. An interruption can prevent cleanup; other existing real-app tests restore previous drafts but do not all assert absence/restoration of a previously empty recovery. Run only on a disposable emulator. No production application code, phone data, hardware, or vehicle commands changed.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 97 tasks (9 executed, 88 up-to-date); lint 0 errors, 27 warnings. JVM results were up-to-date, not freshly executed in this phase; historical Phase 167 records 485 passing tests.
- Emulator exceeded the initial boot wait but completed boot shortly afterward; its read-only 30-second preflight passed. Startup window and exit-info captured under ignored `app/build/phase168/`. Prior ANRs remain unexplained.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute'`: `OK (1 test)` in 74.154 seconds, Android healthy. Evidence `app/build/acceptance-20261104-151729-f4736dae/` and `app/build/phase168/route-exchange.txt`.
- The same runner with `-TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#editFieldVertexValidatesClearsPreviewAndPersists,com.mamadrones.gcs.FieldCoverageAppTest#reorderFieldVerticesClearsPreviewAndPreservesRoute,com.mamadrones.gcs.FieldCoverageAppTest#editingCrossingVertexRecoversCoverageAndPersists,com.mamadrones.gcs.MissionWorkflowTest'`: `OK (17 tests)` in 130.636 seconds, Android healthy. Evidence `app/build/acceptance-20261104-151906-8b9b76a0/` and `app/build/phase168/field-workflow.txt`.
- The same runner with `-TestClass 'com.mamadrones.gcs.MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel'`: `OK (1 test)` in 65.548 seconds, Android healthy. Evidence `app/build/acceptance-20261104-152148-29675940/` and `app/build/phase168/route-edit.txt`.
- Reviewed `app/build/phase168/coverage.png` showing the synthetic coverage diagram and estimates; upper scrolling content is partly clipped behind the fixed toolbar. Reviewed `crossing.png` showing the invalid-outline guidance. These screenshots do not establish broader visual or accessibility acceptance.

## Remaining and review

Review the recovery cleanup change and regression logs. Nineteen focused Android tests passed across three runs, not the entire app or a single end-to-end execution. Physical coverage, rover command integration, Cube heartbeat, owner release review and validated rollback remain open. The app's preview is modeled and does not guarantee treated soil. Commit/push require explicit authorization.
