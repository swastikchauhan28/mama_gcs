# Phase 15 — MAVLink receive diagnostics

Date: 2026-10-07. Scope: diagnose receive-only MAVLink sessions without adding commands, security bypasses, logging persistence, or assumptions about the installed radio.

## Delivered

- Shared decoder counters work with both UDP and BLE: input chunks/bytes; decoded supported messages; messages admitted from the selected autopilot; messages ignored before autopilot selection or from another source; checksum failures; malformed payloads; unsupported message IDs/flags; and signed candidates rejected by the existing policy.
- The last unsupported message ID and last input/accepted-message receive ages are available. The UI's parser-error total means checksum failures plus malformed payloads; unsupported/signed candidates have separate counts.
- Diagnostics shows the current session type and distinguishes active sessions, closed historical sessions, and no session started. The existing socket card is explicitly UDP-only; BLE raw notification counts remain on the BLE page.
- The BLE page also shows decoder counters for a BLE session, never a UDP session's decoder history. Historical counters are not labeled live, even if a new BLE device has been inspected but reception has not started.
- Counters remain visible after disconnect, reset at the next MAVLink receive-session start, and disappear on process restart. Storage is fixed-size: no raw packets, credentials, Bluetooth addresses, or unbounded ID/history collections are retained.
- Operator guidance explains why bytes may not become telemetry and identifies possible checks without claiming a particular hardware fault. It explicitly discourages disabling a live rover's signing as a workaround.
- The source-selection policy is unchanged. Signed candidates, unsupported candidates and source-filtered messages never become accepted telemetry merely because diagnostics counts them. No vehicle actions are enabled.

## Interpretation limits

An input chunk is a nonempty UDP datagram or BLE notification delivered to the decoder, not necessarily a MAVLink message. One chunk may contain several frames, and one frame may span chunks. Empty input is not counted. Raw noise or incomplete data can yield zero parser results and zero parser errors; zero errors does not prove that a stream is valid MAVLink.

Decoded counts cover only supported, checksum-validated messages. Accepted counts are the subset admitted by the selected-autopilot policy; they do not guarantee every numeric field is meaningful or that a whole multi-part status message is assembled. Candidates rejected for signing, unsupported flags or unknown IDs are not established as checksum-valid/authenticated messages. These counters are neither packet-loss rates nor machine-health assessments.

"Active session" describes the receive-session lifecycle, not vehicle heartbeat liveness. Use the connection status and receive ages separately. The app still accepts only its supported unsigned MAVLink 2 subset and still requires a valid autopilot heartbeat before admitting telemetry.

## Validation

- `:app:testDebugUnitTest`: 90 tests passed, no failures/errors/skips.
- `:app:assembleDebug`: passed; debug APK generated.
- `:app:connectedDebugAndroidTest` restricted to `com.mamadrones.gcs.FoundationUiTest`: all 11 tests passed on the Pixel 3a Android 14 / API 34 emulator. This is the Foundation UI suite, not every instrumentation class in the project.
- `:app:lintDebug`: passed with 0 errors and 28 warnings.
- `git diff --check`: passed.

The combined verification run completed successfully. No physical radio, Cube Orange or VESC acceptance test was performed.

Tests cover result classification, chunk/message separation, malformed/corrupt/unsupported/signed candidates, source filtering, noise, fragmented frames, retained closed-session counters, new-session reset, and diagnostic wording. UI regression checks cover counters after disconnect, command lock preservation, and BLE-versus-UDP history separation.

## Operator check

1. Start the existing UDP/SITL connection or the hardware-confirmed BLE characteristic.
2. Open Systems → Diagnostics. On BLE, the same decoder details are also on the BLE MAVLink page.
3. Check Input bytes, Decoded supported messages and Accepted from selected autopilot in that order. Read the guidance for any rejection/ignore counters.
4. Disconnect. Confirm the card says LAST SESSION / CLOSED and retained counters are not confused with live telemetry.
5. Start a new receive session. Verify counters restart from zero before new bytes arrive.

Physical BLE/Cube Orange/VESC testing remains pending. Durable logs/export, packet-loss estimates, live motor telemetry, trusted authentication, command handling, missions on the vehicle, and spray/hydraulic integration are not delivered by this phase.
