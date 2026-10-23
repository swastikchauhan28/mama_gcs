# Current handover

Read this first, then the [project instructions](../AGENTS.md). Keep the newest session first; put detailed chronology in the linked phase report. Update status when evidence changes, without removing earlier failed-run evidence.

## 2026-10-23 — Phase 53 home-marker visual gate accepted on disposable emulator

### Done

- The focused Phase 52 home-marker Android test passed `OK (1 test)` on the disposable Android 34 emulator from the moved Desktop checkout. Its synthetic screenshot was visually inspected: a white H is legible on the purple home marker, separate from the green vehicle point. Post-test Android had no ANR/error dialog. See [Phase 53](phase-53-report.md) for the exact command and ignored evidence paths.

### In progress

- Physical Cube-origin heartbeat localization and independent owner/release gates remain open. The Phase 52/53 changes are uncommitted; no commit or push was requested.

### Broken or risky

- Earlier Android System UI ANRs remain recorded. This one offline fixture at one emulator size does not prove appearance across devices, live telemetry or physical safety. The H nearly reaches the purple circle edge. The original checkout was moved to `C:\Users\swastik\OneDrive\Desktop\mama_gcs`; untracked `desktop.ini` was not touched.

### Next

- Keep the monitoring-only boundary. Await the hardware team's secured Cube-heartbeat trace; before any release, complete independent owner review, recovery and physical acceptance. If the home marker is revised further, rerun its pixel/expiry test and inspect a new screenshot.

### Do not touch

- Do not infer hardware acceptance from the emulator, change Cube/radio settings, enable vehicle commands, clear a populated app, or commit/push without a separate request.

## 2026-10-23 — Phase 52 visual-gate completion attempt, emulator blocked

### Done

- Retried the focused home-marker acceptance on the disposable Android 34 emulator. The first boot showed an Android System UI ANR; the second did not complete Android boot. Post-run file timestamps indicate the second boot's `-wipe-data` reset the **disposable AVD's** data despite a separate `-datadir` argument. Neither task-started emulator dismissed an ANR. See the [Phase 52 follow-up](phase-52-report.md#23-october-follow-up--requested-visual-gate-completion-attempt).

### In progress

- The post-change purple-plus-white pixel test and screenshot visual review remain unrun; the Phase 52 icon-size change is still provisional. Hardware-team Cube-heartbeat localization and owner/release gates remain open.

### Broken or risky

- The disposable emulator is currently not a healthy UI-test environment; its data may have been reset by the second boot. Existing JVM/build/lint results do not prove a rendered H, and the earlier weaker UI pass predates the change. No physical hardware or operator phone was tested.

### Next

- Restore a healthy disposable emulator, then run only the focused home-marker test and inspect its synthetic screenshot. If the H fails, investigate the MapLibre symbol path without relaxing assertions.

### Do not touch

- Do not dismiss Android ANRs, repeat the `-datadir`/`-wipe-data` combination without verifying its effect, clear a populated app, use a physical phone as a substitute, change hardware settings or enable controls. Do not commit/push without a separate request.

## 2026-10-23 — Phase 52 home-marker legibility, visual acceptance pending

### Done

- Enlarged the local in-memory H icon on the offline map and added a focused Android assertion for both purple marker and white center pixels. The prior Phase 51 screenshot showed a purple point but no discernible H. Fresh post-source-change JVM/build/lint checks passed: 389/389 JVM tests, both debug APKs built, lint 0 errors/28 warnings. See [Phase 52](phase-52-report.md).

### In progress

- Post-change pixel test and screenshot visual review remain unaccepted. The disposable emulator repeatedly showed an Android System UI ANR before that test could start; no dialog was dismissed. Hardware-team Cube-origin heartbeat localization and owner/release review remain open.

### Broken or risky

- The H-size adjustment is provisional until rendered on a healthy emulator. A pre-change, weaker white-pixel test passed once, but its screenshot was unavailable afterward and it did not verify purple pixels. It cannot establish post-change visual acceptance. The 28 lint warnings remain. No physical hardware was tested.

### Next

- Run the focused Phase 52 offline-map test on a healthy disposable emulator, inspect its screenshot for H legibility at the purple home point and verify freshness removal. If the icon is still absent, diagnose MapLibre symbol rendering without relaxing the test. Independently await the hardware team's secured heartbeat trace.

### Do not touch

- Do not bypass/dismiss an emulator ANR, use a populated phone as a test substitute, alter Cube/radio wiring or settings, or enable vehicle commands. Do not commit/push without a separate request.

## 2026-10-23 — Phase 51 software-only lint cleanup

### Done

- Audited the prior 32 lint warnings and removed four source-level warnings through Compose optional-`modifier` ordering and the existing KTX bitmap helper. Fresh lint: 0 errors/28 warnings; fresh JVM: 389/389 passed; debug and Android-test APKs built. Focused read-only-emulator UI batches passed 9/9, 23/23 and 1/1. See [Phase 51](phase-51-report.md) for exact commands, warning categories and ignored evidence paths.

### In progress

- Hardware-team Cube-origin heartbeat localization, remaining lint disposition and independent owner/release review remain open.

### Broken or risky

- The local-style map screenshot shows the purple home point, but the small white H glyph is not discernible, so that visual detail is not accepted. The 28 remaining warnings include dependency/toolchain version notices and two policy/configuration warnings. None was suppressed. No physical hardware was tested.

### Next

- If UI polish is pursued, investigate the H glyph with a targeted visual test; otherwise await the hardware team's secured read-only heartbeat trace before repeating a phone-side telemetry check.

### Do not touch

- Do not upgrade dependencies, change Bluetooth permission/orientation policy, touch Cube/radio settings or wiring, or enable vehicle commands as part of lint cleanup. Do not commit/push without a separate request.

## 2026-10-23 — Phase 50 radio-status diagnostics

### Done

- Added CRC-validated MAVLink `RADIO_STATUS` (ID 109) parsing and a separate, session-scoped radio diagnostic. The app now labels radio-only reception without treating it as a Cube heartbeat, RC data or vehicle admission. See [Phase 50](phase-50-report.md).
- Final fresh JVM run: 389 tests in 64 suites, 0 failures/errors/skips. Debug APK, Android-test APK and lint passed before a JVM-test-only export-boundary addition; final focused emulator run passed 5/5 UI tests, and a related screen batch passed 27/27. Synthetic radio screens were visually inspected. Exact commands and evidence locations are in the phase report.

### In progress

- The hardware team is localizing where the Cube-origin heartbeat stops. Independent human review and release gates remain open.

### Broken or risky

- Physical Cube/R12/T12/Bluetooth behavior was not retested in this phase. A valid radio frame is unauthenticated radio evidence, not proof of vehicle telemetry, RC reception or safe operation. Lint's 32 warnings were not individually dispositioned.

### Next

- Obtain a secured read-only trace at Cube TELEM2, R12 UART2 and the T12 phone-side stream; compare valid Cube heartbeats at each hop. Keep vehicle controls unavailable.

### Do not touch

- Do not change wiring or Cube/radio parameters, run actuators, or treat radio-only status as hardware acceptance. The user separately authorized committing and pushing this phase on 23 October.

## 2026-10-23 — blank draft persisted across app restart

### Done

- With operator approval, created a new two-point synthetic local route from an empty draft on the read-only disposable emulator, saved it, force-stopped only Mama GCS, relaunched it and reopened Plan. The app process ID changed `4406` → `5208`; the two-point count, 15,724 m estimate and **No unsaved changes** remained. Android showed no ANR/crash dialog. See the [23 October follow-up](test-report-2026-10-23.md#blank-draft-and-process-restart-follow-up--23-october-2026).

### In progress

- Independent human operator acceptance and hardware-team Cube-origin heartbeat localization remain open.

### Broken or risky

- Synthetic emulator persistence does not establish geospatial safety, Cube telemetry, physical control acceptance or production readiness. Waypoint 2 was not separately re-inspected after restart.

### Next

- Await the secured, read-only Cube heartbeat trace across TELEM2, R12 UART2 and T12 Bluetooth; keep the monitoring-only boundary. If release work resumes, obtain independent operator review and the remaining defense/recovery gates.

### Do not touch

- Do not change Cube/radio settings, wiring, actuators or populated phone data to advance this software check.

## 2026-10-23 — manual local-route walkthrough

### Done

- On the read-only disposable emulator, manually reordered the two points in the existing saved fixture, saved, navigated away and reopened Plan, exported GeoJSON to local Downloads, previewed and—after explicit operator approval—imported the same file, then saved again. The two coordinates and their order matched the export. Route review and dark/light layouts were visually inspected; upload/start remained unavailable. The final Android window check showed no ANR/crash dialog. See the [23 October test report](test-report-2026-10-23.md#manual-local-route-walkthrough--23-october-2026).

### In progress

- Hardware-team Cube-origin heartbeat localization and independent human operator acceptance remain open.

### Broken or risky

- This reused an existing two-point fixture, reopened the Plan screen rather than restarting the app process, and did not validate blank-draft creation or physical telemetry. The emulator export is local test data, not a vehicle mission.

### Next

- Await the secured read-only heartbeat trace at Cube TELEM2, R12 UART2 and phone-side T12 Bluetooth. If needed for release, separately have an operator complete a blank-draft and process-restart path.

### Do not touch

- Do not alter Cube/radio settings, wiring, actuators, populated phone data or the monitoring-only command boundary to advance this test.

## 2026-10-23 — broad software regression

### Done

- Fresh JVM run: 383/383 passed, 0 skipped. Debug/test APK builds and lint passed (0 errors, 32 warnings). On a read-only emulator, both software-only Android batches passed: 7/7 map/BLE-report and 121/121 other tests, with no ANR/crash dialog before or after. Original-resolution map captures and a disconnected app-launch screenshot were inspected. See the [23 October test report](test-report-2026-10-23.md).

### In progress

- Physical Cube-origin heartbeat localization and an independent manual operator path remain unverified.

### Broken or risky

- Four physical BLE-remote methods were excluded, and emulator tests do not verify Cube/R12/T12 telemetry or physical safety. The full software result is not production readiness.

### Next

- Have the hardware team trace Cube heartbeat read-only through TELEM2 → R12 UART2 → T12 Bluetooth on the identified setup; then compare Mama GCS. Keep physical controls unavailable.

### Do not touch

- No Cube/radio settings, wiring, actuators, populated phone data, production secrets or historical test evidence should be altered as a shortcut.

## 2026-10-23 — Phase 49 emulator acceptance completed

### Done

- On a read-only disposable emulator, the focused `ClassicConnectionCheckUiTest` passed all 3 tests after correcting a test-only Compose semantics assertion. Light/dark diagnosis-card screenshots were visually inspected; pre/post Android window checks showed no ANR/crash dialog. The earlier ADB block and first failed assertion remain in the [Phase 49 report](phase-49-report.md).

### In progress

- Physical Cube-origin heartbeat tracing remains separate; synthetic UI acceptance does not resolve the hardware telemetry path.

### Broken or risky

- The full Android suite, manual installed-app path and physical acceptance were not run in Phase 49. No rover-control capability is approved.

### Next

- Perform the hardware-team-secured read-only comparison at Cube TELEM2, R12 UART2 and the phone-side T12 Bluetooth link. Consider radio-only `RADIO_STATUS` diagnostics only after separate review.

### Do not touch

- Do not change Cube/radio parameters or wiring, run actuator commands, clear the populated phone, or interpret synthetic test data as physical proof.

## 2026-10-23 — T12 radio-status comparison

### Done

- Reconciled the 22 October QGroundControl and Mama GCS screenshots in the [hardware record](hardware-integration.md#23-october-2026--phone-side-comparison-and-next-isolation-test). QGC parsed `RADIO_STATUS (109)` from component 68 on the tested Bluetooth link; the operator did not see a Cube `HEARTBEAT (0)`. Mama GCS's separate Classic session had 126,757 bytes but zero supported decoded messages. Its parser intentionally does not decode `RADIO_STATUS`, so those results are compatible and do not identify a failed hardware hop.
- Recorded the operator's R12 UART2, crossed TX/RX, 57,600 baud and forwarding reports as reports, alongside the earlier live Cube SERIAL2 readback. No app or hardware behavior changed.

### In progress

- Localize where Cube-origin heartbeat stops using a secured read-only comparison at Cube TELEM2, R12 UART2 and the phone-side T12 Bluetooth link. Phase 49 emulator visual acceptance remains open separately.

### Broken or risky

- QGC radio-status reception establishes a radio message, not Cube liveness. Raw start-byte and checksum counters are not validated packet-loss or fault-location measurements. No physical telemetry or control acceptance is established.

### Next

- Ask the hardware team for a bounded same-setup heartbeat trace with source IDs at each hop. If QGC receives a Cube heartbeat on the same Bluetooth path while Mama GCS does not, inspect app transport/parser/source filtering. A later approved app phase may add validated, radio-only `RADIO_STATUS` visibility without satisfying Cube heartbeat gates.

### Do not touch

- Preserve existing Phase 49 documentation edits and historical evidence. Do not change Cube/radio parameters or wiring, run actuator commands, or treat a radio message as an authenticated vehicle.

## 2026-10-22 — Phase 49 visual acceptance attempt

### Done

- Attempted the approved disposable-emulator visual acceptance for the Phase 48 Classic stream diagnosis. Existing debug and Android-test APKs were available.
- ADB could not start its server and listed no emulator/device, so no APK was installed and no synthetic UI or physical-phone test was run. See [Phase 49](phase-49-report.md).

### In progress

- Phase 49 remains open pending a healthy disposable emulator and functioning ADB server.

### Broken or risky

- This is an environment block, not visual acceptance. Do not claim the diagnosis card passed screenshot review. Do not dismiss Android ANRs or use the populated phone as a software fixture.

### Next

- Repair/start the disposable emulator and ADB server, then run `ClassicConnectionCheckUiTest` with pre/post Android health checks and inspect light/dark captures.

### Do not touch

- Do not alter Cube/R12/T12 wiring or parameters, operate the rover, clear app data, or use the physical phone for instrumentation.

## 2026-10-22 — Phase 48 Classic telemetry-stream diagnosis

### Done

- Added a read-only diagnosis card to **Application settings → Bluetooth / HC-05** only for fresh Classic bytes with no supported validated MAVLink. Existing counters select one bounded category: no MAVLink start-byte candidates, rejected frame candidates, unverified unsupported-ID candidates or no more-specific validated-frame evidence. It names a safe next check but explicitly cannot determine wrong baud, R12 UART, TX/RX, noise or any physical cause. See [Phase 48](phase-48-report.md) and [decision D-048-01](DECISIONS.md#d-048-01-classify-undecoded-classic-receive-evidence-without-identifying-a-physical-fault).
- Fresh verification on 22 October: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed in 7m57s. XML reports 383 JVM tests across 63 suites, 0 failures/errors/skips; both APKs compiled; lint reports 0 errors/32 warnings. The synthetic Android UI test compiled but did not execute because no verified disposable device was available in this session.
- Updated the hardware record with a user-supplied 21 October Mission Planner live readback: current `SERIAL2_PROTOCOL=2` and `SERIAL2_BAUD=57` (MAVLink 2 / 57600). The older accepted `.param` file differs and must not be written to the Cube as a reconciliation step.

### In progress

- Hardware acceptance remains blocked on the receiver side: identify the R12 UART carrying the Cube TELEM2 route, TX/RX/GND continuity, and its serial forwarding configuration. Current Classic bytes have not produced a supported MAVLink message, Cube heartbeat or RC sample.

### Broken or risky

- The Phase 48 diagnosis is a UI interpretation of existing counters, not a test of wiring, receiver configuration, baud, electrical signal quality, radio forwarding, Cube identity or safe operation. Movement, arming and app emergency-stop commands remain unavailable.
- Android visual acceptance for this new card remains open. The known disposable-emulator/System UI ANR history is unchanged; do not use a populated phone as an instrumented test fixture or dismiss an ANR to obtain a pass.

### Next

- Run the focused `ClassicConnectionCheckUiTest` on a healthy verified disposable emulator, inspect the diagnosis card in light/dark themes and record post-run Android health. Separately, after the R12 details are established with a secured read-only procedure, repeat the Classic receive test and require supported decoded MAVLink, a selected-autopilot heartbeat and fresh `RC_CHANNELS`.

### Do not touch

- Do not write Cube parameters, infer receiver pinout from wire colors, disconnect/reconnect live wiring, move sticks, arm, or issue vehicle commands. Preserve the older parameter export and historical bench evidence.

## 2026-10-21 — physical Classic telemetry follow-up

### Done

- The operator confirms receiver telemetry is wired to Cube **TELEM2**, separately from **SBUS → RCIN**. The supplied photos show TELEM2 occupied and the R12 Multi Link V1.0 UART1/UART2/SBUS labels, but the connector hides which UART pair and exact signal pin order are used.
- Read-only phone inspection during an active Classic session (`T12_131`) found 202 chunks / 53,084 bytes, 2,392 MAVLink 1 start-byte candidates, 0 MAVLink 2 candidates, 0 supported decoded messages, 782 checksum failures and 1,597 unverified unsupported-ID candidates. Last input was 0 seconds ago. Mama GCS showed `Link lost · Setup` and `No sample received`.

### In progress

- Hardware person to identify whether the TELEM2 cable uses R12 UART1 or UART2 and verify TX↔RX, RX↔TX and GND wiring with power off. No physical pin order is inferred from wire color or the label.
- The live Cube `SERIAL2_PROTOCOL=2` / `SERIAL2_BAUD=57` readback is recorded in the current hardware record. Preserve the different older parameter file, but do not write it to the Cube.

### Broken or risky

- Current app receive is byte-active but has no validated supported MAVLink, heartbeat or RC sample. This is not Cube-link acceptance and does not authorize movement, arming or any other hardware command.
- The Phase 47 implementation-time note that hardware was unavailable is historical; this later same-day bench observation supersedes it for current status. It does not retroactively validate the telemetry stream.

### Next

- Obtain the R12 UART choice/pin map and a fresh read-only SERIAL2 parameter readback; then repeat the bounded Classic receive check and require supported decoded messages, a selected-autopilot heartbeat, and fresh RC_CHANNELS before claiming telemetry acceptance.

### Do not touch

- Do not change Cube parameters, disconnect/reconnect live wiring, move sticks, arm, or issue vehicle commands as part of this diagnosis. Preserve the populated phone and all historical test evidence.

## 2026-10-21 — Phase 47 plain-language Classic UI

### Done

- Added a short stage-derived explanation above the existing Bluetooth Classic/HC-05 technical checklist, without changing receive assessment, parser, transport, trust filtering, storage or command behavior. See [Phase 47](phase-47-report.md) and [decision D-047-01](DECISIONS.md#d-047-01-add-plain-language-classic-receive-status-without-changing-evidence).
- Fresh final Gradle run: 382 JVM tests/63 suites, 0 failures/errors/skips; app and Android-test APKs built; lint 0 errors/32 warnings. The new synthetic Android test compiled but did not execute.

### In progress

- Visual acceptance of the new text in light/dark themes is pending a healthy verified disposable emulator. Phase 46's targeted visual check is separately recorded; broader UI visual acceptance remains open.

### Broken or risky

- The disposable emulator showed a `com.android.systemui` ANR before Phase 47 APK installation/instrumentation; it was stopped without dismissing the dialog or clearing data. This repeats the environmental risk already recorded in Phase 46 and is not a passing visual check.
- At implementation time, hardware was unavailable. Earlier Classic bytes were not validated as supported MAVLink. A prior read-only Mission Planner observation was recorded as TELEM1/MAVLink2/57600, but later user-confirmed wiring is TELEM2 and the accepted parameter export disagrees with the recorded SERIAL2 settings; see the newer physical telemetry follow-up above. Neither Cube identity nor physical control readiness is established.

### Next

- On a healthy verified disposable emulator, run the focused `ClassicConnectionPlainLanguageUiTest`, inspect its two theme captures and Android health, then run the broader current UI inventory before any release claim. Keep the separate secured hardware diagnosis for when the identified setup is available.

### Do not touch

- Preserve Phase 46's existing dirty files and the operator's populated phone. Do not weaken safety wording, dismiss ANRs, modify hardware settings, enable vehicle commands or commit/push without authorization.

## 2026-10-21 — Phase 46 visual acceptance attempt

### Done

- Created a fresh disposable API 34 `MamaPhase46` AVD under ignored `app/build/` and installed the Phase 45 app/test APKs on that AVD only. See the [Phase 46 report](phase-46-report.md) for the test choice, legacy AVD-manager failure and exact scope.
- The first acceptance attempt stopped before instrumentation because Android System UI showed an ANR. A clean lower-memory retry then passed the original `ClassicConnectionCheckUiTest` **3/3** with no post-run Android error; synthetic light/dark top-of-panel captures were inspected and showed legible checklist rows. The safety guidance was below the captured viewport.
- Strengthened only that synthetic UI test to assert/record bottom-of-page visibility in both themes. Test APK rebuild and lint passed (0 errors/32 warnings). An ANR blocked the first attempt with the updated APK, but a final clean boot without reinstall passed the strengthened fixture **3/3**; four light/dark top/bottom captures were inspected and post-run Android health was clean. Evidence and both earlier ANRs remain in [Phase 46](phase-46-report.md). The verified disposable emulator was stopped without clearing data or dismissing dialogs. Production app and hardware code did not change.

### In progress

- Targeted Phase 45 Classic checklist visual acceptance is complete. No further Phase 46 test is running. Broader Android suite and physical acceptance remain separate open work.

### Broken or risky

- `com.android.systemui` ANR recurred after APK installs, including once with about 2.4 GB host RAM free. Clean-boot tests passed, but causation and broader emulator stability are not established. Do not dismiss ANRs or treat this targeted pass as a full UI/endurance qualification.
- The latest physical Classic stream still lacks validated supported MAVLink, heartbeat and RC evidence. The safety/release gaps below are unchanged.

### Next

- Before a release claim, run the complete current UI inventory on a healthy disposable emulator and complete the release gate. Keep the separate secured read-only Cube/R12/T12 bench observation for when that setup is available; the latest real stream is still undecoded.

### Do not touch

- Do not run synthetic UI fixtures on a populated phone, clear operator data, weaken the acceptance runner, change Cube/radio/VESC settings or enable vehicle commands. No commit/push was requested for this phase.

## 2026-10-21 — Phase 45 Classic RC receive evidence

### Done

- From clean `main` at `ced11d690dbdad392bdb4b9ab465266feb59c3d7`, Phase 45 added a separate, read-only fresh `RC_CHANNELS` indication to the Classic checklist and explicit evidence report. It requires current Classic session, fresh selected-autopilot heartbeat, accepted RC observation and fresh RC state. Parser, transport, source selection, storage, parameters and command paths did not change. See [Phase 45](phase-45-report.md) and [decision D-045-01](DECISIONS.md).
- Fresh Gradle run completed: 381 JVM tests/62 suites, no failures/errors/skips; debug and Android-test APK builds passed; lint 0 errors/32 warnings. Instrumentation compiled but did not run. The user subsequently authorized a focused Phase 45 commit and push; check Git history and remote status for their actual outcome.

### In progress

- Emulator visual execution is open: ADB had no attached device and existing AVDs were not established as disposable. The new UI test compiled but has no executed screenshot review.

### Broken or risky

- Latest supplied physical bench session still shows Classic bytes but zero validated supported MAVLink; no current Cube heartbeat or RC has been independently observed. The new RC indicator will remain `NOT CONFIRMED` until the existing receive chain validates such data. Its `FRESH` state would still not prove physical stick movement, SBUS wiring or safe control.
- The Phase 44 release, speed/failsafe, authenticated-source, recovery and ANR caveats below remain unresolved. Do not label the app rover-control-ready.

### Next

- On a verified disposable emulator with adequate resources, run `ClassicConnectionCheckUiTest`, inspect the captured checklist in both themes, and record device health after the run. Do not use a populated phone as a test fixture.
- If the identified Cube/R12/T12 setup is available under a hardware-team-secured read-only procedure with outputs isolated, compare current Classic byte counts, checksum-validated supported messages, fresh selected-autopilot heartbeat and RC_CHANNELS. Preserve a sanitized evidence report. No parameter changes, arming or actuator commands.

### Do not touch

- Preserve the accepted [hardware record](hardware-integration.md) and all historical failures. Keep vehicle commands unavailable; do not infer hardware safety or identity from a receive indicator. Do not clear app data, operate hardware, or commit/push without the corresponding authorization.

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

- Preserve the [accepted hardware/parameter record](hardware-integration.md). It retains Cube Orange family, Skydroid T12/R12 Multi Link V1.0, remote-name history, user-confirmed TELEM2 plus separate SBUS/RCIN, the parameter-file values and 8 km/h maximum. Exact R12 UART pins and the current live SERIAL2 configuration remain unresolved. These are not independently verified physical behavior. Do not repeat old questions without a new conflict.
- Keep command paths unavailable; protect auth, trust filtering, storage and safety boundaries under [security](security.md) and [project instructions](../AGENTS.md).
- No raw telemetry or user secrets in Git; no app-data clearing/uninstall, destructive rollback, automatic commit/push or hardware operation. Preserve old reports and unrelated changes.
