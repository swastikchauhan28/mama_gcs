# Phase 140 — jump to a local waypoint

Date: 2026-11-02. Starting revision: `4e4402e` on clean `main`, aligned with `origin/main`. AI model/version: unavailable.

## Goal and design

The Route editor allows up to 250 points, making a numbered point slow to find by manual scrolling. Add a bounded numeric input that accepts only an existing 1-based waypoint number, scrolls the Route list to that point, and highlights the selected row. Both **Go to waypoint** and keyboard **Done** invoke the same action. The text field has a three-character limit derived from `MissionDraft.MAX_WAYPOINTS`; the current draft count controls validity. Empty or invalid input does not scroll.

`MissionScreen` owns the input, `LazyListState`, and highlighted stable waypoint ID as transient UI state. It reads the current `MissionPlanUiState` draft and never emits a `MissionPlanAction` for a jump. The ViewModel and repository continue to own route edits and persistence. The index offset includes the shared header, optional narrow-screen map, and Route introduction; changing that item structure requires revisiting the offset. No dependency, permission, schema, account, credential, external destination, telemetry path, Cube parameter, or vehicle command changed. See D-140-01 in [DECISIONS](DECISIONS.md).

The local draft is the only external input to this control. Its 250-point bound already applies before rendering, and the numeric field is limited to three characters. A jump can fail to find its intended row if the list structure changes without updating the offset; the highlighted stable ID then remains a visual cross-check. A draft change clears the highlight. The input is saveable for screen recreation but does not enter the route file or repository.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: passed on the final code, 97 tasks (27 executed, 70 up-to-date). XML results: 478 JVM tests, zero failures or skips. Lint: zero errors, 27 warnings.
- On the no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator, `VisualLayoutRegressionTest#waypointJumpValidatesInputAndFindsFirstAndLastAtLargeText` passed (`OK (1 test)`). The synthetic 250-point draft at 2× Compose text size rejects `251` and `abc`, reaches and highlights waypoint 250 and waypoint 1 through keyboard Done, and emits no route actions.
- After adding a separate wide-layout check, `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed (69 tasks, 10 executed and 59 up-to-date; lint still zero errors and 27 warnings). `VisualLayoutRegressionTest#waypointJumpButtonFindsMiddleInWideLayout` passed on the same emulator (`OK (1 test)`): it taps Go, reaches and highlights waypoint 3 in the wide list, and emits no route action. A tool wait timed out during an earlier invocation; the repeated run captured the pass result.
- The final whole-window screenshot in ignored `app/build/phase140/waypoint-250.png` was visually reviewed: waypoint 250 and its jump highlight are legible in the short landscape viewport. The screenshot contains only synthetic coordinates.
- Two initial focused attempts timed out waiting for waypoint 250 after tapping Go. A captured interim screenshot showed the numeric keyboard covering most of the short landscape editor. The revised implementation added keyboard Done and cleared focus before scrolling; the unchanged target-row assertions then passed. These earlier failures remain part of the test history.

## Remaining risks and next step

The tests use synthetic drafts and Compose text scaling; a full Android regression and physical font-setting check remain open. No hardware, route upload, or field coverage accuracy can be inferred from these UI tests. Review the scoped diff and include the control in a later broader regression. A rollback would revert only the Phase 140 UI/test/docs changes from the starting revision while preserving newer user work; no production rollback artifact or release acceptance is established. Commit/push require a separate user request.
