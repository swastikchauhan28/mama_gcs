# Phase 165 — edit field-outline vertices

Date: 2026-11-04. User approved Phase 165 and disposable-emulator tests. Baseline `fb6f3ea3a7730de3085462260efd967f82bafe44`, clean `main`; model/version unavailable. Decision: D-165-01.

## Change and boundary

The field-outline row offers Edit. The existing validated coordinate dialog displays the selected vertex using round-trip precision and dispatches an edit identified by its original ID and coordinates. Both UI and view model reject a changed or missing original vertex. The immutable draft replaces that vertex in place, retaining the route and other vertices. No-op edits avoid scheduling a new recovery write. A changed outline invalidates the existing volatile coverage preview; existing explicit save and working-copy recovery remain. New coordinates can still make an invalid polygon; existing coverage geometry validation must be used. This local planning feature does not upload a mission or operate hardware. The dialog snapshot holds one bounded vertex; the local draft remains subject to its existing 64-vertex limit.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 97 tasks (30 executed, 67 up-to-date), 485 JVM tests across 79 suites, zero failures/errors/skips; lint 0 errors and 27 warnings.
- Two no-wipe emulator starts failed health preflight: first showed Android System UI/system ANR dialogs; second recorded a new `com.google.android.as` ANR. Logs and window/exit information are under ignored `app/build/phase165/`. Cause remains unverified; no error dialog was dismissed.
- A different existing disposable userdata image passed the 30-second health check. `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.MissionWorkflowTest#fieldVertexEditRejectsStaleAndInvalidChangesAndSaves,com.mamadrones.gcs.FieldCoverageUiTest#editingOutlineRejectsChangedVertex,com.mamadrones.gcs.FieldCoverageAppTest#editFieldVertexValidatesClearsPreviewAndPersists'` passed all 3 tests in 59.676 seconds with Android healthy. Evidence: `app/build/acceptance-20261104-135715-bee38e6d/` and `app/build/phase165/tests.txt`. The real-activity test uses synthetic data, checks invalid input, cancel, preview preservation and clearing, save and reopen, and restores the previous disposable draft/recovery in `finally`; interruption can prevent cleanup.
- Reviewed `app/build/phase165/edit-dialog.png`: title, full precision coordinates, and Apply/Cancel were visible. This is one normal-size emulator screenshot, not a complete accessibility review.

## Remaining and review

Review the stable-ID update and stale snapshot guard, validation, preview invalidation and local persistence test. This is targeted coverage; no full Android regression, owner release approval, physical field coverage or hardware validation is claimed. Emulator ANRs remain unexplained. A reviewed inverse source change is the rollback approach; no production-qualified artifact or recovery drill is established. Commit/push require explicit authorization.
