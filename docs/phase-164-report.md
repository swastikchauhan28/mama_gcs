# Phase 164 — confirm clearing a field outline

Date: 2026-11-04. User approved Phase 164 and emulator testing. Clean baseline `ea00b5a9b58136e7dd45e2c0ac0acc7e44badd13`; model/version unavailable. See D-164-01.

## Change and boundaries

Clear outline now opens a confirmation naming the vertex count and explaining route, saved draft and preview effects. Cancel or dialog dismissal does not dispatch a clear action. The dialog retains a bounded in-memory draft snapshot only while open; confirm is enabled only while the editable working draft still matches it. A stale draft requires closing and reopening the dialog. On explicit confirmation the existing local clear action updates the working draft and invalidates the existing preview. Existing automatic working-copy recovery and explicit save behavior remain; no route waypoint, schema, network destination, or vehicle command changes. The guard is UI-specific and does not restrict other internal callers. Recreation dismisses the dialog rather than persisting its snapshot.

Updated the operator walkthrough, added a real-activity cancel/clear/save check and a stale-snapshot fixture check, and updated the existing field-recovery test to confirm clearing. The real-activity tests temporarily replace the disposable emulator's saved draft and restore draft/recovery in `finally`; interruption could prevent cleanup.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 97 tasks, 27 executed and 70 up-to-date. JVM XML: 79 suites, 484 tests, 0 failures/errors/skips. Lint: 0 errors, 27 warnings.
- Four no-wipe starts of the existing isolated emulator failed preflight (ANRs and System UI dialogs). A separate previously created disposable image also logged ANRs on its first run; its no-wipe restart passed the unchanged 30-second health preflight. Evidence under ignored `app/build/phase164/` includes `preflight*.txt`, exit-info and window captures. No error dialog was dismissed. These failures remain undiagnosed.
- `FieldCoverageUiTest#clearOutlineConfirmationRejectsChangedDraft`: `OK (1 test)` in 17.058 s, Android healthy (`stale-test.txt`, `app/build/acceptance-20261104-132115-c584f742/`).
- `FieldCoverageAppTest#clearOutlineRequiresConfirmationAndPreservesRoute` and `#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute`: `OK (2 tests)` in 126.655 s, Android healthy (`app-tests.txt`, `app/build/acceptance-20261104-132154-35f98a38/`). The new test checks cancel preserves the preview and saved outline, confirm clears the preview, route coordinates remain, and explicit save stores the cleared field.
- The same real-app confirmation test at 2× system font passed twice: 143.067 s (`app-large.txt`, `app/build/acceptance-20261104-132432-646c12e4/`) and 48.409 s (`visual-large-test.txt`, `app/build/acceptance-20261104-133000-eb7f4d36/`), both with Android healthy. The runner reported GPU option unavailable.
- Initial normal and enlarged screenshots were partially obscured by Android's first-use immersive tutorial. After a fresh health preflight, temporarily acknowledged that tutorial on the alternate disposable emulator, reran the enlarged test, and reviewed `confirm-large-unobscured.png`: the confirmation text and both actions were visible. The previous font and tutorial settings were restored (`visual-settings-restore.txt`); emulator stopped without wiping. The first enlarged run's unset font setting reverted to Android's default 1.0 after deletion (`font-restore.txt`).

## Remaining and review

Review the draft snapshot, stale-state guard, clear action dispatch and dialog wording together. This is targeted coverage on a disposable emulator, not a full Android suite, exhaustive accessibility review, owner release approval, or physical hardware/coverage validation. Earlier emulator ANRs remain unexplained. No known-good production release artifact or release rollback drill has been established.
