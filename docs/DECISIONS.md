# Decisions and alternatives

Record consequential choices before implementing them. Keep dated corrections; do not rewrite earlier outcomes. Existing phase reports remain the historical record. This file starts with Phase 44 and does not invent the reasoning behind earlier designs.

## D-050-01: Decode RADIO_STATUS only into a radio diagnostic channel

- Date: 2026-10-23.
- Status: user approved radio-status diagnostics; drafted before implementation. Verification belongs in the Phase 50 report.
- AI model/version: exact runtime identifier unavailable.
- Baseline: `7535d0d17b1c088b82b823e5fddec2a44749a730` on `main`; pre-existing uncommitted handover and 23 October test-report edits are preserved.
- Context: QGroundControl observed MAVLink `RADIO_STATUS (109)` on a T12 Bluetooth session while Mama GCS received bytes but decoded no supported messages or Cube heartbeat. The current parser does not know ID 109. MAVLink's generated common header defines a 9-byte payload and CRC extra 185; those protocol facts do not establish the current physical stream identity or Cube reachability.
- Alternatives: (1) leave the app unchanged and use QGC for radio observation; (2) add `RADIO_STATUS` to the vehicle-message router, risking radio data being conflated with selected-autopilot evidence; (3) checksum-validate ID 109 into a separate radio-only parse result and bounded session-memory diagnostic.
- Choice: option 3. Show a count, last receipt age, reported source and raw/device-specific fields in existing decoder UI. Do not route it to vehicle state, select an autopilot, refresh heartbeat/RC evidence, persist it or add it to exports. Signed candidates remain rejected.
- Reason: identify a valid radio-generated MAVLink frame without claiming that Cube-origin messages crossed the radio link.
- Trade-off: extra parser/UI surface and a bounded memory snapshot; radio fields may be vendor-specific and a valid CRC is not sender authentication. A radio-only stream must still display missing Cube heartbeat.
- Verification: MAVLink 1/2 valid and corrupt fixtures, length/signing cases, session isolation and heartbeat/RC gate tests, UI text tests, build/lint and disposable-emulator visual inspection if healthy. Physical proof remains a separate secured read-only test.
- Rollback: revert the isolated parser result, diagnostic fields/UI and tests after diff review; no schema, migration, dependency, secret or hardware setting changes.
- Revisit if: a same-link QGC capture proves Cube heartbeat while Mama GCS does not, or the diagnostic wording misleads operators about vehicle health.

## D-048-01: Classify undecoded Classic receive evidence without identifying a physical fault

- Date: 2026-10-22.
- Status: approved by the user for Phase 48 before implementation; verification belongs in the phase report.
- AI model/version: exact runtime identifier unavailable.
- Baseline: `ad895537f27893465d566260f27a06dc23a4e7db` on clean `main`.
- Context: the current T12/R12/Cube bench session has fresh Classic bytes and MAVLink-like start-byte candidates but zero supported decoded messages plus checksum failures. Existing decoder counters are accurate but require the operator to interpret several values before deciding what evidence to obtain next.
- Alternatives: (1) leave the existing counters and provide hardware advice only in chat; (2) assert a likely baud, UART or wiring fault from counter patterns; (3) classify only the observable byte/frame evidence and show a bounded next safe check plus the ambiguity that remains.
- Choice: option 3. Add a pure, session-local diagnosis category for the existing `WAITING_MAVLINK` Classic stage and render explicit diagnostic copy in the existing panel. No transport, parser, source selection, persistence, parameter, permission or command behavior changes.
- Reason: make the current failure actionable while preserving the distinction between received bytes, validated MAVLink and physical wiring/configuration evidence.
- Trade-off: counters cannot distinguish wrong baud, wrong receiver UART, TX/RX crossover, power/ground continuity, radio bridge configuration, electrical noise or unsupported protocol. The app must not select one as the cause.
- Verification: unit tests for deterministic pattern classification and UI text, full build/JVM/lint checks, and a focused disposable-emulator visual check only if the emulator is healthy. No phone/vehicle test or hardware change is part of this phase.
- Rollback: remove the classifier and panel block as one cohesive UI-only change after review; no data migration, local setting or hardware configuration is involved.
- Revisit if: a validated hardware capture shows that the labels are misleading, parser semantics change, or a later approved integration provides authenticated receiver-port metadata.

## D-047-01: Add plain-language Classic receive status without changing evidence

- Date: 2026-10-21.
- Status: Drafted before Phase 47 implementation under the user's UI-only request; verification belongs in the phase report.
- AI model/version: exact runtime identifier unavailable.
- Baseline: `34c49d0f0436c8c863b554ed08302974581ea58d` on `main`, with the unrelated uncommitted Phase 46 UI-test and documentation work preserved.
- Context: the Classic checklist already distinguishes socket, bytes, validated MAVLink, selected-autopilot heartbeat and measurement receipt, but its technical labels are difficult to interpret during a hardware-unavailable session.
- Alternatives: (1) leave the technical checklist unchanged and explain it only in chat; (2) add a separate troubleshooting flow, which duplicates state and navigation; (3) add one short summary derived only from the existing checklist stage, keeping the detailed evidence visible.
- Choice: option 3. Add a plain-language line to the existing Classic panel and synthetic UI assertions for every stage. Do not change assessment logic, telemetry trust/source filtering, storage, transport, parameters or commands.
- Reason: make the next safe interpretation immediately visible without treating an open Bluetooth socket or raw bytes as proof of Cube telemetry.
- Trade-off: the summary cannot identify the failing physical hop or authenticate a vehicle; its wording must remain narrower than the underlying evidence. The panel grows slightly and needs light/dark visual review.
- Verification: fresh JVM/build/lint checks, targeted disposable-emulator UI assertions and visual inspection when the emulator is healthy; no phone or hardware test in this phase.
- Rollback: remove only the summary line and its test after reviewing the diff; no persisted data or hardware setting changes.
- Revisit if: operators misread a stage, assessment semantics change, or physical evidence contradicts the displayed summary.

## D-045-01: Show RC receipt as a separate Classic receive observation

- Date: 2026-10-21.
- Status: Phase 45 implemented under the user's “next phase” request; software verification recorded in the [phase report](phase-45-report.md). Emulator visual and physical acceptance remain open.
- AI model/version: exact runtime identifier unavailable.
- Baseline: `ced11d690dbdad392bdb4b9ab465266feb59c3d7` on clean `main`.
- Context: the latest bench evidence has Classic serial bytes but no validated supported MAVLink or RC values. The current connection checklist groups RC input with other measurements, so a fresh attitude/battery message can make the overall stage `RECEIVING` while RC remains absent.
- Alternatives: (1) rely only on the existing RC & PWM screen, which is accurate but requires a separate screen and makes the receive-chain checklist less explicit; (2) add a new decoder or protocol heuristic, which broadens telemetry trust behavior without evidence that the serial stream is MAVLink; (3) expose a separate, session-local RC receipt indicator based on existing accepted-message and freshness evidence.
- Choice: option 3. Keep the parser, source selection, protocol handling, transport and command boundaries unchanged. The new indicator requires current-session Classic bytes, a fresh selected-autopilot heartbeat, a fresh accepted `RC_CHANNELS` observation and a fresh RC state timestamp. It remains false for stale/disconnected/other-source evidence.
- Reason: distinguish a functioning Cube receive path from the narrower RC-channel path without claiming stick movement, physical receiver wiring or safety. A boolean is sufficient because this checklist reports only receipt; numbered values remain on RC & PWM.
- Trade-off: the indicator may be `NOT CONFIRMED` when the Cube does not stream `RC_CHANNELS`, even if SBUS is functioning. The app does not request a stream rate or diagnose the specific missing hop.
- Verification: unit tests for session/freshness/source gates and report output, Android fixture test and visual review if an emulator is healthy; fresh JVM/build/lint checks. No physical acceptance without a separately scoped secured bench check.
- Rollback: revert this cohesive UI/model/report change after reviewing the diff; no schema, persisted data, Cube setting or radio change is involved.
- Revisit if: a validated Cube stream shows RC messages but the indicator remains absent, or a later approved control design needs stronger authenticated evidence.

## D-044-01: Consolidate working records without changing the app

- Date: 2026-10-19.
- Status: approach approved by the user before implementation; implementation and checks are tracked in the [Phase 44 report](phase-44-report.md).
- AI model/version: exact runtime identifier unavailable; not inferred from the generic Codex identity.
- Context: the user adopted *Defend What You Ship: AI Field Guide v2*. Global instructions exist on the current machine, but a fresh repository checkout lacks a concise handover, decision index, common verification checklist and recovery procedure. Existing reports already preserve substantial implementation and test history.
- Baseline: `c0406127ec3e2c84af60c514531924dc777c0816`; worktree clean at task start. This is a source baseline with scoped software evidence, not a production/hardware-approved release.
- Alternatives:
  1. Rely only on chat/global instructions: minimal files, but new checkouts and other machines lose essential context.
  2. Create every template from the guide and copy all historical material: comprehensive-looking, but duplicates facts and increases contradictory records and maintenance cost.
  3. Add a repository entry point plus four focused working records; link existing reports: portable, auditable and smaller, but deeper flow/data/threat/defense work remains explicit future work.
- Choice: option 3. Add root `AGENTS.md`, `HANDOVER.md`, this decision log, `TESTING.md`, `ROLLBACK.md`, a phase report and a README entry. Reuse the existing architecture description, security boundaries, hardware record and phase/test reports.
- Reason: satisfy the approved documentation baseline without changing runtime behavior or pretending that documents resolve the current undecodable hardware stream.
- Trade-off: ongoing handover maintenance is required; this phase does not complete the guide's entire release gate or certify the owner's understanding.
- Verification: documentation links, PowerShell example syntax, whitespace, source/evidence consistency and a Markdown-only changed-file allowlist. Do not rerun app/hardware tests merely for these documentation changes; label existing results historical.
- Revisit if: a new subsystem, storage migration, release candidate or actual control integration needs a dedicated flow, data, threat, dependency or defense record. Obtain approval for that next bounded scope.

## Future entry format

Use an ID and date, status/approval scope, known model/version (or unavailable), context, affected behavior/files, two or more real alternatives, choice/reason, trade-offs, assumptions, verification, rollback implications and a revisit condition. Reference the relevant phase/bug report rather than duplicating its chronology. Record retrospective reasoning explicitly as retrospective; never attribute it to an earlier developer without evidence.
