# Hardware integration gates

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

The operator supplied a [manual page for the Flipsky Dual 75100](https://manuals.plus/ae/1005004267433267) and reports that the controller is connected by Bluetooth. Flipsky's [Dual 75100 product page](https://flipsky.net/products/flipsky-dual-75100-with-aluminum-pcb-based-on-vesc-for-electric-skateboard-electric-scooter-ebike-speed-controller) lists USB, CAN and UART interfaces and two motor channels. Bluetooth may be provided by an attached module, but the installed board revision, module model, BLE vs Classic protocol, and pairing behavior are not yet verified. This does not establish controller IDs, firmware, telemetry format or a phone-reachable data path for this app. Flipsky describes its [nRF51 module](https://flipsky.net/products/bluetooth-module-2-4g-wireless-based-upon-the-nrf51_vesc-project) as BLE over a VESC UART configured at 115200 baud; do not assume this is the installed module.

Before implementing a live read-only VESC adapter, obtain: (1) photos of the installed board label, Bluetooth module label and connector wiring; (2) VESC Tool screenshots showing firmware/hardware identification for each side and its CAN ID; (3) confirmation that VESC Tool on this Android phone can connect to this module and read both controllers, plus the Bluetooth device name and module type; and (4) an example telemetry capture from both sides with field definitions and update rates. The Flipsky page warns that phase filtering is unsupported on this model and cautions against upgrading factory firmware; do not change ESC firmware or configuration as part of app integration without hardware-team validation.

## Future command acceptance gates

Before any phase enables commands, require a trusted authenticated session, backend/use-case authorization, provisioned vehicle identity, supported capability and verified hardware mapping, fresh relevant telemetry, correct mode/interlocks, explicit command outcome handling, and confirmation of resulting state. A write/send or ACK alone must not be described as a physically confirmed stop or actuator state.

Link health is not subsystem health. Every telemetry adapter will need per-source timestamps, units/range validation and freshness policies. The current screen projection hides all values when the selected vehicle connection is not `CONNECTED`; this conservative Phase 1 behavior is not a substitute for per-subsystem freshness later.

The disabled emergency-stop UI cannot replace the independent physical safety system. No field operation is supported by this build.

## Phase 7 drive gate foundation

`DriveSafetyGate` now expresses the minimum evidence categories as a pure deny-by-default policy. Its default snapshot satisfies no checks, and the Control screen only renders the checklist. There is still no trusted identity provider, permission-bearing command use case, signed MAVLink verifier, approved vehicle profile, command route, deadman lease owner, or actuator/stop command sender. The checklist is not a readiness verdict, does not grant authority, and must not be populated from operator-editable UI state. Continue to keep all movement, arming, and stop controls unavailable until the facts above are verified for the actual installed UGV.
