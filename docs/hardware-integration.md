# Hardware integration gates

Phase 2 adds an optional, manually configured fixed-peer UDP socket. The app starts without an endpoint, vehicle selection, MAVLink session, or assumed controller inventory. Opening this socket only receives raw bytes; it cannot operate the UGV.

## Facts needed before adapters or commands

| Subsystem | Information required |
| --- | --- |
| ArduPilot Rover | Autopilot/firmware version, MAVLink dialect/version, system/component IDs, telemetry rates, supported modes and commands, signing policy, radio/network topology |
| UDP / Bluetooth | Actual endpoint and local ports, trusted network boundary, reconnect expectations; Bluetooth Classic module/profile, framing and pairing policy |
| VESC | Controller model/firmware, number and identity of controllers, CAN/UART wiring, protocol version, baud/bitrate, direct vs gateway vs autopilot telemetry route, documented message mapping |
| Motor measurements | Sensor availability, temperature sources, RPM vs electrical RPM, motor pole pairs before conversion, gearing and wheel dimensions before speed conversion, fault definitions |
| Spray | Pump/output/controller mapping, nozzle count and addressing, normally-open/closed behavior, pressure/flow sensor availability and units, priming/dry-run protection, interlocks and confirmed-off feedback |
| Hydraulics | Controller/protocol, valve and pump mapping, pressure/temperature feedback, neutral/de-energized behavior, limits, mechanical interlocks and emergency-stop behavior |
| Battery / sensors | Telemetry source, pack configuration, available measurements, calibration, units and machine-specific thresholds |
| Safety | Physical emergency stop, motor/brake response, link-loss failsafe, deadman timeout, spray/hydraulic safe behavior, authority arbitration and a controlled test procedure |

VESC telemetry is **not presumed to exist in MAVLink**. It may arrive through an independently authenticated transport, a documented controller gateway, or a supported autopilot mapping. `VescRepository` is separate from `MavlinkRepository` for this reason. No CAN frames, UART packets, relay/servo numbers, nozzle count or hardware thresholds have been invented.

## Future command acceptance gates

Before any phase enables commands, require a trusted authenticated session, backend/use-case authorization, provisioned vehicle identity, supported capability and verified hardware mapping, fresh relevant telemetry, correct mode/interlocks, explicit command outcome handling, and confirmation of resulting state. A write/send or ACK alone must not be described as a physically confirmed stop or actuator state.

Link health is not subsystem health. Every telemetry adapter will need per-source timestamps, units/range validation and freshness policies. The current screen projection hides all values when the selected vehicle connection is not `CONNECTED`; this conservative Phase 1 behavior is not a substitute for per-subsystem freshness later.

The disabled emergency-stop UI cannot replace the independent physical safety system. No field operation is supported by this build.
