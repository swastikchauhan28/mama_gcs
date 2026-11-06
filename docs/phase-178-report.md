# Phase 178 — Simulated heartbeat recovery timeline

## Goal and scope

Extend the Android-test-only link-health fixture with a recovery after a greater-than-five-second heartbeat gap. Verify the existing UI distinguishes a recent heartbeat after recovery from the preceding active gap and exposes the completed-gap evidence.

## Design and boundaries

The recovery scenario is immutable in-memory diagnostics data: a 6,100 ms completed interval, one completed long gap, and input observed before the recovery heartbeat. The instrumentation test transitions a single composed fixture through steady, delayed and recovered values. It opens no socket, reads no device, saves no data, changes no timeout, and sends no command. No production source is changed.

## Verification record

- Baseline revision: `cf68e93`, clean worktree before this phase.
- Planned checks: focused JVM timing-panel test, debug/test APK compilation and lint, then the disposable-emulator `HeartbeatTimingUiTest`. Review the recovered-state screenshot if the run passes. Hardware or network behavior is not implied.
- First emulator run: steady and delayed states passed, and Android reported no window ANR, but the recovered state correctly exposed the same `6100 ms` interval in both longest and latest rows. The assertion incorrectly expected one value, so it stopped before saving the screenshot. Assert exactly two rows and rerun; this is a fixture assertion error, not production behavior.
- Final verification: `:app:testDebugUnitTest --tests "*MavlinkDiagnosticsPanelTest" --rerun-tasks :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed in 6m 25s (97 scheduled tasks); lint completed without errors and retains 27 existing warnings. The final disposable-emulator run of `HeartbeatTimingUiTest` passed `OK (3 tests)` in 13.144 seconds, with no window ANR. The reviewed recovery screenshot at `app/build/phase-178/heartbeat-recovery-simulated.png` shows the explicit simulation label, Connected header and `ACCEPTED HEARTBEAT RECENT` card. The tests separately assert the completed-gap labels and both 6100 ms rows, which are below the screenshot fold.

## Remaining work and rollback

The recovery timeline proves only the supplied diagnostic projection. It cannot show that a live TCP peer, Wi-Fi network or Cube will recover in the same way. Review an inverse test/docs-only diff to roll back; do not reset data or alter hardware configuration.
