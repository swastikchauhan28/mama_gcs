# Mama GCS working instructions

Apply the user's *Defend What You Ship: AI Field Guide v2* workflow. These repository instructions are portable; they do not depend on a PDF in Downloads or a particular machine's global instructions. Respect higher-priority instructions and the current request. This is a monitoring/local-planning development app, not approved rover-control software.

## Read first

1. [Current handover](docs/HANDOVER.md): latest status, evidence and next safe step.
2. [Security boundaries](docs/security.md) and, for hardware work, the [current hardware record](docs/hardware-integration.md). Later dated evidence in that record takes precedence over old phase requests for information.
3. [README architecture and feature scope](README.md), the relevant phase report, and [decisions](docs/DECISIONS.md).
4. [Testing checklist](docs/TESTING.md) before validation and [rollback guidance](docs/ROLLBACK.md) before risky edits or release work.

## Plan and document

- Check Git revision/status first and preserve unrelated work. Propose a bounded goal, scope, alternatives, risks and acceptance criteria for vague "next phase" requests; get approval before significant/risky implementation unless already approved.
- Draft meaningful decisions before editing code. Record actual known model/version or unavailable; do not invent historical rationale or approvals.
- Keep one logical change at a time. Explain non-obvious assumptions/invariants in comments. Update affected flow, data ownership/limits, threat/failure descriptions when behavior changes; use existing documents where suitable.
- Preserve phase reports as history and append dated corrections. End implementation tasks by updating the handover's Done, In progress, Broken or risky, Next and Do not touch sections. Read-only questions need not modify files.
- Keep alternatives and assumptions explicit: verified, user-reported, unverified, disproved or consciously accepted. Do not ask again for answers already retained in the hardware record unless new evidence conflicts.

## Protected boundaries

- Keep movement, arming, mode changes, mission upload/execution, spray, hydraulics and app emergency-stop commands unavailable until separately approved integration and safety work establishes the required evidence. A disabled UI or a boolean checklist is not a trusted command boundary.
- Never infer physical safety, identity, wiring, motor direction or speed enforcement from an open socket, BLE continuity, CRC, imported parameters, simulated data or passing software tests.
- Do not change Cube/radio/VESC parameters, flash firmware, send actuator commands or run physical disconnect experiments without specific authorization and a hardware-team-secured procedure. Read-only diagnostics must also be scoped to the identified setup.
- Require explicit review for material changes to local account crypto/auth/permissions, telemetry trust/source filtering, persistence/migrations, build configuration, production dependencies or external destinations. Phase 44 authorizes documentation only, not those future changes.
- Do not read/expose credentials unnecessarily or commit `local.properties`, keys, private exports, device identifiers or raw packet captures. Document dependency purpose/version/source/risk and credential handling without secret values. Ignore rules are not a secret audit.
- Do not weaken tests, checksum checks, safety gates or validation to obtain a pass. Do not dismiss Android ANR/crash dialogs to force UI acceptance.
- Do not clear/uninstall an operator's populated app, overwrite user data, reset Git history, force-push, deploy or operate hardware as an implied testing step. Follow the recovery limits below.

## Verify and hand off

- Use the risk-appropriate commands in [TESTING](docs/TESTING.md). Inventory current tests; report targeted/full coverage, skips, cached tasks, environmental failures and physical acceptance separately. Never turn old output into a new test claim.
- Inspect the full diff for scope, removed tests, weaker checks, assumptions, dependencies and secret exposure. Give the user a short review guide; do not claim they reviewed it without confirmation.
- Record exact commands, actual results and evidence locations in the phase report. UI changes need visual checks as well as assertions. Hardware facts require independent evidence, not fixture results.
- Commit/push only when requested. Keep commits logically focused and report their status. This agreement does not authorize deployment or hardware operations.
- Before customer release, complete the release gate in [TESTING](docs/TESTING.md), including a current owner-reviewed defense brief, teach-back, risk acceptance and a validated recovery path. Do not claim these human checks on the owner's behalf.
- Close with what changed/why, actual checks, remaining gaps, risks and next safe step. Scale documentation to the task; do not create empty copies of every guide template.
