# Phase 48 — Classic telemetry-stream diagnosis

## Goal and approved scope

The user approved a diagnostics-only phase on 22 October 2026. The goal is to make an active Bluetooth Classic session with fresh bytes but no validated supported MAVLink easier to interpret. The app will classify existing bounded decoder evidence and show the next safe hardware check. It must not identify a physical root cause from byte counters.

Starting source revision: `ad895537f27893465d566260f27a06dc23a4e7db` on clean `main`. [Decision D-048-01](DECISIONS.md#d-048-01-classify-undecoded-classic-receive-evidence-without-identifying-a-physical-fault) was recorded before implementation.

## Intended flow and limits

`ClassicConnectionCheck.assess` already requires a current open Classic session and fresh received bytes before reaching `WAITING_MAVLINK`. Phase 48 will derive a narrow category from existing session counters: no MAVLink start-byte candidates; start-byte candidates with parser rejection; unverified unsupported-ID candidates without a supported decoded message; or no more-specific evidence. The `ClassicConnectionCheckPanel` will render a diagnostic block only for this stage.

The category is not a port detector, baud detector, electrical test, source authenticator, vehicle identity, heartbeat, RC value or control permission. It will direct the operator to compare known settings and inspect the receiver-to-Cube telemetry route with power off; it will not request or write settings, send bytes, alter trust/source filtering, retain raw packets or enable commands.

## Verification — 22 October 2026

- `git diff --check` completed with no whitespace errors. Git printed existing LF-to-CRLF conversion warnings for changed tracked files; no file content was reformatted to address them.
- Fresh full software gate: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` completed with `BUILD SUCCESSFUL` in 7m57s. XML under `app/build/test-results/testDebugUnitTest/` reports **383 JVM tests in 63 suites, 0 failures, 0 errors and 0 skips**. The focused `ClassicConnectionCheckTest` reports 18 tests, including the four Phase 48 evidence categories. Debug and Android-test APKs were built. `app/build/reports/lint-results-debug.xml` reports **0 errors and 32 warnings**, matching the recorded warning baseline.
- The updated synthetic Android UI test compiled with the Android-test APK, but did not execute: no verified disposable emulator/device was available in this session. This is not visual acceptance. The known System UI ANR history remains relevant; no dialog was dismissed and no populated phone was used.
- Review found only the additive diagnosis enum/assessment field, panel block, tests and Phase 48 records. No dependencies, permissions, external destinations, parser/trust changes, raw-packet retention, configuration writes or command paths were added. No raw telemetry, device identifiers or credentials are committed.

## Recovery and open work

Rollback is removal of the additive classifier and panel block; no persisted state or hardware setup changes. Physical acceptance still requires a selected-autopilot heartbeat and fresh supported MAVLink/RC evidence from the actual secured Cube/R12/T12 route. The receiver UART selection, TX/RX crossover and receiver-side serial settings remain unverified.
