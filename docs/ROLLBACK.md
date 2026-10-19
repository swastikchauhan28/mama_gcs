# Rollback and recovery guidance

This is a procedure, not an executed or validated rollback. Obtain explicit authorization for the exact target/action. See [security](security.md), [testing](TESTING.md) and the [handover](HANDOVER.md). A Git revert restores source behavior only; it cannot restore physical safety, firmware or application data.

## Baseline and known limits

- Phase 44 started from clean `main` at `c0406127ec3e2c84af60c514531924dc777c0816`. It is the pre-documentation source baseline, with the scoped evidence in [Phase 43](phase-43-report.md), not a production-qualified release. No exact known-good deployed artifact or full recovery drill is established.
- Phase 44 changes only `AGENTS.md`, `README.md`, `docs/HANDOVER.md`, `docs/DECISIONS.md`, `docs/TESTING.md`, this file and `docs/phase-44-report.md`. It has no app data migration, APK change, remote configuration or feature flag to undo.
- Do not revert the earlier Phase 43 application feature merely to undo Phase 44 documentation. Do not treat older source as automatically more compatible or safer.

## Before any recovery

1. Have the hardware team secure/isolate the setup independently of the app if hardware is present. The app cannot command or confirm a physical stop. Do not unplug or power-cycle a moving system as a software recovery step.
2. Identify the symptom, affected revision/artifact/device and narrow recovery target. Record `git status --short --branch`, `git rev-parse HEAD` and `git log -5 --oneline`. Separate pre-existing user changes and secrets; do not automatically stash, overwrite or delete them.
3. Preserve needed user data through supported, authorized procedures. Do not copy credentials or arbitrary private app stores into Git/logs. Record what cannot be backed up and stop if required data cannot be protected.
4. Inspect the proposed inverse diff and all later changes that depend on it. Agree on expected behavior and [verification](TESTING.md) before acting.

## Source-only documentation recovery

- If uncommitted: review the seven-file Phase 44 scope, then reverse only this task's edits with a reviewed patch under authorization. Newly created files may be removed only after confirming they contain no later/user work. Do not restore the whole workspace.
- If committed: identify the actual Phase 44 commit, inspect it, and prefer a new inverse commit that preserves history. A scoped procedure is `git show --stat <verified-phase44-commit>`, then authorized `git revert --no-commit <verified-phase44-commit>`, review the inverse diff and rerun documentation checks before an authorized commit/push. Replace placeholders deliberately; do not paste an assumed hash. Stop on conflicts rather than resolving by discarding content. Do not perform this on a dirty worktree with overlapping user edits.
- No `reset --hard`, force push, blanket checkout/restore or broad recursive deletion. No app reinstall or data clearing is necessary for documentation recovery.

## App/data recovery for future code releases

- First identify a compatible, tested artifact or corrective forward build, its source revision, package identity, signing certificate, version and supported data schemas. Never infer safety from a successful build alone.
- Current [build configuration](../app/build.gradle.kts) is a development setup; a production release-signing/recovery pipeline is not established. Do not use forced downgrade, uninstall or clear-data commands as a routine repair. A replacement install is not proof of schema compatibility and may fail certificate/version checks.
- Local account/audit storage depends on an Android Keystore key. Backup is disabled; copying an encrypted database does not make its key portable. Uninstall/data clearing can destroy routes, recovery copies, account access and local history. Forgotten-admin recovery, key migration/rotation and audit export are not implemented. Preserve the installation and escalate rather than reset it silently.
- Where supported and authorized, explicitly save/export each needed route and verify the files can be read in a disposable compatible test installation. GeoJSON and GPX are route exchange, not complete app backup; GPX does not preserve the local boundary. Protect unsaved drafts, separate library routes and externally saved files individually. Parameter review is in memory; retain the operator's original source file independently.
- Exercise upgrade/rollback compatibility on disposable synthetic data first: accounts/session access, saved routes/library/recovery, settings, BLE history, supported imports and explicit error behavior. No destructive drill on the operator's real installation. If a required data-preserving recovery cannot be demonstrated, that is a release blocker.
- There is no implemented production remote kill switch or general rollback feature flag. Physical safety must not depend on restoring an APK or on network availability.

## After recovery

For a documentation-only inverse, validate links, command syntax, whitespace and exact file scope. For code/data changes, run the required build/JVM/UI and manual paths for that change, confirm preserved data and unchanged unavailable-command boundaries, and perform separately authorized hardware checks only when needed. Record exact revision/artifact, outcome, failures and remaining limitations in the handover/phase report. A rollback is not complete merely because Git applied the patch.
