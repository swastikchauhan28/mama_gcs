# Phase 65 — Enlarged-text waypoint dialog check

Date: 2026-10-26

## Goal and scope

Check the mission editor's Add coordinates dialog at a 320 dp window and 2× text. Ensure each coordinate field can be brought fully into view, Add and Cancel remain reachable, and valid coordinates still take the existing local Add path. The test uses a synthetic `MissionPlanUiState(loading=false)` and an in-memory action list; it does not use the repository, write a route, open a file picker, connect telemetry or send a vehicle command.

## Finding

The dialog already places its coordinate text area in a vertical scroll container and keeps its Add/Cancel footer outside that area. The pre-scroll screenshot showed the longitude field partly below the visible text viewport. Scrolling the content brought longitude fully into view while preserving the dialog actions. At the tested width and scale, the existing scroll behavior is sufficient, so no production layout change was justified.

## Verification

- Emulator verified from the current ADB inventory: `emulator-5554`, Android boot complete, no pre-existing error dialog; SwiftShader renderer.
- Final Android test APK build: `gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL** (6 tasks executed, 54 up-to-date). The installed debug app APK was already built successfully in Phase 64 and was unchanged in Phase 65.
- Focused test: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#waypointDialogKeepsFieldsAndActionsReachableAtLargeText'` — **PASS, `OK (1 test)`**, Android health gate passed, no new ANR/crash. It checks both fields after scrolling, footer bounds, Add disabled before valid input, and the expected in-memory `MissionPlanAction.Add(-35.36, 149.16)` after entry.
- Screenshot captured by the test and visually inspected: `app/build/phase65-waypoint-dialog.png`. It shows the scrolled longitude field fully visible and Add/Cancel available. The text area may need scrolling at this scale.
- Earlier failed attempts were retained: one build initially used an unsupported `onRoot` matcher signature; after correcting it, the first emulator attempt tried to scroll directly to a not-yet-composed LazyColumn item. The test now scrolls the editor list to the Add coordinates item and explicitly scrolls inside the dialog. Neither earlier attempt is counted as a pass.
- No full Android suite, lint, physical phone or hardware test was run.

Final instrumentation and health evidence: `app/build/acceptance-20261026-145956-12244645/`.

## Limits and review

This is one English synthetic Compose fixture at one width, scale and emulator. It demonstrates software layout and the local callback only, not physical device rendering or route persistence. Review the new test in `app/src/androidTest/java/com/mamadrones/gcs/VisualLayoutRegressionTest.kt`; no production file changed in Phase 65. Rollback is limited to removing this test and its Phase 65 records. Phases 60–64 and unrelated `desktop.ini` were preserved. Phase 65 is uncommitted; commit/push was not requested.
