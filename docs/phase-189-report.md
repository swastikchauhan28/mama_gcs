# Phase 189 — Autopilot message history in Diagnostics

Date: 2026-11-16. Baseline: `1d88427`, clean `main` worktree. Scope: software-only display change while hardware is unavailable.

## Goal and implementation

Diagnostics previously showed five recent `STATUSTEXT` messages plus an older warning. It now shows the repository's full bounded in-memory list (at most 50), newest first, with a warnings-only filter for reported severities 0–4. The heading distinguishes current, link-lost and historical retained messages, and shows filtered/retained counts. The filter does not delete messages or affect the Operate warning banner.

The selected autopilot's MAVLink `STATUSTEXT` is parsed and assembled, written to `VehicleRepositoryImpl`'s 50-message state list, projected by `ConsolePanels.statusTexts`, and rendered by `DiagnosticsScreen`. A new receive session clears the list. No data is persisted, no transport or permissions change, and no request or command is sent.

## Verification

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` with the existing temporary build-output init script, offline and one worker: passed in 10m 55s. JUnit XML: 501 tests, 0 failures. Lint XML: 0 errors, 20 warnings. This includes a new unit assertion for full list, filter, no mutation and link labels.
- Focused emulator UI and visual verification: blocked before test execution. Three fresh starts of the disposable API 34 emulator failed the required 30-second health preflight because Android displayed `Application Not Responding: com.android.systemui`. The second and third attempts followed no-wipe emulator restarts; the third had about 6.7 GB host memory free at launch after OneDrive quit. No Phase 189 instrumentation result or screenshot is claimed. The cause of Android's startup freeze remains unknown.
- Live Cube reception: unavailable and not tested. Synthetic messages establish display behavior only.

## Risks and next step

The list is a last-50-message view, not a durable log. Reported text can be false or stale, especially after link loss; no message does not prove the vehicle healthy. Recover a healthy emulator or use another isolated test device for the focused UI check and visual inspection. Verify a real Cube message when hardware becomes available. The user explicitly requested commit and push after this report was prepared; that does not close the visual or hardware acceptance gaps.
