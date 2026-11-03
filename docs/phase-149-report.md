# Phase 149 — software readiness review

Date: 2026-11-03. Reviewed revision: `3329b5fc2b241f775ef8f1541bb358c92c350fbe` on clean `main` aligned with locally recorded `origin/main`. Model: gpt-6.1-terra. The user requested a software readiness review while hardware work is on hold. This is an evidence review, not a release approval.

## Suitable demonstration scope

The current development build can demonstrate local mission drafting, waypoint editing/reordering/removal and jump navigation, explicit save/recovery, local route library and GeoJSON/GPX exchange. Field coverage produces a bounded, unsaved model from an outline, tool width and overlap; direction comparison, headland and modeled gaps are advisory. Offline drafting and coverage calculation do not require a vehicle. Map basemaps require configured MapTiler access and network; check that dependency separately for the demonstration. Do not present generated passes as an uploaded or executable mission, a trajectory free of obstacles, or measured soil coverage. See [README](../README.md) and [security boundaries](security.md).

Receive-only telemetry screens, connection diagnostics, parameter-file review and fixture data can be demonstrated with their data source clearly identified. CRC and heartbeat provide protocol/liveness evidence, not sender authentication. The app's mission upload, drive/arming commands, spray/hydraulic commands and app emergency stop are unavailable. A physical safety system is independent of this software.

## Evidence reviewed and limits

- [Phase 148](phase-148-report.md) records 478 fresh JVM tests on 2 November (zero failures/skips) and 211 software Android methods with passing statuses across batches and retries on 2–3 November. Four physical remote methods were excluded. Several original runs failed, stalled or failed Android health checks; the reconciled result is not one uninterrupted clean full-suite pass.
- This review inspected the current local XML summary (77 suites, 478 tests, zero failures/skips) and confirmed the main Phase 148 logs exist under ignored `app/build/phase148/`. The logs and screenshots are temporary build artifacts, not durable release evidence. No fresh test, lint, build, manual walkthrough, physical test or hardware connection was run in Phase 149.
- The latest commit's test-only helper change was inspected: it invokes the existing Android fullscreen-education handler during map-pixel and keyboard waits; original pixel, keyboard and Android health assertions remain. The prior screenshot showed the education prompt dimming the map; the final pass did not prove that the helper encountered it. The original Compose snapshot threading exception, Android System UI ANRs and Bluetooth service crashes remain unexplained. See [Phase 145](phase-145-report.md), [Phase 146](phase-146-report.md) and [Phase 148](phase-148-report.md).
- The user's Mission Planner screenshot from 21 October shows `ArduRover V4.7.1` on `CubeOrangePlus` in that connected session. The operator later reported the R12 telemetry lead on TELEM1; current live SERIAL1 readback and end-to-end Cube heartbeat/RC telemetry through T12 remain unverified. The older TELEM2 readback is historical. See [hardware integration](hardware-integration.md).

## Open actions before a customer release or physical operation

| Area | Status and next action |
| --- | --- |
| Owner review | Open: owner reviews the actual diff and defense brief, explains the system, tradeoffs and failure points in their own words, and completes teach-back. No owner review is inferred here. |
| Software acceptance | Open: choose a specific release candidate and record its artifact/revision, fresh required build/lint/UI results, a realistic manual route and import/export demonstration, screenshots and any accepted failures. Resolve or explicitly scope the intermittent emulator/Compose failures. |
| Security and dependencies | Open: review pinned dependencies, maintainers/sources and relevant risks; review permissions, network destinations and credential handling. Run the available secret checks with exact scope and inspect results; this review is not a complete secret or history audit. |
| Recovery and distribution | Open: establish a signed release artifact and a tested data-preserving upgrade/recovery procedure, including accounts/Keystore, routes, library and recovery copies. No known-good production rollback artifact or completed recovery drill is established; see [rollback guidance](ROLLBACK.md). |
| Cube/T12/R12 | On hold: with an independently secured bench, read current TELEM1/SERIAL1 settings and establish Cube-origin heartbeat and RC messages at the relevant points. Operator reports and a Bluetooth socket alone are not end-to-end validation. Do not write parameters during this review. |
| Vehicle and field | Open: separately approved integration and physical acceptance must establish actuator wiring, independent stopping, failsafes, actual positioning and coverage performance before operation. The 8 km/h value is an operator requirement, not verified enforcement. |
| Documentation | The README labels an old Phase 52–53 change “Current uncommitted change” even though the reviewed tree was clean. Correct that stale wording in a scoped follow-up; retain historical reports. |

The complete release checklist is in [TESTING](TESTING.md). Source commits and emulator results do not authorize deployment or machinery operation. The review updates the hardware record with a historical screenshot observation; it does not alter app code, dependencies, parameters or hardware. Commit/push require a separate request.
