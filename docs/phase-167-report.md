# Phase 167 — crossing-field guidance

Date: 2026-11-04. User approved Phase 167 and emulator testing. Clean baseline `eb1932831aa15e2c2ea926e0a0ead5532e98a911`; model/version unavailable. Decision D-167-01.

## Change

When coverage detects an outline that crosses or touches itself, its existing invalid result now directs users to edit or reorder vertices in Field outline. No geometry validation, preview logic, persistence, network, dependency or hardware behavior changed. Correcting a vertex does not guarantee a valid field; generate a new preview to check. Four existing exact-message assertions were updated; their recovery and other assertions remain intact.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 97 tasks, 24 executed and 73 up-to-date. JVM XML: 79 suites, 485 tests, no failures/errors/skips. Lint: 0 errors, 27 warnings.
- Disposable emulator passed the project's 30-second preflight. `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#editingCrossingVertexRecoversCoverageAndPersists,com.mamadrones.gcs.FieldCoverageAppTest#reorderFieldVerticesClearsPreviewAndPreservesRoute,com.mamadrones.gcs.FieldCoverageAppTest#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute,com.mamadrones.gcs.FieldCoverageUiTest#selfCrossingOutlineClearsPreviewAndRecoversWithoutChangingRoute'`: `OK (4 tests)` in 139.967 seconds with Android healthy. Evidence: ignored `app/build/acceptance-20261104-145600-b0f67f1a/` and `app/build/phase167/tests.txt`.
- Reviewed `app/build/phase167/crossing.png`: complete updated message and Field outline tab were visible at the tested portrait size. This single configuration is not a complete accessibility review. The real-activity tests use synthetic disposable-emulator data and restore saved state in finally; interruptions can prevent cleanup.

## Remaining and review

Review the changed validator message and four exact-message assertions. No full Android regression, physical coverage validation, or owner release acceptance is claimed. Earlier emulator startup ANRs and the Cube heartbeat remain unresolved. Rollback is a reviewed inverse source change; no production-qualified artifact or recovery drill is established. Commit/push require explicit authorization.
