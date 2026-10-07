# Phase 12 ArduPilot Rover instrument telemetry

Date: 2026-10-07. Scope: receive-only decoding and display of the Rover-relevant MAVLink `VFR_HUD` fields for the confirmed Cube Orange / ArduPilot Rover configuration.

## Delivered

- Added MAVLink 2 `VFR_HUD` (message 74) CRC and exact payload-length validation.
- Decoded ground speed, heading, throttle percentage, altitude, and climb rate. Airspeed is intentionally not presented as a rover measurement.
- Added a timestamped Rover HUD state and ignore invalid values rather than turning sentinels or non-finite data into readings.
- Routed the new message through the same source-pinned receive-only MAVLink session as other telemetry.
- Added VFR_HUD readings to the vehicle details panel and operator instrument dock.
- Added parser and state-reducer tests for normal and invalid/malformed values.

## Boundaries

This phase does not add MAVLink transmission, vehicle controls, mission upload, BLE GATT connection, or VESC data. HUD readings are reported instrument values, not proof of actual motor output or a safe operating state. Ground speed and heading may also arrive from `GLOBAL_POSITION_INT`; this phase keeps the VFR_HUD source separately visible to avoid silently mixing data sources.

## Validation

Unit tests and Android build/lint are run for this phase. A physical Cube Orange/BLE integration test remains pending the Bluetooth module identity, BLE GATT service/characteristic UUIDs, Cube telemetry-port wiring, and firmware version.
