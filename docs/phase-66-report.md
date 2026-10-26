# Phase 66 — Enlarged-text waypoint row actions

Date: 2026-10-26

## Goal and scope

Check the route point Edit, Remove, Up and Down actions at a 320 dp window with 2× text. Check their labels and horizontal bounds, the first/middle/last reorder limits and the existing local callbacks. The test uses three synthetic waypoints in a `MissionPlanUiState` fixture and stores callbacks only in memory. It does not use the mission repository, persist route data, connect telemetry or send vehicle commands.

## Finding

The row's existing `FlowRow` wraps its actions for enlarged text. Some controls are below the initial list viewport, so the editor must scroll to them. Each action becomes fully visible when the list scrolls to it; labels stay on one line and within the 320 dp screen. The final visual capture shows the wrapped controls without overlap. Existing layout and list scrolling were sufficient, so no production code change was made.

## Verification

- Test APK build: `gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL** (6 tasks executed, 54 up-to-date). The debug app APK was unchanged from Phase 64/65.
- Focused test: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#waypointRowActionsFitAndKeepReorderBoundariesAtLargeText'` — **PASS, `OK (1 test)`**, Android health gate passed, SwiftShader emulator.
- The test scrolls each button into view, checks the label text layout and window bounds, confirms Up is disabled on the first item and Down on the last, and verifies enabled Move behavior for the middle item. Edit opens the coordinate dialog and Cancel closes it; Move and Remove dispatch only the expected in-memory actions.
- Screenshot captured by the test and visually inspected: `app/build/phase66-waypoint-actions.png`.
- Earlier attempts are preserved: the first test compared bounds from different scroll offsets and reported an invalid overlap; the next attempt showed Remove was not visible before scrolling to it. The final test explicitly scrolls to each action and passed. Neither earlier result is counted as a pass.
- No full Android suite, lint, physical phone or hardware test was run.

Final instrumentation and health evidence: `app/build/acceptance-20261026-152119-093f6923/`.

## Limits and review

This is one synthetic English route at one emulator width and scale. It proves UI geometry and callback behavior only, not persistence or field-device rendering. Review the new `waypointRowActionsFitAndKeepReorderBoundariesAtLargeText` test in `app/src/androidTest/java/com/mamadrones/gcs/VisualLayoutRegressionTest.kt`; Phase 66 did not change production files. Rollback is limited to this test and its Phase 66 records. Phases 60–65 and unrelated `desktop.ini` were preserved. Phase 66 is uncommitted; commit/push was not requested.
