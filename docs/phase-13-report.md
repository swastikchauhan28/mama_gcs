# Phase 13 BLE MAVLink receive link

Date: 2026-10-07. Scope: user-initiated BLE GATT connection, runtime service/characteristic discovery, and read-only MAVLink telemetry reception through a selected notification/indication characteristic.

## Delivered

- Advertisement results remain transient; an internal device handle maps a scan result to a Bluetooth device without exposing or persisting its MAC address.
- The operator can connect to a selected BLE device and inspect its GATT notification/indication characteristics at runtime. UUIDs are not hard-coded.
- Selecting a characteristic enables its Client Characteristic Configuration Descriptor and forwards each received BLE value into the existing incremental MAVLink parser/session.
- The existing heartbeat validation and source-identity pinning apply to BLE exactly as they do to UDP. The UI distinguishes GATT connected from valid MAVLink telemetry received.
- BLE closes when explicitly disconnected or when the app goes to the background. The screen blocks opening BLE while the UDP session is active.
- BLE transport transmission is deliberately unsupported. No motor, steering, arm/disarm, mission, VESC, spray, or hydraulic commands are sent.

## Limitations and safety

This does not prove the selected characteristic contains MAVLink or that its source is the Cube Orange. Select it only after the hardware team confirms the route. BLE stream chunking is handled by the existing incremental parser; if a module multiplexes unrelated byte streams onto one characteristic, that hardware route must be clarified. The receiver does not pair/bond devices, authenticate a device, verify MAVLink signing, or protect against a malicious peer. The app remains monitoring-only.

VESC motor telemetry remains separate: a MAVLink BLE characteristic does not imply that Flipsky VESC readings are included. Their protocol, controller IDs, and transport path still need confirmation.

## Validation

`testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin` passed. No phone, BLE module, Cube Orange, or SITL BLE-forwarding setup was attached for physical link validation.

## Physical integration still needed

Test with the phone, BLE module, and Cube Orange connected. Confirm the device name, the selected service and notification characteristic UUIDs, Cube telemetry-port wiring, baud rate, ArduPilot Rover firmware version, and that valid autopilot HEARTBEAT plus expected telemetry are displayed. BLE-to-MAVLink SITL forwarding has not been physically tested in this phase.
