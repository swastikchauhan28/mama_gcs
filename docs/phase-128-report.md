# Phase 128 — complete software regression

Date: 2026-10-31
Starting revision: `794a1adfe8af4408411f64507865360aa10bd00` (`main`, clean and aligned with `origin/main`).
AI model/version: unavailable.

## Goal and scope

Run the existing software regression on an Android Studio emulator, without changing production code or exercising live hardware. The alternative of relying on Phase 126 individual retries would not establish a clean grouped run; this phase instead reconciles every software test against the inventory and checks Android health for each batch. Emulator tests use controlled fixtures and cannot establish hardware, field accuracy, security, or release readiness.

## Verification and results

- Fresh build and unit checks: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain --rerun-tasks`. Result: success; 97 tasks executed; 478 JVM tests passed, zero failures, errors or skips; lint reported zero errors and 28 warnings. Log: `app/build/phase128/gradle.txt`; test XML and lint output are in the configured temporary build directory.
- Android software inventory: 206 distinct methods across 49 test classes, excluding four physical-remote acceptance methods. `app/build/phase128/run-regression.ps1` ran the inventory in ten normal-font batches and one enlarged-font batch on the disposable `MamaPhase116` API 34 emulator. Reconciliation reports 206 started, 206 passed, none missing, failed or skipped; all 11 batch health gates passed. Inventory, commands and output: `app/build/phase128/test-inventory.txt`, `run-regression.ps1`, `results.txt`, `android-console.txt` and `reconciliation.txt`; the latter lists individual instrumentation evidence directories. The existing checks include synthetic telemetry, planner and document-picker flows, not an independent manual end-to-end acceptance.
- The original emulator font-scale setting was unset and restored to unset: `app/build/phase128/font-restoration.txt`. No emulator data was wiped; no physical device or vehicle command was used. Build artifacts are ignored and may be removed by later cleaning.

## Observed failures and limitations

Before the successful suite, the first `MamaPhase126` startup showed a System UI ANR, and its no-wipe retry showed an Android Bluetooth service error. Startup observations are saved under `app/build/phase128/startup-*` and `retry-*`; the alternate emulator's initial observation is under `alt-*`. These were startup failures, not passing test runs. The subsequent healthy batch gates do not establish the root cause or repair the intermittent system failures; the earlier MapLibre native crash also remains unexplained. The four hardware-only tests, real telemetry, physical operation, field accuracy and the customer release gates remain unverified. Lint warnings remain to be assessed separately.

## Next and recovery

Review the startup evidence and investigate recurrence without wiping existing emulator data. Re-run affected checks if the environment or software changes. Preserve user phone data and keep live hardware commands out of software regression. No application behavior or dependencies changed, so restoring the starting revision is not needed for this documentation-only phase; do not reset or discard unrelated work. Commit and push require explicit authorization.
