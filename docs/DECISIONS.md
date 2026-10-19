# Decisions and alternatives

Record consequential choices before implementing them. Keep dated corrections; do not rewrite earlier outcomes. Existing phase reports remain the historical record. This file starts with Phase 44 and does not invent the reasoning behind earlier designs.

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
