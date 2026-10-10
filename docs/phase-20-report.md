# Phase 20 — Telemetry evidence report

## Delivered

Analyze tools → Link & messages → Prepare telemetry report captures a frozen, read-only text snapshot. The operator sees the complete report before choosing Save text file. Android's document picker selects the destination; the app neither sends the report to anyone nor uploads it itself. A selected cloud document provider may upload according to that provider's behavior.

The report includes capture/session times, active versus closed/no-session status, connection state, observed MAVLink system/component IDs, version and decoder counters, heartbeat/input ages, and sample receipt times. No location coordinates, track, device name/address, network endpoint, account data, parameter contents, motor fault text, mode string, STATUSTEXT or raw packets are serialized. The source IDs are observations, not trusted pairing. Timestamps do not prove usable sensor readings or safety.

The report is generated from an explicit field allowlist and bounded to 16 KiB at the writer. Missing session counters are NOT RECORDED, not invented zeros. Closed sessions are labeled history. Future sample timestamps and samples predating the session are labeled rather than reported fresh.

## Save lifecycle and boundaries

- Preview capture is memory-only, retained by the navigation ViewModel across rotation, not process death. The original snapshot is written even if telemetry changes or stops while the picker is open.
- Opening the system picker can background the app and close foreground-only receivers. Reconnection remains manual; the snapshot retains its capture-time labels.
- Picker cancellation preserves the preview. Duplicate save/capture actions are blocked while choosing/writing. Document I/O runs off the main thread and closes its stream on success/error.
- Write failures retain the preview for retry and warn that a partial destination file may exist. No automatic deletion is attempted. Dismissing the screen or process death during writing can also leave an incomplete document; verify the saved file before sharing.
- After process death, a picker result without its original snapshot does not write fresh or replacement telemetry to that destination. The user is told to prepare a new report; the document provider may have created an empty file.
- There is no continuous telemetry log, raw `.tlog`, report history, automatic sharing, vehicle control or configuration write. Saved reports are outside the app's encrypted account store and are controlled by the chosen document provider.

## Updated hardware evidence

The user now identifies the handheld remote as Skydroid T12, the receiver as R12 / Multi Link V1.0, and the installed Cube firmware as ArduPilot Rover 4.7.0. These are reported facts, not software-verified firmware or device identity. “HC-05” is the Bluetooth name shown on the phone; it does not prove a separate module. SBUS → RCIN is user-confirmed. The photos indicate a separate TELEM1 cable, but exact end-to-end TX/RX continuity and data forwarding are still unverified. The Cube `.param` file and physical stop/disconnect behavior remain needed before control work can be finalized.

## Manual acceptance

1. In a stationary approved telemetry test setup, open Link & messages and prepare a report. Check displayed counters against the frozen preview.
2. Confirm excluded fields do not appear; close the preview without saving to cancel.
3. Prepare again, save through the document picker and inspect the resulting text file. Its capture time and counters should match the preview, not later live updates.
4. Cancel the picker and retry. Test denied/unavailable storage: failure must not be reported as a successful save. Remove incomplete files before sharing.
5. Reconnect telemetry manually if the picker closed it. Share the verified file with the hardware team using your file manager.

## Validation

Verified on 2026-10-10 with `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`:

- Debug app and Android test APK built successfully.
- 180 JVM tests passed, with zero failures, errors or skipped tests; this includes 14 new report/writer and ViewModel tests.
- Lint completed successfully; the report lists 0 errors and 29 warnings.
- `git diff --check` passed.
- Two new Compose fixture tests compiled into the Android test APK but were not executed on an emulator or phone in this phase.

Physical phone picker/provider behavior and hardware telemetry acceptance remain unverified. These software checks do not establish rover safety or command capability. No commit or push was performed.
