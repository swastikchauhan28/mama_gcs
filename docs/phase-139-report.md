# Phase 139 — confirm local waypoint removal

Date: 2026-11-02. Starting revision: `6aac355` on `main`; the existing README, handover and hardware-record edits were preserved. AI model/version: unavailable.

## Goal and design

Prevent an accidental tap on a Route-list **Remove** button from immediately changing the working draft while hardware work is paused. The confirmation names the current waypoint number and coordinates, and explains that the saved draft changes only after **Save draft**. Cancel, Android back and outside dismissal emit no route action. The confirm button is enabled only while the draft is editable and the selected stable waypoint ID still exists. See decision D-139-01 in [DECISIONS](DECISIONS.md).

The screen holds one nullable pending waypoint ID in `rememberSaveable`; it reads the current draft to render the dialog and emits the existing `MissionPlanAction.Remove(id)` only on confirmation. The mission ViewModel and local draft repository retain ownership of edits, recovery and explicit saves. No parameter, transport, command, dependency, permission, schema, account, credential or external destination changed. The ID is local UI state, not an authenticated vehicle identity. Existing recovery behavior may store a working-edit copy after a confirmed removal; Cancel does not start an edit.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: passed, 97 tasks (51 executed, 46 up-to-date). Test XML: 478 JVM tests, zero failures or skips. Lint XML: zero errors, 27 warnings. Existing compiler/deprecation warnings remain.
- After the final test-only screenshot adjustment, `./gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` and a final `./gradlew.bat :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed. The final lint run executed 3 tasks and reused 31 up-to-date tasks; the app and JVM test sources were unchanged.
- On the no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator, `VisualLayoutRegressionTest#waypointRowActionsFitAndKeepReorderBoundariesAtLargeText` passed after the screenshot test was corrected. It verifies the dialog appears, Cancel emits no remove action, and confirmation emits exactly the selected waypoint ID. The final whole-window screenshot at `app/build/phase139/remove-confirmation.png` was visually reviewed: title, waypoint number/coordinates, Cancel and Remove waypoint are legible at the test's 2x Compose text density.
- `MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel` passed on the same emulator. It exercises the real local editor, confirms removal, saves, recreates the ViewModel, and checks the remaining waypoint. The test restores the previous emulator draft in `finally`; no physical phone was used.
- The first focused run failed only in the new screenshot capture because the dialog creates a second Compose root. A second run passed its assertions but captured the underlying app root, so visual acceptance remained open. The final test uses Android's whole-window screenshot and passed; this preserves the failure history. The two updated map-workflow removal methods compiled but were not run in this phase. No full Android regression or hardware acceptance was performed.

## Remaining risks and next step

The extra confirmation tap changes the editing workflow. Other screen sizes, actual Android font settings, and the full Android regression remain unverified for this phase. Prior emulator startup and MapLibre native issues remain separate. Review the scoped diff, then run the full software regression on a healthy disposable emulator before release. Vehicle command and physical safety gates remain unavailable. No production rollback is established; a future inverse must target only this UI/test change and preserve pre-existing documentation and user data. Commit/push require a separate user request.
