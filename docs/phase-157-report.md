# Phase 157 — focused mission and coverage regression

Date: 2026-11-03. Starting revision `a4762605385c8af0106222587ea24f6a90a1f38b`, clean `main`. Model/version unavailable. User approved focused software regression on a disposable emulator. Decisions: D-157-01 and D-157-02.

## Scope and side effects

Selected checks cover route editing/save/reopen, synthetic field outline and coverage changes, stale-preview clearing, comparing and using tool settings, enlarged-text coverage, and GeoJSON save/export/reimport through Android DocumentsUI. Existing tests temporarily replace emulator-only drafts and attempt restoration; document-picker tests create and remove a uniquely named test file. An interruption could leave synthetic data. Map-related tests may contact the existing configured provider. No physical phone, hardware, vehicle command or Cube parameter was used.

## Results and correction

- Fresh targeted JVM run (exact command below): `BUILD SUCCESSFUL`; 97 tasks, 2 executed and 95 up-to-date. XML contained 47 tests, zero failures/errors/skips; APK/lint tasks were up-to-date. Existing lint report: 0 errors, 27 warnings.
- Isolated Pixel 3a API 34 emulator, no snapshot, `swiftshader_indirect`, 2048 MB. Initial 30-second `Wait-AndroidEmulatorHealthy.ps1` preflight passed. Current APKs were installed with `adb install -r` without clearing data.
- The initial five-method `FieldCoverageUiTest` batch checked `settingsRegeneratePreviewAndNeverReplaceRoute`, `missingAndChangedOutlineCannotLeaveAnOldPreview`, `compareToolSettingsShowsBothOptionsAndInvalidatesOnEdit`, `useComparedToolSettingsUpdatesPreviewInputsAndClearsOldPreview`, and `trianglePreviewAndControlsRemainReachableAtLargeText`. Four methods passed; the enlarged-text method timed out waiting for an off-screen comparison header. The batch failed overall, while its Android health gate was true. Evidence: ignored `app/build/phase157/coverage-batch.txt` and `app/build/acceptance-20261103-161318-c281f407/`.
- The independent `MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel` and `RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute` batch passed `OK (2 tests)` in 151.217 s with Android healthy. Evidence: ignored `app/build/phase157/mission-batch.txt` and `app/build/acceptance-20261103-161551-a2e0d892/`.
- The test correction waits for the existing comparison calculation to finish, scrolls the lazy editor to its header and retains both direction visibility assertions. Its first isolated retry passed `OK (1 test)` in 17.987 s with Android healthy. A screenshot showed the east/west card only partly in the horizontal viewport, so the test was strengthened to swipe and check the entire modeled range fits horizontally. The final isolated retry passed `OK (1 test)` in 16.877 s with Android healthy. Original and final evidence: ignored `app/build/phase157/{large-text-retry.txt,large-text-final.txt,large-comparison.png,large-comparison-final.png}` and `app/build/acceptance-20261103-{162826-4c3b8f6e,163642-bc8d9075}/`. The final screenshot was visually inspected and shows the full east/west range after the swipe.
- After the test-only change, final `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded; 69 tasks, 9 executed and 60 up-to-date. No production code changed.
- One emulator restart showed a Bluetooth-system error dialog and blocked preflight; it was stopped without dismissing the error. Evidence: ignored `app/build/phase157/{preflight-retry.txt,retry-error-window.txt,retry-crash.txt,retry-anr.txt}`. A later preflight passed (`preflight-retry2.txt`) and the test retry passed. Emulator was stopped without wiping.

```powershell
./gradlew.bat :app:testDebugUnitTest --rerun --tests 'com.mamadrones.gcs.domain.model.MissionDraftTest' --tests 'com.mamadrones.gcs.domain.model.MissionDraftReviewTest' --tests 'com.mamadrones.gcs.domain.model.FieldCoveragePlannerTest' --tests 'com.mamadrones.gcs.domain.model.CoverageMapProjectionTest' --tests 'com.mamadrones.gcs.domain.model.CoverageDirectionComparisonTest' --tests 'com.mamadrones.gcs.presentation.mission.FieldCoverageStateCancellationTest' --tests 'com.mamadrones.gcs.presentation.mission.CoverageSettingsComparisonStateTest' --tests 'com.mamadrones.gcs.presentation.mission.CoverageRangeFormatTest' --tests 'com.mamadrones.gcs.presentation.mission.ApplyComparedCoverageSettingsTest' --tests 'com.mamadrones.gcs.data.local.datastore.MissionDraftCodecTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
```

The Android runs used the following explicit selection (each invocation also piped output to the evidence file named above):

```powershell
$adb = 'C:\Users\swastik\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$coverage = @('com.mamadrones.gcs.FieldCoverageUiTest#settingsRegeneratePreviewAndNeverReplaceRoute','com.mamadrones.gcs.FieldCoverageUiTest#missingAndChangedOutlineCannotLeaveAnOldPreview','com.mamadrones.gcs.FieldCoverageUiTest#compareToolSettingsShowsBothOptionsAndInvalidatesOnEdit','com.mamadrones.gcs.FieldCoverageUiTest#useComparedToolSettingsUpdatesPreviewInputsAndClearsOldPreview','com.mamadrones.gcs.FieldCoverageUiTest#trianglePreviewAndControlsRemainReachableAtLargeText') -join ','
./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass $coverage
$mission = @('com.mamadrones.gcs.MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel','com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute') -join ','
./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass $mission
./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#trianglePreviewAndControlsRemainReachableAtLargeText'
```

The result is seven distinct selected Android methods accounted for across a failed five-test batch, a passing two-test batch and a passing isolated retest; it is **not** one uninterrupted seven-test pass or a full-app regression. The correction changes test navigation and strengthens visibility verification; it does not prove every device or text scale is accessible.

## Remaining

Owner walkthrough, broader regression, physical Cube heartbeat, actual field coverage and release acceptance remain open. Earlier emulator/Compose instability and the Bluetooth-system startup error are not diagnosed. Review the scoped test diff and preserved evidence before committing.
