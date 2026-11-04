# Phase 163 — reorder field-outline vertices

Date: 2026-11-04. User approved the phase and emulator testing. Clean starting revision `278822a9d382961cf0fbeac0d6c075e9819a5495`; model/version unavailable. See D-163-01.

## Change and boundaries

Added Move up/Move down to each local field vertex, disabled at the first/last positions and when editing is unavailable. Controls wrap and have vertex-specific accessibility descriptions. `MissionScreen` dispatches a stable ID and one-step offset; `MissionDraft.moveFenceVertex` validates them and reorders a copy of the bounded outline while preserving route points and coordinates. `MissionPlanViewModel` uses its existing explicit-save and recovery path; boundary no-ops do not reset recovery state. `rememberFieldCoverageState` recreates and disposes volatile preview state when the ordered outline changes. No schema, dependency, network destination, or vehicle command changed. Reordering can create invalid geometry, which remains subject to planner validation.

The walkthrough now explains how to reorder and regenerate a preview. The bounded list is at most 64 `DraftWaypoint` entries, stored with the local draft; only explicit save or the existing working-copy recovery flow persists edits. A preview is still a model and is not an uploaded or executable route.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 97 tasks, 31 executed, 66 up-to-date. Fresh JVM XML: 79 suites, 484 tests, 0 failures/errors/skips. Lint: 0 errors, 27 warnings.
- Initial disposable-emulator 30-second preflight found one ANR; second showed System UI ANR. Evidence under ignored `app/build/phase163/preflight.txt`, `preflight-exit-info.txt`, `preflight-retry.txt`, and `retry-window.txt`. No dialog was dismissed. A third no-wipe start passed the health preflight (`preflight-third.txt`).
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.MissionWorkflowTest#fieldVertexMoveUsesDraftRecoveryAndExplicitSave'`: `OK (1 test)` in 0.533 s, Android healthy. Evidence: `app/build/phase163/workflow-test.txt`, `app/build/acceptance-20261104-115211-b17d0b5c/` (ignored). It checks moved order, preserved route, recovery, boundary no-op, and explicit save.
- Same runner for `com.mamadrones.gcs.FieldCoverageAppTest#reorderFieldVerticesClearsPreviewAndPreservesRoute`: normal font `OK (1 test)` in 52.489 s, Android healthy (`app-test.txt`, `app/build/acceptance-20261104-115226-632651da/`). It checks disabled edge buttons, moves down to a crossing field and up to correct it, preview invalidation/recovery, vertex order, route coordinates, and unchanged saved draft.
- Same real-app test with Android system font 2×: `OK (1 test)` in 40.158 s, Android healthy (`app-large-test.txt`, `app/build/acceptance-20261104-115344-6ae6f944/`). Original system font 1.0 was restored and re-read (`font-restore.txt`). The runner could not determine its GPU option. Reviewed ignored `app/build/phase163/large-phase163-field-order.png` and `large-phase163-recovered.png`; buttons wrap at large text and the regenerated preview is visible.

## Remaining and review

Review `MissionDraft.moveFenceVertex`, the view-model action/no-op handling and field-row buttons together; review the tests for the local saved/recovery flow and correct preview invalidation. No full Android regression, exhaustive accessibility review, physical coverage or hardware acceptance was performed. The earlier ANRs remain undiagnosed. The real-app test temporarily replaces the disposable draft, restores the previous draft and recovery in `finally`, and can be interrupted before cleanup. No customer release or known-good production rollback artifact has been established.


## 2026-11-04 follow-up — broader mission regression blocked

At the user's request to complete verification, reviewed the Android inventory. Hardware-only tests and tests requiring conflicting system-font settings cannot safely be treated as one undifferentiated batch. Planned a software-only mission editor, coverage and draft workflow regression on the existing disposable emulator. No additional regression test started: three no-wipe starts failed the 30-second Android health preflight. The first and third showed System UI ANR dialogs; the second reported a new ANR event. Evidence is retained in ignored `app/build/phase163/regression-preflight.txt`, `regression-preflight-retry.txt`, `regression-preflight-third.txt` and related window/exit-info files. No error dialog was dismissed. The disposable emulator was stopped. These failures do not establish a Mama GCS defect and do not invalidate the earlier focused passing runs.

The broader mission regression, full Android software suite, owner review and physical acceptance remain outstanding. The feature changes remain ready for review, uncommitted and unpushed.


## 2026-11-04 follow-up — mission regression and lazy comparison repair (retest pending)

A four-core no-wipe launch of the existing disposable emulator passed its unchanged 30-second health preflight (`app/build/phase163/four-core-preflight.txt`, ignored). This per-run setting did not modify the AVD configuration; the result does not prove a fix to startup instability.

- `Test-AndroidAcceptance.ps1` with `MissionWorkflowTest,MissionPlanningTest` reported `OK (17 tests)` with Android healthy in 142.74 s; 15 executed and two Phase 152 process-stop stages skipped by explicit assumption. Evidence: `four-core-mission-regression.txt` and `app/build/acceptance-20261104-121545-a9009aeb/` (ignored).
- `FieldCoverageUiTest` batch ran 23 tests: 19 passed and four failed with 30-second waits for the lazily composed `coverage-comparison` item; Android health passed. Failures were `enlargedTextCoverageMapControlsRemainUsable`, `enlargedTextConcaveCoverageMapControlsRemainUsable`, `enlargedTextSidewaysAssumptionRemainsUsable`, and `selectedCoverageReachesOfflineMapInLandscape`. Evidence: `four-core-coverage-regression.txt` and `app/build/acceptance-20261104-121849-10813564/`.
- Updated the affected test navigation to wait for the compare control to re-enable after calculation, then scroll to and assert the comparison item. Existing map and content assertions remain. This hypothesized fix is not yet verified. `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded (69 tasks, 10 executed, 59 up-to-date).
- Three subsequent four-core no-wipe starts failed the health preflight before retests: two System UI error dialogs and one new ANR. Evidence: `fix-preflight.txt`, `fix-preflight-retry.txt`, `fix-preflight-third.txt` and associated window/exit-info files. No dialogs were dismissed; emulator stopped. The fixes are unverified, and the four failed tests remain unresolved until rerun on a healthy disposable emulator.

Next: install the rebuilt test APK on a healthy disposable emulator and use `Test-AndroidAcceptance.ps1` for each of the four methods listed above, then consider a fresh coverage batch. Preserve failed evidence and do not interpret a test navigation fix as an emulator stability fix.


## 2026-11-04 follow-up — four repaired tests passed

A first no-wipe retest startup showed a System UI ANR (`final-retry-preflight.txt`, `final-retry-window.txt`, `final-retry-exit-info.txt`); the error was retained and not dismissed. A second four-core startup passed the unchanged 30-second preflight (`final-preflight-again.txt`). The rebuilt instrumentation APK was installed without clearing data.

The acceptance runner executed the four previously failed `FieldCoverageUiTest` methods together: `enlargedTextCoverageMapControlsRemainUsable`, `enlargedTextConcaveCoverageMapControlsRemainUsable`, `enlargedTextSidewaysAssumptionRemainsUsable`, and `selectedCoverageReachesOfflineMapInLandscape`. Result: `OK (4 tests)` in 94.083 seconds, Android health gate passed, runner GPU option unavailable. Evidence: ignored `app/build/phase163/final-retest-four.txt` and `app/build/acceptance-20261104-124548-e03ff19e/`. All original map/content assertions remain. The emulator was stopped without wiping.

This confirms the targeted test-navigation repair in this run; the earlier 23-test batch had 19 passes and four failures, and was not rerun as a whole after the repair. Emulator startup ANRs remain undiagnosed. Hardware, physical coverage, a complete Android regression, and release acceptance remain outside this result.
