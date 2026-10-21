# Decisions and alternatives

Record consequential choices before implementing them. Keep dated corrections; do not rewrite earlier outcomes. Existing phase reports remain the historical record. This file starts with Phase 44 and does not invent the reasoning behind earlier designs.

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
