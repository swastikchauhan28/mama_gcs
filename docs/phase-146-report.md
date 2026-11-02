# Phase 146 — coverage snapshot exception investigation

Date: 2026-11-02. Starting revision: `701ee6e`, clean `main` aligned with `origin/main`. Model: gpt-6.1-terra. The user authorized investigation and emulator testing of the Phase 145 intermittent failure.

## Findings

The original Phase 145 stack reports `SnapshotStateObserver` multithreaded access during Compose test-clock recomposition, with a `DefaultDispatcher` worker in the continuation stack. A similar occurrence and passing retry were already recorded in [Phase 80](phase-80-report.md). The coverage state launches comparison in a `rememberCoroutineScope`, calls the planner through `withContext(Dispatchers.Default)`, then assigns the result after that call returns. This code inspection and stack alone do not establish whether production state, Compose test scheduling or another cause produced the exception. No code fix was made without a demonstrated cause.

## Emulator attempts

Existing app and test APKs were installed with `adb install -r` on the no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator. The first start was blocked before instrumentation by an Android launcher ANR. After a no-wipe restart, `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.FieldCoverageUiTest#compareDirectionsShowsIndependentCardsAndClearsAfterChanges com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner` failed in 31.041 s with `No compose hierarchies found`; `dumpsys window` showed an Android System UI ANR. A second no-wipe restart and the same test failed in 73.519 s with the same test error and System UI ANR. Subsequent runs were stopped because the error window was present. Temporary outputs are `app/build/phase146/baseline-1.log` and `baseline-2.log`.

The original snapshot exception was not reproduced in these attempts. The System UI freezes are separate observed failures; their underlying cause is not established. The emulator was stopped without wiping it. No production code, hardware, phone data, build configuration or dependencies were changed. No fresh build, lint or JVM tests ran.

## Next step

Repeat the comparison test on a healthy disposable emulator, capture each result and investigate a reproducible snapshot stack before changing scheduling or assertions. The earlier passing retry is historical evidence, not a fix. Hardware and release acceptance remain open. Commit/push require a separate request.
