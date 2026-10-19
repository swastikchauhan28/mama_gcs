# Current handover

Read this first, then the [project instructions](../AGENTS.md). Keep the newest session first; put detailed chronology in the linked phase report. Update status when evidence changes, without removing earlier failed-run evidence.

## 2026-10-19 - Phase 44 documentation baseline

### Done

- Phase 44 adds repository instructions, this handover, [decisions](DECISIONS.md), [testing](TESTING.md) and [rollback](ROLLBACK.md) guidance; see the [phase report](phase-44-report.md) for validation and scope. No app, build, dependency, test or hardware behavior is changed.
- Starting source baseline: `c0406127ec3e2c84af60c514531924dc777c0816` on `main`, clean and synchronized with `origin/main` at inspection. The implementation task left Phase 44 uncommitted; the user subsequently authorized committing and pushing this seven-document change. Use Git history and remote status for the resulting revision/synchronization, not the starting baseline.
- Implemented app scope: read-only supported MAVLink reception over Classic/BLE/UDP; diagnostics and explicit report exports; RC/PWM and local RC activity observation; online maps; local mission drafting/recovery/library/exchange; local parameter-file review; local accounts and encrypted account/audit storage. See [README](../README.md) for precise limits.
- Historical Phase 43 evidence: 380 JVM tests in 62 suites passed; the final targeted UI run passed 14 tests, including repaired PWM switching. Lint: 0 errors, 32 warnings. These tests are not rerun by Phase 44; [TESTING](TESTING.md) separates their scope from earlier full-suite evidence.
- Phase 44 documentation checks passed: seven-document scope, 112 relative links, one heading fragment, seven PowerShell examples parsed without execution, whitespace/conflict/fence checks and scoped obvious-secret-pattern review. These are documentation checks, not app or hardware tests.

### In progress

- Phase 44 documentation implementation and verification are complete. The commit/push follow-up is authorized; that request does not establish human diff review, release approval or hardware acceptance. No application implementation or hardware test is running. A subsequent technical phase needs its own bounded proposal and approval.

### Broken or risky

- Latest supplied physical bench screenshots show Classic bytes but no validated supported MAVLink messages; current Cube heartbeat/RC reception is not established. Root cause is unproven. Earlier user-reported reception and remote-only GATT continuity are not acceptance of this failing session.
- The final 14-test UI run passed its assertions, but a post-run Android system ANR was observed. Do not call it an error-free stability soak or a full current-suite run.
- Actual speed enforcement, physical stop, link-loss response, manual takeover and motor behavior remain unverified. Controls remain unavailable. Local accounts and CRC do not authenticate the vehicle.
- The accepted file's cruise target conflicts with the confirmed 8 km/h maximum and its failsafe settings do not establish a guaranteed stop. Preserve these recorded gaps; do not silently change parameters.
- No production-qualified artifact/recovery drill or owner teach-back is established. Deeper flow/data/threat/failure/defense records and release security/device testing remain future work, not completed by these documents.

### Next

- Propose a read-only bench diagnosis of the current serial stream: distinguish transport bytes, validated frames, selected-autopilot heartbeat and fresh RC_CHANNELS using existing diagnostics. First obtain current setup availability/authorization, not already-recorded model/baud/speed answers. No arming, parameter changes or actuator commands.
- Before a release claim, run the complete current software inventory on a healthy environment, complete scoped hardware acceptance, preserve evidence and complete the [release gate](TESTING.md).

### Do not touch

- Preserve the [accepted hardware/parameter record](hardware-integration.md). It retains Cube Orange family, Skydroid T12/R12 Multi Link V1.0, remote-name history, photo-based TELEM1 plus separate SBUS/RCIN, 57600-baud file baseline and the 8 km/h maximum. These are not independently verified physical behavior. Do not repeat old questions without a new conflict.
- Keep command paths unavailable; protect auth, trust filtering, storage and safety boundaries under [security](security.md) and [project instructions](../AGENTS.md).
- No raw telemetry or user secrets in Git; no app-data clearing/uninstall, destructive rollback, automatic commit/push or hardware operation. Preserve old reports and unrelated changes.
