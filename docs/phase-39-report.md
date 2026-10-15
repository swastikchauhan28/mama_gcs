# Phase 39 — BLE receive-profile evidence export

## Delivered

Application settings → Bluetooth / BLE now provides **Capture BLE profile**. This uses existing discovery results, not a new connection, subscription or hardware query.

- Capture requires a successful, currently connected GATT discovery, no competing telemetry link, and no scan, connect/close, subscription setup or running stability trial.
- One immutable text snapshot is held by the screen's ViewModel. A new explicit capture replaces it. Reconnecting or changing discovery results does not silently rewrite the snapshot. No auto-capture or persistence is added.
- Disconnect all telemetry links before **Review captured profile** and **Save text file**. The Android document picker is not opened during an active BLE connection or stability trial. Both UI and ViewModel check admission.
- Review contains a frozen timestamp, process-local connection ordinal, eligible-option count, up to 64 exact service/characteristic UUID pairs, instance numbers, and the app's NOTIFY/INDICATE choice. Empty and truncated lists are explicit.
- The report describes only receive options with a notification configuration descriptor, not all GATT services or supported protocols. It does not prove which option carries MAVLink or VESC data.
- Close retains the snapshot; Discard removes it from memory. Picker cancellation/failure and write failure retain it for retry. A failed write warns that a partial file may exist. Successful saving clears the snapshot; process recreation cannot resume a save without it.
- UTF-8 output is bounded at 24 KiB. UUIDs must match canonical UUID syntax; arbitrary strings are omitted. Device names, Bluetooth addresses, payloads, arbitrary errors, coordinates and accounts are excluded. UUIDs may reveal a vendor/profile: review before manual sharing.

## Hardware-team workflow

1. Connect and inspect the identified remote as usual. There is no need to start telemetry receive or a stability trial for this report.
2. Tap Capture BLE profile. No notification descriptor or vehicle command is sent by capture.
3. Disconnect BLE and stop other telemetry links/scanning, then tap Review captured profile.
4. Check the contents and save the text file to a location you choose. Associate it with the intended remote manually before sharing it with the hardware team.
5. Ask which exact service/characteristic carries serial telemetry, which payload protocol it uses, and whether the R12/Cube must be connected before notification data is sent.

The capture belongs to the BLE screen's in-memory state; leaving that navigation entry or restarting the process can lose it. This is not a persistent hardware profile, trusted pairing, automatic receive selection, or safety approval. No existing command restrictions are relaxed.

## Initial validation (superseded by the successful rerun below)

- `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`: **BUILD SUCCESSFUL** in 10m 14s.
- **357 JVM tests passed**, zero failures/errors/skips, including six formatter/bounded-output tests and six snapshot/export-state tests added in this phase.
- Lint: **zero errors, 31 warnings** (same count as Phase 38).
- Four new synthetic Android UI tests compile: connected capture versus disconnected review, long preview/save reachability and reconnect lockout, separate close/discard behavior, and picker/write mutation lockout.
- **Emulator UI acceptance is BLOCKED, not passed.** The planned 30-check BLE regression run never started: the runner rejected Android ANR dialogs before instrumentation. Two starts of the existing API 34 emulator showed System UI ANRs. A separate clean API 34 test device reached the launcher but subsequently reported Android system, Bluetooth-service and Google-services ANRs during initialization/installation; Windows then had approximately 141 MB free RAM. No error dialog was dismissed or runner safeguard bypassed.
- Both APKs installed successfully. The isolated test device is in ignored `app/build/phase39-avd-home`; existing emulator data was not cleared. Test emulators were stopped after the blocker. No screenshots from this phase are claimed as visually verified.

## Retest — 15 October 2026

After the user closed unused apps, host free memory was approximately 4.5 GB. The existing isolated emulator still produced system-service startup ANRs, so it was stopped. A separate temporary API 34 emulator outside OneDrive, with 2 GiB guest RAM, four virtual cores and SwiftShader rendering, booted sufficiently to run instrumentation. Existing user emulator data was not cleared.

- The unchanged APKs ran the full targeted **30-test** suite: **29 passed, one failed** in 371.377 seconds. All Phase 38 regression checks and three of the four new profile UI checks passed.
- `BleProfileReportActionsTest.closeAndDiscardAreSeparateActions` failed at the discard click after closing the preview with `No compose hierarchies found in the app` (test line 80). Its preceding close and retained-review assertions completed. This does not by itself prove an app logic defect or establish a root cause.
- A separate unchanged-code run of that single test **passed** in 24.342 seconds. This is an intermittent failure, **not a clean full-suite pass**. No test assertion, runner guard, application code or device error dialog was changed to obtain the retry result.
- Background Google-services ANRs were present in the emulator event log during the run. No Mama GCS crash/ANR was present in the inspected event output, but emulator reliability remains a qualification on these results.
- The landscape report-preview screenshot was reviewed: the scrollable report body is readable, with Close preview and Save text file visible. This does not claim portrait, large-font, full navigation visual acceptance or actual document-provider compatibility.
- Local ignored evidence: `app/build/acceptance-20261015-172848-0ff2ff4e/instrumentation.txt`, `ble-profile-preview.png` in that same directory, and isolated retry `app/build/acceptance-20261015-173608-ceb8e139/instrumentation.txt`.
- No hardware connection, BLE subscription or rover command was made. Temporary test emulators were shut down afterward. The 357-test JVM/build/lint results above were from the preceding build, not rerun during this UI-only retest.

## Synchronization fix and successful rerun — 15 October 2026

The close/discard test now waits, with a bounded timeout, for the resumed host activity to regain window focus and for the discard control to be displayed after dialog dismissal. The earlier existence-only check did not establish window readiness. This addresses a test synchronization gap; it does not establish the exact underlying cause of the earlier emulator failure. Production application behavior is unchanged.

- The test performs five close/discard cycles with distinct snapshots, checking that Close retains the exact snapshot, Discard removes it, and each callback occurs exactly once. Save must never be called. Clicks and outcome assertions are not retried; there is no fixed sleep or bypass of runner safeguards.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`: **BUILD SUCCESSFUL** in 2m 1s. The unchanged JVM test task was up-to-date; its XML results still contain **357 tests, zero failures/errors/skips**. Lint remains **zero errors, 31 warnings**.
- Corrected full targeted BLE UI suite: **30/30 passed** in 129.674 seconds.
- Separate consecutive rerun of all four profile UI tests: **4/4 passed** in 26.4 seconds. The formerly intermittent case completed **10 close/discard cycles across these two runs**.
- Local ignored evidence: `app/build/acceptance-20261015-174423-68d45d8a/instrumentation.txt` and `app/build/acceptance-20261015-174720-f2c041dd/instrumentation.txt`.
- Post-test event-log inspection found one background Google-services startup ANR, but no Mama GCS crash/ANR in the inspected output. Both instrumentation runs completed successfully; this is not a claim that the emulator was free of all system-service issues.
- These were synthetic emulator UI checks: no physical BLE connection, subscription, movement, arming or other rover command was made.

The remaining failed targeted UI test is resolved in these runs. Manual Android document-picker/provider compatibility, physical-radio acceptance and broader release testing remain pending. These tests do not certify rover operation.
