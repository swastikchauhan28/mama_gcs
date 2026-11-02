# Phase 147 — emulator retry for coverage comparison

Date: 2026-11-02. Starting revision: `e2bbbf7`, clean `main` aligned with `origin/main`. Model: gpt-6.1-terra. The user approved a reversible emulator configuration and retry.

## Method and result

At inspection the host reported approximately 3,579 MB free RAM. The existing `Pixel_3a_API_34_extension_level_7_x86_64` AVD was configured for eight cores and 1,536 MB RAM. It was launched with `-no-snapshot-load -no-snapshot-save -no-boot-anim -cores 4`; the core setting applied only to this launch, and the AVD configuration and data were not wiped. Existing app and test APKs were reused. No build, JVM suite or lint ran.

The emulator appeared as `emulator-5554`. Three separate invocations of `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.FieldCoverageUiTest#compareDirectionsShowsIndependentCardsAndClearsAfterChanges com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner` each returned `OK (1 test)`, in 41.651, 48.028 and 53.112 seconds. The test uses synthetic in-memory fields. Temporary outputs are in ignored `app/build/phase147/run-1.log` through `run-3.log`.

Bounded `dumpsys window` checks found no matching Android error/ANR window before each run or after the third. The emulator was stopped without wiping data. No physical phone or vehicle was used.

## Limits and next step

Three passes did not reproduce the Phase 145 Compose snapshot exception or the Phase 146 Android freezes. Host memory and the launch core count differed from the earlier attempts, so this run cannot establish which factor affected stability or prove a fix. Keep the historical failures open, use the launch-only setting for further checks, and capture evidence if they recur. Full software regression, physical field coverage, hardware communication and release acceptance remain open. Commit/push require a separate request.
