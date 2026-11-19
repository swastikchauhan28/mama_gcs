# Phase 202 — TCP/heartbeat events in telemetry report

## Goal and baseline

User approved a bounded, read-only addition to **Prepare telemetry report** on 2026-11-19. Baseline `3d7b518`, clean `main`; model/version unavailable. The earlier live Cube heartbeat interruption remains unexplained. See D-202-01.

## Design and execution path

The TCP ViewModel owns at most eight volatile socket status/time observations. `MavlinkDiagnostics` owns selected-autopilot heartbeat timing and up to three completed gaps. `TcpHeartbeatTimeline` projects the latest eight combined events from matching elapsed-time observations in the displayed TCP session. The diagnostics card and `TelemetryEvidenceReport` use that same projection, so their ordering, session filtering and fixed event labels agree.

`MamaGcsApp` passes the TCP state to `TelemetryReportControls`; an explicit Prepare tap freezes the current vehicle state, transitions and elapsed capture time through `TelemetryReportViewModel`. The allowlisted report labels the session active or closed, prints each event's age **at capture**, and says when no event or matching elapsed TCP session is available. The preview remains frozen while the Android document picker is open; saving still requires an explicit tap. The report's 16 KiB limit and exclusions for network endpoint, device address, coordinates, raw payload and arbitrary error text remain.

This event list is memory-only until the user explicitly saves the report. It is bounded and can skip rapid status changes; a past `TCP OPEN` event is not proof of continuous socket availability. The report does not authenticate a vehicle, identify a failed hardware hop, or authorize physical operation.

## Verification

- Focused JVM command: `.\gradlew.bat :app:testDebugUnitTest --tests "com.mamadrones.gcs.core.diagnostics.TelemetryEvidenceReportTest" --tests "com.mamadrones.gcs.presentation.screens.TelemetryReportViewModelTest" --tests "com.mamadrones.gcs.presentation.dashboard.MavlinkDiagnosticsPanelTest" --no-daemon --max-workers=1 --console=plain` — passed on 2026-11-19. It covered event order and ages, current/closed/empty/mismatched sessions, fixed-label privacy and frozen preview.
- Full command: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — passed in 4m 8s. XML results show 521 JVM tests in 82 suites, zero failures/errors/skips; both debug APKs built. Lint reported zero errors and 27 warnings. Some Gradle tasks were up-to-date; the targeted JVM run preceding this gate executed the changed tests.
- Disposable no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator was started with host GPU and passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30` without new crash/ANR events. Both APKs were installed using `adb install -r`. `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.TelemetryReportActionsTest` passed `OK (3 tests)` and the Android health gate. The first screenshot showed the header but cut off the event rows; this was a visual-test capture issue, not an assertion failure.
- The Android test was changed to scroll the existing preview before capture. `.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed; the emulator passed a fresh 30-second health preflight; the rebuilt test APK passed the same three-test suite and Android health gate again. Final evidence is under ignored `app/build/acceptance-20261119-113441-baf6b8c6/`. The ignored `app/build/phase202-tcp-report-preview.png` was inspected: active-session label, recovered heartbeat, five-second threshold and TCP OPEN with capture-time ages are legible. The fixture sent no command, opened no socket and saved no report. The runner could not independently identify the GPU option from its process command line.

## Remaining and release boundary

No live Cube gap or physical fault was reproduced in this software phase. The synthetic event ages and UI do not establish continuous TCP availability or validate the sender. No parser, transport, timeout, source filtering, account, dependency, permission, command or hardware-setting change was made. The baseline is a source recovery point, not a certified release. Commit/push require a separate user request.
