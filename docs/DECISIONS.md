# Decisions and alternatives

## D-059-01: Separate mobile workspace title from link shortcut

- Date: 2026-10-26. Status: drafted before implementation under the user's UI-only next-phase request. Exact AI model/version unavailable. Baseline `3e767a05d1b6e3d20e612376cd5940fb8bc3d46b`; preserve untracked `desktop.ini`.
- Context: on phone-width screens, the long connection shortcut competes with the workspace title in one header row. The observed “Application settings” title is ellipsized even though there is vertical space. The title, route and connection label are display-only inputs to `StationShell`; no transport or vehicle state is written by this layout.
- Alternatives: abbreviate status words, reduce title text size, allow the entire row to wrap unpredictably, or place the existing connection shortcut on a dedicated second mobile row. Choose the second row to retain truthful status wording and a stable menu/title alignment. Keep the wide header unchanged.
- Reason/trade-off: the mobile header becomes one touch-target row taller, reducing content height. The title should remain readable at 320–360 dp and 1.3× text size; an unusually long future label may still need another layout adjustment. Preserve one shortcut instance and its existing navigation/test tag.
- Verification: focused Compose geometry/text-layout and shortcut-navigation test, existing software build/JVM/lint checks, and visual inspection on a disposable emulator. No physical connection or control inference follows from these checks.
- Rollback: reverse only the header/test/documentation diff after review. There is no data, dependency, permission, schema, remote destination or hardware migration. Revisit if the extra row obscures necessary content on supported smaller screens.

## D-058-01: Reveal the selected mobile workspace tab

- Date: 2026-10-26. Status: drafted before implementation under the user's UI-only request. Exact AI model/version unavailable. Baseline `f241ba13636e9a626f1973d5f7a3f75d1a520b92`; preserve untracked `desktop.ini`.
- Context: the narrow workspace tab row remembers its horizontal offset across route changes. Opening a page from the menu or a shortcut can therefore leave its selected tab outside the visible strip. Inputs are local route changes; the selected tab is transient UI state and has no persistence or vehicle effect.
- Alternatives: leave manual scrolling, add a separate current-page label, or scroll the existing tab strip to the selected item on route changes. Choose the last option as the smallest direct fix. Keep the wide drawer and existing tab clicks.
- Risk/trade-off: automatic movement can surprise someone who has manually scrolled the strip. Trigger only when the selected route changes, not on every recomposition or live telemetry update. A too-wide tab may still be clipped on an unusually narrow viewport; verify normal phone widths and large text. No transport, command, permission, dependency, schema, or hardware change.
- Verification: focused Compose geometry/navigation test on a disposable emulator; JVM test, APK/test APK build, lint and visual check where available. A UI pass cannot establish Cube or rover operation.
- Rollback: reverse only this navigation UI, focused test and documentation diff after review. No data migration or physical rollback. Revisit if route changes produce a distracting jump or supported phone widths cannot show the selected tab.

## D-057-01: Gate emulator acceptance on JUnit and Android health

- Date: 2026-10-24. User approved Phase 57. Exact AI model/version unavailable. Baseline `f467724c3801ba20b8c5374e8ed4910d6aceedde`; preserve existing documentation edits and untracked `desktop.ini`.
- Context: Phase 56's host-GPU run aborted after Android System UI crashed, whereas one SwiftShader run passed 130 software methods. The current acceptance runner checks boot/error windows before instrumentation and JUnit afterward, but records neither the actual renderer nor Android health after the run.
- Alternatives: rely on manual log inspection; require only a post-run window check; or capture the emulator process's GPU option and compare Android crash/ANR log entries across the run, in addition to post-run boot/window checks. Choose the last option, without clearing log buffers or changing test assertions.
- Reason/trade-off: a JUnit pass cannot silently overrule a concurrent Android crash. Device logs can contain unrelated background failures; label the responsible process and preserve evidence instead of automatically attributing a crash to Mama GCS. If a required health inspection fails, the run is not accepted. The GPU option is a local test-environment observation, not proof of graphics-driver identity.
- Verification: test pass/fail branches with controlled fixture output, then run the reviewed 35-class/130-method software-only inventory on the disposable SwiftShader AVD and inspect the runner's saved evidence. The four physical BLE acceptance methods remain excluded.
- Rollback: revert only the test-runner/documentation diff after review; no APK, schema, dependency, app data or hardware change. Revisit if the Android log format changes or background system events cause a misleading gate.

## D-056-01: Use software-rendered disposable AVD to isolate System UI crash

- Date: 2026-10-24. Exact AI model/version unavailable. Baseline `f467724c3801ba20b8c5374e8ed4910d6aceedde`; preserve the earlier handover/report edits and `desktop.ini`.
- Context: the monolithic 130-method software UI run on the task's `-gpu host` AVD aborted at method 30. Timestamped Android logs show `com.android.systemui` failing in `FinalizerWatchdogDaemon` while destroying a `HardwareRenderer` context, followed by window death and ANR stack collection. The prior exact 46-method group had passed; no Mama GCS assertion failure explains the system crash.
- Alternatives: patch app/test code without an app-level cause; ignore the crashed run; split the suite indefinitely; or compare the same AVD, APKs, test order and assertions with the installed emulator's `swiftshader_indirect` renderer. Choose the controlled renderer comparison and keep the failed result.
- Reason/trade-off: one software-rendered AVD completed 130/130 and stayed free of reported ANR/error windows, supporting an emulator graphics-backend issue without proving a specific driver root cause. SwiftShader is not identical to host-GPU rendering, may have different performance, and one pass does not establish repeatability, real-device behavior or physical safety.
- Revisit condition: another SwiftShader crash, a reproducible Mama GCS process failure, or UI rendering differences on supported devices. Re-run the full software inventory after such changes; do not weaken assertions or count an aborted run as a pass.
- Rollback: no app/test/build source or user data changed. The renderer choice affects only the disposable AVD launch; restore the prior option only for a scoped comparison after preserving evidence. No physical hardware or release rollback occurred.

## D-055-01: Bound system screenshot capture for BLE fixture tests

- Date: 2026-10-24. Status: drafted before the test edit under the user's request to work on the failed test. Exact AI model/version unavailable. Baseline `edda04a91460bcdd5e0a4968a5fcf2c564a19933`; preserve existing documentation edits and `desktop.ini`.
- Context: the restored-source seven-method emulator batch failed only when `UiAutomation.takeScreenshot()` returned null for the BLE preview test; the same method passed alone. Compose semantics proved Save was displayed, but did not prove Android's separate dialog window was active or the compositor screenshot was ready. The test's preview/Save state is a fixture; no Bluetooth or hardware input is involved.
- Alternatives: keep the one-shot capture and accept intermittent red batches; switch to Compose-root PixelCopy (already failed in Phase 54); omit the screenshot (loses visual evidence); or wait for the actual Android dialog to become the active accessibility window, then wait a bounded time for one valid system screenshot. Choose the last option, with explicit failure if the window or screenshot never appears.
- Reason/trade-off: preserve the same Android-wide visual evidence and all content/button assertions without changing production UI. A bounded compositor-readiness wait may conceal a short graphics delay, but not a persistent null/blank screenshot or wrong active window. It does not resolve unrelated home-marker, picker, or teardown failures.
- Verification: rebuild the test APK, run the focused method and the same seven-method batch on the disposable emulator, inspect the saved screenshot and Android health. If stable, run the other reviewed software-only classes in bounded batches. Record any failure without loosening assertions; physical acceptance remains separate.
- Rollback: revert only the scoped test change after diff review; no app-data, schema, dependency, runtime-app or hardware migration. Revisit if Android's accessibility window evidence is unavailable on supported devices or a valid frame still cannot be captured reliably.
- 2026-10-24 correction: the first isolated run timed out waiting for the dialog title in `UiAutomation.rootInActiveWindow`, despite Compose showing the Save button. The accessibility-root condition is not a valid readiness gate on this emulator and was removed. The revised implementation asserts the visible dialog title through Compose semantics and retains a bounded wait for the original Android-wide screenshot; this still fails if no screenshot arrives.
- 2026-10-24 expanded evidence: the repaired BLE profile test and original seven-method batch passed, but the next 52-method batch failed in two sibling BLE fixture tests at their own one-shot `takeScreenshot()` calls (50/52). Consolidate the same bounded system-capture wait into a test-only helper for all three observed cases, retaining each screen's prior content assertions and requiring PNG encoding. No production UI change is justified by these failures.
- 2026-10-24 verification correction: the shared helper's three affected methods passed together (3/3). A full 52-method rerun passed the first former screenshot failure but aborted at method 9 after a separate ActivityScenario teardown timeout and Android system crash. After a no-wipe disposable-emulator restart, that lifecycle method passed alone and the four related BLE classes passed together (17/17). This is targeted evidence, not a full-suite pass; the screenshot files were later recovered and visually inspected.

Record consequential choices before implementing them. Keep dated corrections; do not rewrite earlier outcomes. Existing phase reports remain the historical record. This file starts with Phase 44 and does not invent the reasoning behind earlier designs.

## D-054-01: Stabilize fixture visibility and screenshot evidence without relaxing assertions

- Date: 2026-10-24. Status: drafted before Phase 54 edits. Exact AI model/version unavailable. Starting revision `edda04a91460bcdd5e0a4968a5fcf2c564a19933`; pre-existing test-report/handover edits and untracked `desktop.ini` are preserved.
- Context: the 23 October software retest found a home-card `assertIsDisplayed` failure on a vertically scrollable fixture, one null Android-wide screenshot in the BLE panel test, and an unrelated `ActivityScenario` teardown timeout in a 123-test batch. All three cases passed on focused rerun; root causes are not proven. No real hardware is involved.
- Alternatives: leave tests unchanged and retry; add generic sleeps/retries; remove the assertions or screenshot; or make the two fixture actions deterministic while retaining their intent. Choose explicit scroll-to before the home status visibility assertion and Compose-root screenshot capture for the BLE fixture. Do not hide the lifecycle timeout with retries or change production UI.
- Trade-off: Compose-root capture tests the rendered app surface rather than the entire Android window; the test's status/guidance assertions remain unchanged, and the saved screenshot remains diagnostic evidence, not a pixel oracle. The lifecycle timeout may recur in long batches and will be reported separately. Revisit if a screen-level system overlay must be asserted or the timeout repeats under healthy emulator conditions.
- Verification: compile Android tests, run both focused cases, then the reviewed software-only emulator inventory without the physical remote class. Preserve earlier failed logs, check Android post-run health, and report any new failure without weakening the gate.
- Rollback: reverse only the Phase 54 test/documentation diff after review; no schema, dependency, runtime-app or hardware migration is involved. The current revision is a source baseline, not a verified production release.
- 2026-10-24 result: the two edited tests passed 4/4 in the focused run, but the full software inventory split into disjoint batches had a real missing-H screenshot and a picker foreground timeout. Individual reruns passed; neither batch failure is resolved. See [Phase 54](phase-54-report.md). No renderer or picker wait change was made because the cause and appropriate production/test boundary need separate investigation.
- 2026-10-24 correction after review: scrolling before the home assertion would weaken its original immediate-visibility requirement, so that edit was reverted. The Compose-root screenshot later failed in PixelCopy (3/4 in a focused check), so that edit was reverted too. No Phase 54 test-code change remains; the drafted choice above is not the shipped outcome. The observed home-marker and picker failures remain open.

## D-052-01: Verify home-marker pixels before changing its renderer

- Date: 2026-10-23. Status: drafted before Phase 52 code edits; the user approved a bounded home-marker visual investigation and small fix if needed. Exact AI model/version unavailable.
- Baseline: clean `main` at `0917dab5d934576a10a3e55d8fc8b4c128d5bd95`.
- Context: the Phase 51 offline-map capture shows the purple home circle but no discernible white H. The test checks layer/source presence and freshness, not rendered H pixels. The bitmap icon and symbol layer are in-memory map-style data; they do not affect telemetry or persistence.
- Alternatives: accept layer presence as sufficient, switch to a basemap text glyph, replace the marker renderer, or add a pixel-level offline-map check and enlarge the existing local icon. Choose the last option. A basemap glyph would add remote-font dependence; a renderer replacement is disproportionate while the existing icon path can be checked.
- Risk/trade-off: a larger H could crowd the home circle at some densities; pixel tests must check both purple marker and white center, and the screenshot still needs visual review. Emulator System UI ANRs may prevent physical rendering acceptance even when a test APK builds; do not substitute an earlier weaker test result. Keep marker visibility/freshness tests. If the icon path remains unreliable, prefer a local self-contained marker instead of weakening the assertion.
- Verification: focused disposable-emulator map test, screenshot inspection, JVM/build/lint checks, and review that command/telemetry/hardware paths stay untouched. No owner/physical acceptance is implied.
- Rollback: review and reverse only the Phase 52 map/test/documentation diff if needed; no schema, app-data or hardware migration.
- 2026-10-23 follow-up: [Phase 53](phase-53-report.md) passed the unchanged focused disposable-emulator pixel/expiry test and visually confirmed a legible H. This closes the narrow fixture visual gate, not the hardware or release gates; earlier Android ANRs remain recorded.

## D-051-01: Fix narrow source lint warnings without changing behavior

- Date: 2026-10-23.
- Status: drafted before Phase 51 source edits; user approved a software-only lint pass. Exact AI model/version unavailable.
- Baseline: clean `main` at `2134eb492a636571ce25156e3baa9ebede0cbbc7`.
- Context: the last lint report has 32 warnings. Most are version notices; three are Compose optional-`modifier` ordering, and one recommends an existing KTX bitmap helper. Hardware diagnosis remains blocked outside this software phase.
- Alternatives: upgrade dependencies/build tools, suppress warnings, or fix only behavior-preserving source usages. Choose the narrow source fixes; dependency upgrades and manifest/debug orientation policy need separate risk review. Do not suppress lint.
- Risk/trade-off: reordering public Composable parameters can break positional Kotlin call sites, so update and compile every repo call site. Replacing `Bitmap.createBitmap` with `createBitmap` should preserve dimensions/configuration/drawing, but verify the marker path and targeted UI on a disposable emulator when available.
- Revisit if: compilation, UI/layout or marker appearance changes, or lint reveals a different warning set on a fresh run. Roll back only this small source/call-site diff after review; no app-data or hardware migration is involved.

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
