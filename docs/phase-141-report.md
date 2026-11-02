# Phase 141 — mission planning regression

Date: 2026-11-02. Starting revision: `aae0e73` on clean `main`, aligned with `origin/main`. Model: gpt-6.1-terra.

## Scope and method

The user approved a software-only mission planning regression. The no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator was booted and identified as `emulator-5554`. The existing Phase 140 debug app and test APKs were installed with `adb install -r`; no fresh build, JVM suite or lint was run in this phase. The editor and map tests temporarily write synthetic emulator drafts, and the map workflow temporarily changes the emulator basemap; their `finally` blocks attempt to restore the prior draft, recovery and basemap. An interruption can prevent restoration. The map test uses the existing configured map provider. No physical phone or vehicle was used.

Each test was invoked separately using `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.<class>#<method> com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`:

| Test | Result and scope |
| --- | --- |
| `MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel` | `OK (1 test)` in 44.495 s. Invalid input, add, reorder, edit, save, reopen, confirm removal and save; also checks the local fence and landscape map. |
| `VisualLayoutRegressionTest#waypointJumpButtonFindsMiddleInWideLayout` | `OK (1 test)` in 7.943 s. Go button, wide layout and no route action. |
| `VisualLayoutRegressionTest#waypointJumpValidatesInputAndFindsFirstAndLastAtLargeText` | `OK (1 test)` in 12.254 s. Invalid input and first/250th jump via keyboard Done at 2× Compose text size. |
| `MissionMapWorkflowAcceptanceTest#editorActionsKeepNumberedMapMarkersInSyncAcrossRotationAndStyle` | `OK (1 test)` in 58.549 s. Map markers after add, edit, reorder, removal, rotation and basemap change. |

The map workflow's synthetic landscape capture was pulled to ignored `app/build/phase141/landscape-hybrid.png` and visually reviewed: two numbered markers and their connecting line are visible. The capture is not an assessment of imagery quality. A bounded `dumpsys window` check after the tests found no matching Android error/ANR dialog. The emulator was stopped without wiping it. Logs under `app/build/phase141/` are temporary; both visual test invocations shared one log filename, so only the latter visual log remains there. The results above are the observed console outputs.

## Limits and next step

This is four targeted instrumentation invocations, not a full app regression. The tests do not prove Cube telemetry, mission upload, rover movement, physical coverage or release readiness. The full software regression, prior intermittent emulator/MapLibre issues and hardware acceptance remain open. Review this record and run additional coverage when needed. Commit/push require a separate request.
