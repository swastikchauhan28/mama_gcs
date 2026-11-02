# Phase 143 — field coverage preview verification

Date: 2026-11-02. Starting revision: `d0b154e` on clean `main`, aligned with `origin/main`. Model: gpt-6.1-terra.

## Scope and results

The user approved field coverage verification on the emulator. The no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator was identified as `emulator-5554`; the existing Phase 140 app and test APKs were installed with `adb install -r`. No fresh build, JVM suite or lint was run in this phase.

Each test was invoked separately using `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.<class>#<method> com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`:

| Test | Result and scope |
| --- | --- |
| `FieldCoverageAppTest#realPlannerShowsCoverageInPortraitAndLandscapeWithoutSavingIt` | `OK (1 test)`, 84.761 s. Real planner, triangle preview and comparison in both orientations; checks the saved route is unchanged. |
| `FieldCoverageUiTest#settingsRegeneratePreviewAndNeverReplaceRoute` | `OK (1 test)`, 39.119 s. Width/overlap spacing and pass counts, invalid overlap, direction changes and no mission action. |
| `FieldCoverageUiTest#missingAndChangedOutlineCannotLeaveAnOldPreview` | `OK (1 test)`, 23.021 s. Clears stale preview and rejects a missing outline. |
| `FieldCoverageUiTest#triangleCoverageReachesOfflineMap` | `OK (1 test)`, 27.458 s. Synthetic triangle preview reaches the offline map. |
| `FieldCoverageUiTest#concaveCoverageReachesOfflineMap` | `OK (1 test)`, 24.734 s. Synthetic concave preview reaches the offline map, including split passes. |
| `FieldCoverageUiTest#editingFieldClearsSelectedCoverage` | `OK (1 test)`, 24.038 s. Editing the synthetic outline clears the selected map preview. |

The real planner test temporarily saves a synthetic emulator draft and attempts to restore the previous draft and recovery in `finally`; an interruption could leave test data behind. Fixture map tests use a temporary offline style and synthetic geometry; they do not send mission actions. The real planner may use the existing configured map provider. No physical phone or vehicle was used.

The portrait screenshot at ignored `app/build/phase143/coverage-portrait.png` was visually reviewed: the triangle diagram, modeled gap estimates and limitations are visible. Logs at `app/build/phase143/run-1.log` through `run-6.log` are temporary artifacts. A bounded `dumpsys window` check found no matching Android error/ANR dialog after the tests. The emulator was stopped without wiping it.

## Limits and next step

These six tests do not cover all shapes, settings or the entire app. Preview calculations and sampled gaps are models, not measurements of actual soil coverage or positioning accuracy. Hardware, mission upload, physical operation and release acceptance remain open. Review the report and continue risk-based verification. Commit/push require a separate request.
