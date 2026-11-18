# Phase 200 — bounded heartbeat-gap evidence

## Goal and baseline

The user approved a read-only, memory-only record to clarify live periods where selected-autopilot heartbeats stop while other telemetry may continue. Baseline: clean `main` at `c65b6b6` on 2026-11-18. The earlier Phase 199 phone observation showed heartbeat age above 40 seconds while selected position receipts remained recent; its cause remains unknown. Model/version unavailable.

## Execution path and decision

`MavlinkSession` passes incoming bytes to `MavlinkDiagnosticsAccumulator.recordBytes`, then checksum-validated parser results and source dispositions to `recordResult`. The accumulator already uses elapsed receive times for heartbeat intervals. It now retains at most three completed intervals over five seconds, with elapsed start/recovery times and booleans for bytes and accepted `GLOBAL_POSITION_INT` received after the threshold before the recovery chunk. The current gap uses current-session latest receipts. `ConsolePanels.heartbeatTiming` displays the record in Analyze telemetry → Link & messages. The list is volatile, reset with each new session, and read by the panel only. No payload, address, location, persistent identifier, command or new network destination is stored or added. See D-200-01.

The parser result source filter remains unchanged. The completed-gap snapshot deliberately excludes the chunk containing the recovery heartbeat, including any position message in that same chunk. A `NOT OBSERVED` result is therefore limited receipt evidence, not proof that the sender produced no data. The five-second connection rule is unchanged.

## Verification

- Focused JVM test run: `.\gradlew.bat :app:testDebugUnitTest --tests 'com.mamadrones.gcs.data.mavlink.MavlinkSessionTest' --tests 'com.mamadrones.gcs.presentation.dashboard.MavlinkDiagnosticsPanelTest' --no-daemon --max-workers=1 --console=plain` passed. Tests cover current-gap input/position, completed timing, recovery-chunk exclusion, three-event cap and session reset.
- Full software gate: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed. XML reports show 510 JVM tests across 80 suites, zero failures/errors/skips. Lint reports zero errors and 27 warnings. Several Gradle tasks were up-to-date; the full JVM task executed.
- A later `:app:assembleDebugAndroidTest` rebuilt the test APK after a screenshot scroll adjustment and passed. The data-preserving app/test APK updates succeeded on the USB phone. `adb shell am instrument -w -e class com.mamadrones.gcs.HeartbeatTimingUiTest com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner` passed `OK (3 tests)` twice, before and after that adjustment. The fixture opens no socket or vehicle command and does not change saved app data.
- The final synthetic recovered-gap screenshot at ignored `app/build/heartbeat-recovery-phase-200.png` was inspected. It visibly shows the start/recovery/duration and distinct input versus selected-position observations. The earlier capture clipped the second row under the footer; the final capture shows it. No live Cube gap was triggered or measured in this phase.

## Review, risks and next check

Review the new model, accumulator, panel projection, three test files and D-200-01. The diff adds no dependency, permission, authentication change, schema, secret, transport write or relaxed test. No raw telemetry or device address is retained. Live source identity remains unauthenticated; receipt continuity does not establish physical safety or a specific failed hardware hop.

On a future natural live gap, read the current and completed rows in the same session and compare them with TCP socket status and message observations. Do not alter Cube/radio settings or issue vehicle commands based on this display alone. A rollback would restore the pre-phase code at `c65b6b6` after reviewing the diff; that revision is a source baseline, not a certified release. No rollback was performed.
