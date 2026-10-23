# Hardware integration gates

## 23 October 2026 — phone-side comparison and next isolation test

The operator reports that QGroundControl connected to the T12 Bluetooth link and its MAVLink Inspector displayed `RADIO_STATUS (109)` from component 68, with counts/rates changing (screenshots supplied 22 October). The operator then reports that `HEARTBEAT (0)` did not appear; the supplied Inspector screenshots show only the `RADIO_STATUS` row in the visible list. This establishes that QGC parsed at least a radio-status message on its tested link. It does **not** establish Cube telemetry, identify which hop loses it, or prove that QGC and Mama GCS used identical remote/device/session settings. QGC's received `RADIO_STATUS` is distinct from its own heartbeat emission.

A separate Mama GCS Classic-session screenshot supplied 22 October shows 482 chunks / 126,757 input bytes, 6,570 raw `0xFE` candidates, zero raw `0xFD` candidates, zero decoded supported messages, 2,577 checksum failures and 3,981 unverified unsupported-ID candidates; the app had recent bytes but no accepted Cube heartbeat or RC sample. These are session totals, not error rates. The screenshots do not establish the same stream bytes as QGC's session. The app parser deliberately supports a bounded message subset and has **no CRC-extra/decoder entry for `RADIO_STATUS (109)`** (`MavlinkParser.crcExtraFor`), so its zero supported count cannot by itself refute QGC's radio-status observation. Raw magic-byte and unsupported-ID counts are unvalidated candidates; checksum failures may include false starts while resynchronizing. Do not conclude baud or electrical corruption from these totals alone.

The operator additionally reports that Mission Planner displays a live Cube and changing RC channels on the laptop connection. The current Cube readback remains `SERIAL2_PROTOCOL=2`, `SERIAL2_BAUD=57`. The operator later reports R12 `RX2`, `TX2`, GND, 5V and SBUS are used, with `TX2 → Cube TELEM2 RX` and `RX2 → Cube TELEM2 TX`; R12 UART2 at 57,600 and forwarding enabled were answered “yes.” Treat this as operator-reported configuration and wiring, not an independent continuity or forwarding measurement. It supersedes the older request to ask which UART is used, but does not prove valid Cube frames reach the phone.

**Next isolated, read-only comparison:** with the same identified T12 Bluetooth device and no laptop USB/other vehicle link feeding QGC, have the hardware team confirm whether a Cube-origin `HEARTBEAT (0)` appears at the Cube TELEM2 output and at the R12 UART2 input/phone side, using their documented safe bench method. Record link/device, source system/component, wire version, and a short count/rate at each point without storing raw packets or device addresses in Git. If valid heartbeat exists at TELEM2 but not R12 UART2, investigate the cable/connector and electrical interface with power off before handling wiring; if R12 receives it but QGC on Bluetooth does not, investigate R12/T12 forwarding; if QGC sees it but Mama GCS still does not, compare the same phone-side session and investigate parser/transport/source filtering. If TELEM2 itself lacks heartbeat, review current Cube stream configuration read-only. No parameters or wiring should be changed based on these screenshots alone.

Software follow-up, if separately approved: support checksum-validated `RADIO_STATUS` as a clearly labelled **radio-only** observation, without allowing it to select an autopilot or satisfy heartbeat/RC gates. This would improve diagnosis, not repair a missing Cube heartbeat. Phase 49 disposable-emulator visual acceptance remains separate.

## Current hardware record — updated 21 October 2026

Latest bench follow-up: **21 October 2026** — the operator confirms the receiver telemetry cable is on Cube **TELEM2** and the separate receiver SBUS lead is on **RCIN**. Supplied photos show a plug in TELEM2 and the Multi Link V1.0 receiver's UART1 (RX1/TX1), UART2 (RX2/TX2), and SBUS labels. The connector obscures its pin order, so the selected UART pair, TX/RX crossover and ground continuity remain unverified. Do not infer them from wire colors or the cable tag.

The same-day physical-phone Classic session (`T12_131`) was actively receiving bytes: 202 chunks, 53,084 bytes, 2,392 MAVLink-1 start-byte candidates, 0 MAVLink-2 start-byte candidates, 0 supported messages decoded, 782 checksum failures, 1,597 unverified unsupported-ID candidates, and input received 0 seconds ago. The app showed `Link lost · Setup` and `No sample received`. This proves only a live byte stream reached the app; it does not validate Cube telemetry, heartbeat or RC values.

Latest live configuration evidence: a user-supplied **21 October 2026** Mission Planner Full Parameter List screenshot shows `SERIAL2_PROTOCOL=2` and `SERIAL2_BAUD=57`. ArduPilot's parameter codes mean MAVLink 2 at 57,600 baud. This is a read-only current Cube readback for TELEM2; the Mission Planner connection selector (`COM10 / 9600`) is a separate laptop-to-Cube connection and does not change the TELEM2 value.

The supplied and user-accepted `Current_Parameters_16_07_2026.param` instead has `SERIAL2_PROTOCOL=11` and `SERIAL2_BAUD=256`. Preserve it as an older accepted file baseline, but do not use those two SERIAL2 values as current live configuration and do not load/write it to reconcile the difference. The remaining unknown is the receiver side: selected R12 UART, TX/RX/GND mapping and its serial forwarding configuration. The file's `SERIAL1_PROTOCOL=2`, `SERIAL1_BAUD=57` values remain for SERIAL1/TELEM1, not the operator-confirmed TELEM2 cable.

Latest operator confirmation: **13 October 2026** — live Cube telemetry reception in Mama GCS through HC-05 → T12 → R12 → Cube is user-confirmed. This is not an independently observed hardware test or proof of command transmission, stopping behaviour or speed-limit enforcement.

Latest supplied bench evidence: **19 October 2026** — a Classic session screenshot shows 120,221 input bytes, 455 chunks, zero decoded supported messages, 419 checksum failures and 643 unsupported-ID candidates. RC values are not received. This does not confirm usable current Cube telemetry or identify the cause; arbitrary or corrupted data can produce parser candidates. A separate screenshot shows an open socket waiting for recent bytes, so do not combine those moments into a single live result. Retain the earlier reception report as historical, not as acceptance of this failing session. Current work must first establish valid supported MAVLink and a selected Cube heartbeat, then fresh RC_CHANNELS.

Use this record before requesting hardware information. The operator explicitly asked that already supplied answers not be requested repeatedly. Older phase reports describe evidence available at the time; their requests for a remote model, parameter file or maximum speed are superseded by this record. Do not ask the operator to repeat these facts unless they report a change or genuinely new evidence conflicts with them. User-reported configuration and photo observations are not physical safety certification.

### Supplied information — retain as the baseline

| Item | Current record and evidence |
| --- | --- |
| Cube configuration | The operator explicitly confirms `Current_Parameters_16_07_2026.param` is correct and instructs us to follow it. Use it as the accepted file baseline despite its filename date; do not keep requesting a new export or reconfirmation. The original export is not bundled in the repository. |
| Autopilot | Cube Orange family with ArduPilot Rover. The operator previously reported version **4.7.0**, then described it as “latest.” Exact currently installed build remains unverified. The CubeOrangePlus upload-selection screenshot is not installed-board/version evidence. Prefer passive `AUTOPILOT_VERSION` or a connected Mission Planner observation when hardware becomes available, rather than repeatedly asking for the version. |
| Remote | **Skydroid T12**. This supersedes the earlier FlySky i6 description of the current phone/remote path. |
| Receiver | **Skydroid R12, Multi Link V1.0**. |
| Phone-facing Bluetooth name | Initially **HC-05**, later **T12_485** confirmed as the T12 remote (seen in both Classic paired devices and BLE discovery), then the operator identified **t12_131** for the later bench test. Use the device identified for the current setup; do not assume all names are the same physical remote or that a name proves protocol. Keep these separate from the VESC adapter. |
| Intended communication route | Phone → HC-05/T12 remote → R12 receiver → Cube. MAVLink is the reported telemetry protocol. On 13 October 2026 the operator confirmed live Cube telemetry reaches Mama GCS through this route. Retain as user-confirmed reception, not an independently observed acceptance test or proof of commands reaching the Cube. |
| Cube telemetry connector | On 21 October the operator confirmed **TELEM2 → receiver UART** and separate **SBUS → RCIN**; the latest Cube photo shows TELEM2 occupied. Use TELEM2 as the current user-reported route. Which receiver UART pair and the exact TX/RX/GND mapping are not established by the photo. |
| Remote-control lead | **SBUS → Cube RCIN**, user-confirmed and consistent with the separate yellow/black lead in the photos. |
| Motor driver | **Flipsky Dual 75100 with aluminum PCB**, reported hardware version **“48”**. PWM is the reported control connection from the Cube. Exact firmware and hardware-version identification are not independently verified. |
| Motor-data adapter/path | Operator identifies **Flipsky Bluetooth 2.4G wireless V6 NRF51** and reports motor data comes through Cube MAVLink. Retain those reports; actual VESC feedback wiring, message mapping and live motor measurements have not been demonstrated. PWM control alone does not establish feedback. |
| Maximum permitted speed | **8 km/h (approximately 2.22 m/s)**, explicitly reconfirmed by the operator. Do not ask for this number again. Enforcement remains unverified. |

### Accepted parameter values

These replace conflicting earlier verbal output assignments and calibration numbers for software review. They are file configuration, not proof of the physical motor direction or stop response.

- `SERVO1_FUNCTION=73`: configured left throttle; `SERVO2_FUNCTION=74`: configured right throttle.
- Both outputs: `MIN=1100`, `TRIM=1500`, `MAX=1900`, `REVERSED=1`. Do not relabel minimum/maximum as physically verified forward/reverse commands.
- `RCMAP_ROLL=1`, `RCMAP_THROTTLE=3`.
- The supplied file has `SERIAL1_PROTOCOL=2`, `SERIAL1_BAUD=57` (**57600 baud**), `SERIAL2_PROTOCOL=11`, and `SERIAL2_BAUD=256`. Since the reported cable is on TELEM2/SERIAL2, do not apply the SERIAL1 values to it. A later live Mission Planner readback now shows current `SERIAL2_PROTOCOL=2` and `SERIAL2_BAUD=57` (MAVLink 2 / 57600), so retain the file only as an older configuration reference and do not write it. Receiver-side UART baud and framing remain unverified.
- `FS_GCS_ENABLE=0`, `FS_THR_ENABLE=0`, `RC_OVERRIDE_TIME=3`. These do not demonstrate a guaranteed stop after phone or radio loss.
- `CRUISE_SPEED=4` (4 m/s, 14.4 km/h) conflicts with the confirmed **8 km/h maximum**. `SPEED_MAX=0` does not establish enforcement of that maximum. Record this as unresolved safety/configuration work, not permission to exceed 8 km/h or silently alter the Cube file.

### Still unanswered or awaiting tests — ask only when needed

1. No additional baud-rate answer is requested: use the accepted **57600 baud** baseline. Investigate receiver/remote settings only if an actual connection failure or new contradictory evidence makes that diagnostic necessary.
2. Earlier telemetry reception was user-confirmed, but the latest supplied bench session has no validated supported messages. Diagnose that specific receive path with read-only evidence rather than repeating model, parameter-file or speed questions. Record actual current-session heartbeat and RC_CHANNELS when available. Neither an open Bluetooth socket nor a BLE stability pass validates Cube reception, outbound commands or safety behaviour.
3. Neutral brake/coast response; phone/app loss and radio-loss behavior; operator manual-takeover switch and procedure; physical emergency-stop presence and effect. These require hardware-team evidence from a secured setup, not a moving-rover disconnect experiment.
4. How the confirmed 8 km/h maximum will be enforced and the accepted file's higher cruise target reconciled. The numerical maximum is already answered.
5. For later motor monitoring: actual VESC feedback connection, controller firmware/identities and demonstrated telemetry fields. Do not ask again for the reported controller or adapter model.

This record is project memory only. It changes no firmware, parameters, vehicle profile, command permissions or safety-gate evidence. Movement and app emergency-stop functionality remain unavailable pending validated integration.

## General integration checklist

Phase 2 adds an optional, manually configured fixed-peer UDP socket. The app starts without an endpoint, vehicle selection, MAVLink session, or assumed controller inventory. Opening this socket only receives raw bytes; it cannot operate the UGV.

## Facts needed before adapters or commands

| Subsystem | Information required |
| --- | --- |
| ArduPilot Rover | Autopilot/firmware version, MAVLink dialect/version, system/component IDs, telemetry rates, supported modes and commands, signing policy, radio/network topology |
| UDP / Bluetooth | Actual endpoint and local ports, trusted network boundary, reconnect expectations; Bluetooth module model, BLE vs Classic transport, service/characteristic or serial profile, framing and pairing policy |
| VESC | Controller model/firmware, number and identity of controllers, CAN/UART wiring, protocol version, baud/bitrate, direct vs gateway vs autopilot telemetry route, documented message mapping |
| Motor measurements | Sensor availability, temperature sources, RPM vs electrical RPM, motor pole pairs before conversion, gearing and wheel dimensions before speed conversion, fault definitions |
| Spray | Pump/output/controller mapping, nozzle count and addressing, normally-open/closed behavior, pressure/flow sensor availability and units, priming/dry-run protection, interlocks and confirmed-off feedback |
| Hydraulics | Controller/protocol, valve and pump mapping, pressure/temperature feedback, neutral/de-energized behavior, limits, mechanical interlocks and emergency-stop behavior |
| Battery / sensors | Telemetry source, pack configuration, available measurements, calibration, units and machine-specific thresholds |
| Safety | Physical emergency stop, motor/brake response, link-loss failsafe, deadman timeout, spray/hydraulic safe behavior, authority arbitration and a controlled test procedure |

VESC telemetry is **not presumed to exist in MAVLink**. It may arrive through an independently authenticated transport, a documented controller gateway, or a supported autopilot mapping. `VescRepository` is separate from `MavlinkRepository` for this reason. No CAN frames, UART packets, relay/servo numbers, nozzle count or hardware thresholds have been invented.

### Candidate VESC hardware supplied by the operator

The paragraphs below preserve the earlier reference basis. The current record above now includes the operator's V6 NRF51 adapter identification; do not interpret the older “module model not verified” wording as a reason to ask for the model again. Independent protocol/path validation is still pending.

The operator supplied a [manual page for the Flipsky Dual 75100](https://manuals.plus/ae/1005004267433267) and reports the installed controller is the **Dual 75100 with aluminum PCB**, hardware version **"48"**, and is connected by Bluetooth. These are operator-reported identifiers; the exact meaning/format of "48" still needs confirmation from a VESC Tool identification screen or board label. Flipsky's [Dual 75100 product page](https://flipsky.net/products/flipsky-dual-75100-with-aluminum-pcb-based-on-vesc-for-electric-skateboard-electric-scooter-ebike-speed-controller) lists USB, CAN and UART interfaces and two motor channels. The installed Bluetooth module model, BLE vs Classic protocol, and pairing behavior are not yet verified. This does not establish firmware, controller/CAN IDs, telemetry format or a phone-reachable data path for this app. Flipsky describes its [nRF51 module](https://flipsky.net/products/bluetooth-module-2-4g-wireless-based-upon-the-nrf51_vesc-project) as BLE over a VESC UART configured at 115200 baud; do not assume this is the installed module.

The operator reports that the hardware team uses a VESC mobile app to view controller data. This indicates that data is available through their existing tool, but does not by itself identify the app/version, all visible fields, the Bluetooth module/protocol, or a data interface Mama GCS can use. VESC Tool is a separate client; Mama GCS will need its own compatible, documented receive connection and must not infer controller state from the other app.

Before implementing a live read-only VESC adapter, obtain: (1) photos of the installed board label, Bluetooth module label and connector wiring; (2) VESC Tool screenshots showing the exact hardware-version string (to clarify "48"), firmware identification for each side, and any CAN/controller IDs; (3) confirmation that VESC Tool on this Android phone can connect to this module and read both controllers, plus the Bluetooth device name and module type; and (4) an example telemetry capture from both sides with field definitions and update rates. The app now has a bounded BLE-only discovery screen under Systems → VESC Bluetooth; run it while VESC Tool is disconnected and report a likely device name and advertised service UUIDs. An empty result does not exclude Bluetooth Classic / SPP or a non-advertising/connected BLE module. See [Phase 8a](phase-8a-report.md). The Flipsky page warns that phase filtering is unsupported on this model and cautions against upgrading factory firmware; do not change ESC firmware or configuration as part of app integration without hardware-team validation.

## Future command acceptance gates

Before any phase enables commands, require a trusted authenticated session, backend/use-case authorization, provisioned vehicle identity, supported capability and verified hardware mapping, fresh relevant telemetry, correct mode/interlocks, explicit command outcome handling, and confirmation of resulting state. A write/send or ACK alone must not be described as a physically confirmed stop or actuator state.

Link health is not subsystem health. Every telemetry adapter will need per-source timestamps, units/range validation and freshness policies. The current screen projection hides all values when the selected vehicle connection is not `CONNECTED`; this conservative Phase 1 behavior is not a substitute for per-subsystem freshness later.

The disabled emergency-stop UI cannot replace the independent physical safety system. No field operation is supported by this build.

## Phase 7 drive gate foundation

`DriveSafetyGate` now expresses the minimum evidence categories as a pure deny-by-default policy. Its default snapshot satisfies no checks, and the Control screen only renders the checklist. There is still no trusted identity provider, permission-bearing command use case, signed MAVLink verifier, approved vehicle profile, command route, deadman lease owner, or actuator/stop command sender. The checklist is not a readiness verdict, does not grant authority, and must not be populated from operator-editable UI state. Continue to keep all movement, arming, and stop controls unavailable until the facts above are verified for the actual installed UGV.
