# Phase 145 — coverage comparison and headland checks

Date: 2026-11-02. Starting revision: `52c8716` on clean `main`, aligned with `origin/main`. Model: gpt-6.1-terra.

## Scope and results

The user approved emulator checks of coverage direction comparison, headland and sideways-error assumptions. Existing app and test APKs were installed with `adb install -r` on the no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator (`emulator-5554`). No fresh build, JVM suite or lint was run. The selected Compose fixture tests use synthetic in-memory fields and capture mission actions; they do not save a route or connect to a vehicle.

Each test was invoked separately using `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.FieldCoverageUiTest#<method> com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`:

| Method | Result | Scope |
| --- | --- | --- |
| `headlandInvalidatesAndPreviewsRectangleTriangleAndConcaveFields` | `OK (1 test)`, 41.104 s | Headland preview, modeled ranges, invalid input and three shapes. |
| `compareDirectionsShowsIndependentCardsAndClearsAfterChanges` | First run failed in 42.875 s; retry `OK (1 test)`, 29.951 s | Direction cards, map selection and invalidation. The first run raised `IllegalArgumentException` for multithreaded access to Compose `SnapshotStateObserver` during test recomposition. Cause is unconfirmed; a passing retry is not a fix. |
| `sidewaysErrorComparisonShowsAssumptionsAndInvalidatesOldResults` | `OK (1 test)`, 27.435 s | Optional error assumption, ideal margin/gap, invalid input and warning. |
| `advisoryMinimumOverlapIsOptionalAndDoesNotChangeSettings` | `OK (1 test)`, 28.456 s | Advisory overlap and unchanged settings. |
| `overlengthMarginShowsErrorAndClearsOldComparison` | `OK (1 test)`, 34.096 s | Overlength input rejection and recovery. |

Temporary logs are in ignored `app/build/phase145/run-1.log` through `run-5.log` and `direction-retry.log`. The synthetic screenshot `app/build/phase145/comparison.png` was visually reviewed: both direction cards and their diagrams are visible. A bounded `dumpsys window` check found no matching Android error/ANR dialog after the tests. The emulator was stopped without wiping it.

## Limits and next step

Four tests passed on the first run and one passed only after a failure. Investigate the snapshot threading exception if it recurs; its source and impact outside this test are unknown. These tests cover selected software paths, not the entire app or actual soil coverage, positioning accuracy, hardware operation or release readiness. Commit/push require a separate request.
