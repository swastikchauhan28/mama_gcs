# Phase 203 — Bounded telemetry processing off the UI dispatcher

## Goal and baseline

The user approved the bounded receive-path security phase on 2026-11-19 after a read-only audit. Baseline `74f0308`, clean `main` worktree; model/version unavailable. This is read-only telemetry hardening, not telemetry authentication or vehicle-control work. See D-203-01.

## Change and execution path

The production `DefaultMavlinkSessionFactory` retains each caller scope's cancellation Job but uses a serialized background dispatcher for MAVLink receive and timeout work. `MavlinkSession` still subscribes to the hot transport flow before opening the link, then parses, applies source selection, routes accepted messages and publishes diagnostics on that worker. It checks cancellation between results. `stop()` waits for its receiver to finish before publishing a stopped session. Heartbeat and timeout repository updates use atomic StateFlow updates.

No new queue was introduced. TCP, Classic and BLE still cap their receive buffers at 128 chunks and fail their link when full; UDP retains its 64-chunk suspending buffer. A test-only constructor clock option lets the local TCP overflow test supply monotonic time; production retains Android's elapsed clock. The data is volatile and not persisted. Input is unauthenticated telemetry; accepted heartbeat source IDs are not proof of Cube identity. No permissions, dependencies, external destinations, credentials, source-selection policy or command path changed.

## Verification

The first full gate after the dispatcher change passed 522 JVM tests in 82 suites, both debug APK builds, and lint with 0 errors / 27 warnings. A subsequent new local TCP overflow test initially failed before buffer evaluation because the Android elapsed clock is not available in plain JVM tests. The constructor clock option above addresses only that testability issue; its targeted retest passed.

The final `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` run passed. Fresh JVM XML reports show 523 tests in 83 suites, 0 failures/errors/skips. Both debug APKs built; lint reported 0 errors / 27 warnings. This is a software gate, not a physical acceptance test.

The first disposable emulator boot passed Android boot/window inspection but failed the 30-second preflight with one `com.google.android.gms.persistent` SIM-state broadcast ANR before any Mama GCS test ran. Evidence is in ignored `app/build/phase203-emulator-preflight-20261119.txt`. OneDrive was using about 1.7 GB at that time. The operator quit OneDrive for a no-wipe retry. The second boot also failed preflight due to a Google Play services SIM-state broadcast ANR, with about 3.3 GB free memory; evidence is in ignored `app/build/phase203-emulator-preflight-retry-20261119.txt`. Both failed attempts remain recorded.

A third no-wipe boot used the same disposable `Pixel_3a_API_34_extension_level_7_x86_64` AVD with `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-audio`. After a 90-second post-boot settling interval, `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30` passed. Both fresh APKs were installed with `adb install -r`. `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.ClassicMavlinkPathUiTest` passed `OK (4 tests)` and the post-test Android health gate. Evidence: ignored `app/build/acceptance-20261119-122601-66be37d1/instrumentation.txt` and `android-health.txt`. The runner could not identify the GPU option, although the QEMU launch command line was inspected separately. The fixture uses memory-only synthetic MAVLink frames, opens no hardware link and sends no command.

## Risks and next step

Finite receive queues can still saturate under sustained load, and dispatcher scheduling cannot by itself guarantee heartbeat timing on every device. A synthetic software test does not establish live Cube reliability or sender authenticity. Review the diff and test evidence before treating this as a release candidate. Recovery is the baseline revision above plus reversal of this cohesive diff; verify build, link status and heartbeat behavior after any authorized rollback. No rollback was performed.
