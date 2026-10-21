# Phase 47 — Plain-language Classic connection summary

## Goal and scope

The user requested a UI-only next phase while hardware is unavailable. Add one short explanation above the existing technical **Bluetooth / HC-05** receive checklist. The explanation must remain within the evidence already represented by `ClassicCheckStage`; it must not infer a physical fault, vehicle identity or control readiness. No Bluetooth, parser, source-filter, permission, storage, Cube/radio parameter or command behavior changes.

Starting source revision: `34c49d0f0436c8c863b554ed08302974581ea58d` on `main`. At start, Phase 46's `README.md`, `ClassicConnectionCheckUiTest.kt`, `docs/HANDOVER.md` and untracked `docs/phase-46-report.md` were already modified. They were preserved; this phase adds its own code, tests and records. [Decision D-047-01](DECISIONS.md#d-047-01-add-plain-language-classic-receive-status-without-changing-evidence) was drafted before the code change.

## Flow and limits

`ClassicBluetoothScreen` calls `ClassicConnectionCheck.assess` on the active link, connection and bounded `VehicleState`; `ClassicConnectionCheckPanel` renders the existing stage. Phase 47 adds a pure stage-to-text explanation in that panel. It does not read raw bytes or write state. The UI only renders the latest assessment; the five-second receive window is a display heuristic, not a failsafe. The explanation does not alter the detailed checklist, which remains the evidence view. Untrusted Bluetooth input still passes through the unchanged parser and source-selection path before it affects a stage.

The important distinctions remain: socket open is not telemetry; received bytes may be undecodable; some supported MAVLink is not a fresh selected-autopilot heartbeat; heartbeat/measurement receipt is not authenticated identity, sensor validity or safe control. Hardware is unavailable, so no current physical acceptance was attempted.

## Verification — 2026-10-21

- First build before the pure-function extraction: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` returned `BUILD SUCCESSFUL` in 14m 35s. It ran 381 JVM tests in 62 suites with 0 failures/errors/skips; both APKs compiled; lint reported 0 errors/32 warnings. The first sandboxed invocation could not access the existing Gradle wrapper cache, so the same command was run using the configured cache. The Android fixture compiled but did not run.
- A verified disposable `MamaPhase46` emulator under ignored `app/build/` was started for the focused UI fixture. It reached `sys.boot_completed=1`, but **Android System UI showed an ANR before installation or instrumentation**. No test was run, no dialog dismissed and no data cleared. The disposable emulator was stopped. This is an environmental visual-acceptance blocker, not a passing UI test or an app-test failure.
- To execute a software assertion despite that blocker, the summary mapping was extracted to a pure function and a JVM test was added for all ten enum stages. The same full Gradle command above was rerun after the final code change and returned `BUILD SUCCESSFUL` in 9m 16s. XML under `app/build/test-results/testDebugUnitTest/` reports **382 tests in 63 suites, 0 failures, 0 errors, 0 skips**. Both APKs built. `app/build/reports/lint-results-debug.xml` reports **0 errors, 32 warnings**. The new Android light/dark fixture compiled but still has no executed visual result.
- `git diff --check` found no whitespace errors. Existing LF-to-CRLF warnings appeared during the diff check. The working tree remains uncommitted; no push was requested.
- Final changed/untracked-file review found no removed or skipped tests, weakened receive checks, new dependencies, permissions, network destinations or hardware actions. A scoped obvious-credential-pattern scan covered nine changed/untracked files and found zero matches; it is not a whole-repository or history secret audit. The pre-existing Phase 46 changes are not part of the Phase 47 UI behavior.

## Open work and recovery

Run `ClassicConnectionPlainLanguageUiTest` on a healthy verified disposable emulator, inspect light/dark screenshots and post-run Android health, then decide whether the UI is visually accepted. Do not run synthetic fixtures on a populated phone. The complete Android UI inventory, current physical Cube/R12/T12 acceptance and customer release gate remain separate. If this copy proves misleading, review and remove the cohesive summary/test change; there is no data migration or hardware setting to reverse. No tested production artifact or physical rollback claim is made.
