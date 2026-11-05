# Phase 176 — Heartbeat timing diagnostics

## Goal and scope

Provide a bounded, in-memory timing summary for the selected autopilot's accepted heartbeat and incoming data, so an operator can distinguish a heartbeat gap from a TCP socket that remains open. This is read-only observation. It does not alter the timeout, decode acceptance, transport behavior, Cube configuration or vehicle controls.

## Design and boundaries

The existing receive coroutine owns the accumulator. It timestamps chunks and accepted, selected-source heartbeat results using the session's monotonic clock. Fixed-size fields contain the latest heartbeat time, completed interval, longest interval, count of completed intervals greater than five seconds, count with input observed after five seconds but before the recovery chunk, and the latest long-gap input result. The current age and current gap are derived on screen. An interval is counted when the next accepted heartbeat arrives; the first heartbeat and an ongoing or session-ending gap are not completed intervals. Missing, before-session or backward elapsed times clear the interval anchor; the screen also rejects negative session start times. A new session resets the counters. No raw packets, peer addresses, positions or per-event history are persisted. Counters are saturating; timestamps are in-memory elapsed times, not calendar times.

The screen combines the separately observed TCP socket status with timing data; they are not an atomic snapshot. A decoder input chunk is not proof of a heartbeat. Completed-gap input evidence excludes the recovery chunk, so lack of evidence does not prove no earlier data arrived, particularly with equal receipt times. Gaps do not establish which physical or software hop caused them, authenticate the source or establish operational readiness. See D-176-01.

## Verification record

- Baseline revision `009ef5cdb5ba5ceeb4d8a4cf910f6741ddfa3b01`, initial worktree clean.
- Targeted `:app:testDebugUnitTest --tests '*MavlinkSessionTest' --tests '*MavlinkDiagnosticsPanelTest'`: 29 tests, zero failures/errors/skips. This was before the final addition of the TCP socket row; the full rerun below covers the final version.
- First broad build failed at Android test compilation because the new fixture imported a nonexistent `assertExists` symbol. The test-only import was corrected; this failure is not treated as a passing check.
- Final `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 97 tasks (28 executed, 69 up-to-date); 494 JVM tests across 79 suites, zero failures/errors/skips. Lint: 0 errors and 27 warnings. Simulated session tests use a TCP-labelled in-process fake transport, not a real TCP bridge.
- The first headless emulator start was stopped after Android failed to complete startup while free host memory fell to approximately 317 MB during lint. The same documented disposable AVD was restarted after the build without wiping data. `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.HeartbeatTimingUiTest com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`: `OK (1 test)` with a synthetic in-memory fixture; Android reported no window ANR since boot. The simulated screenshot at ignored `app/build/phase-176/heartbeat-timing-simulated.png` was visually inspected. This is one fixture and not a complete Android regression or live Cube test.

## Remaining work and rollback

The continuous summary is not a raw event trace or evidence that the user's live TCP heartbeat problem is fixed. Independent live observation is separately required. Recovery reference is the baseline revision above; review an inverse diff and rebuild rather than resetting user data or Git history. No commit, push, vehicle command or hardware change is authorized by this phase.
