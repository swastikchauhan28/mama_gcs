# Inspector timestamp investigation — 5 November 2026

## Symptom and scope

In a prior five-minute phone observation, the inspector intermittently displayed `TIMESTAMP UNAVAILABLE` while the accepted heartbeat count increased. The Cube's reported identity is not authenticated and no command or parameter operation was performed. The separate intermittent heartbeat timeout remained unproven.

## Cause and change

The screen retained a wall-clock reading refreshed every second. When a newer immutable diagnostics snapshot arrived between ticks, its receipt timestamp could exceed that stale display clock, causing the panel's existing validation to reject the age. The screen now reads the clock when the diagnostics snapshot changes as well as on its periodic tick. It retains validation for genuinely future, invalid and out-of-session values. The elapsed-time session heartbeat timeout remains unchanged. D-175-01 records the choice and limitations.

## Verification

- Starting revision `a7453904c4e20b9fe6d7045fa79320648c799d71`; initial worktree clean. No commit or push requested.
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 97 tasks, 35 executed and 62 up-to-date. JVM XML totals 491 tests, zero failures/errors/skips. Lint passed; task output was up-to-date for report generation.
- Phone UI fixture `MavlinkInspectorUiTest#newTelemetryBetweenClockTicksUsesFreshDisplayTime`: `OK (1 test)`. An in-memory clock and telemetry snapshot reproduce the between-tick case and retain rejection of a future observation; no hardware commands or saved app data are changed.
- The user authorized a data-preserving app update and live read-only TCP test. The phone reported the update succeeded. During about five minutes, 47 sampled status checks showed Connected. In 15 samples with the heartbeat rows visible, displayed ages were 0s (13) or 1s (2), and none said TIMESTAMP UNAVAILABLE; accepted heartbeat counts increased from 148 to 368 between the first and last visible samples (about 236 seconds). In 32 samples the heartbeat rows were not visible and were recorded as unavailable, not as successful age checks. Evidence is local temporary file `mama_gcs_heartbeat_postfix_20261105.csv`; it is not a continuous packet log and may be deleted by the OS.

## Remaining risks and recovery

Sampled checks do not establish individual heartbeat intervals or prove the prior brief link-loss warning is fixed. The display still uses wall clock, which can change; it is not a safety or trust decision. The existing five-second liveness threshold, source filtering, physical controls and unauthenticated TCP behavior are unchanged. To revert, review the inverse diff and restore the inspector screen from baseline revision above, then rebuild and retest; do not reset user data or Git history. Baseline is a recovery reference, not a verified release.
