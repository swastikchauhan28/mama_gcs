# Phase 177 — Simulated TCP/MAVLink link-health fixture

## Goal and scope

Exercise three meaningful diagnostics states without Cube hardware, a TCP server, a real network connection, or changes to user data: a recent selected-autopilot heartbeat, a heartbeat delayed beyond five seconds while decoder bytes continue, and a closed TCP session.

## Design and boundaries

`LinkHealthScenarioFixture` and its state definitions live only under `app/src/androidTest`. Each scenario constructs immutable in-memory `VehicleState` and `MavlinkDiagnostics` values, renders the existing `StationShell` and heartbeat-timing card, and makes no transport call. The assertions cover the top-bar link wording and timing-card wording together, so an open socket with a stale heartbeat cannot be confused with a closed session.

This is a regression fixture, not an operator-facing simulator. It does not add a debug screen to released builds, start or close real sockets, persist any route or setting, alter the five-second timeout, identify a Cube, authenticate a peer, prove packet loss, or issue MAVLink commands.

## Verification record

- Baseline revision: `044da83`, worktree clean before this phase.
- Planned checks: focused JVM projection test, debug/test APK compilation, lint, then the `HeartbeatTimingUiTest` on the disposable emulator. Record actual results after execution; no hardware result is implied by these checks.
- First emulator run: delayed-heartbeat case passed, but the combined steady/socket-loss case failed because the Compose test called `setContent` twice for one activity. This was a test-fixture lifecycle error, not production behavior; replace the second call with an update to the fixture's in-memory scenario state, then rerun.
- Second emulator run: the composition update rendered, and Android reported no window ANR, but the socket-closed assertion matched six legitimate em-dash rows. Narrow it to the merged `TCP socket, —` row and rerun; this is assertion specificity, not an application behavior failure.
- Third emulator run: the status and timing-card assertions reached the socket-closed state with no window ANR, but Compose did not expose the merged socket row as searchable text. Remove that implementation-detail assertion; the independently asserted header and timing-card states already establish the intended state projection.
- Final verification: `:app:testDebugUnitTest --tests "*MavlinkDiagnosticsPanelTest" --rerun-tasks :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed in 9m 18s. It rebuilt all 97 scheduled tasks. Lint completed without errors (the existing 27 warnings remain). The final disposable-emulator run of `HeartbeatTimingUiTest` passed `OK (2 tests)` in 7.354 seconds; Android reported no window ANR. The delayed-heartbeat screenshot was reviewed at `app/build/phase-177/heartbeat-timing-simulated.png` and correctly labels its simulation, link-loss header and ongoing heartbeat gap.

## Remaining work and rollback

The fixture establishes only rendering and state-projection behavior for its supplied values. Real TCP bridge observation remains separately required to assess live reliability. Roll back by reviewing the small test/docs-only inverse diff and rebuilding; do not reset user data, change Cube settings, or rewrite Git history.
