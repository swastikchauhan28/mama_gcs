# Phase 2 transport report

Date: 2026-09-29. Scope: transport foundation only. No vehicle command, active MAVLink session, telemetry decoder, background link, Bluetooth socket, serial socket or hardware deployment was added.

## Implemented

- A persisted `UdpEndpoint` containing only a remote host, remote port and local port. There is no built-in vehicle endpoint. Invalid endpoints are rejected before storage.
- Settings UI for entering, saving, clearing, opening and closing a UDP endpoint. The UI reports socket lifecycle and raw packet counters with language that distinguishes an open socket from a connected vehicle.
- `TransportConnectionManager`, owned by the connection ViewModel, creates at most one explicit UDP transport. It requires an explicit close before a different peer can be opened, has no implicit reconnect, and closes on application background/owner destruction.
- Hardened `UdpTransport`: connected peer socket, received-peer filtering provided by `DatagramSocket`, full UDP payload buffer, outbound packet-size check, terminal close state, atomic statistics updates, and generation checks so stale receiver jobs cannot overwrite newer state.
- Transport-specific `TransportStatus` (`DISCONNECTED`, `CONNECTING`, `OPEN`, `ERROR`) separate from vehicle/MAVLink status. An open socket does not make `VehicleState` connected.
- Bluetooth Classic and serial remain byte-link contracts that explicitly return unsupported. They do not request permissions, pair devices, discover hardware, or open sockets.

## Architecture and lifecycle

`SettingsRepository → DataStore → ConnectionViewModel → TransportConnectionManager → VehicleTransport → UdpTransport` is the transport-configuration path. The UI never accesses sockets. `MavlinkSession` remains unconstructed from this flow; Phase 3 must define its ownership and peer/signing rules before any parser is attached.

The manager owns a single transport inside a ViewModel scope and observes its `StateFlow`. Application foreground lifecycle invokes explicit disconnect on stop. This is intentionally not a background communications service. The socket cannot be reopened after a terminal `close`; a new manager creates a fresh transport for a later foreground session.

## Security and safety

Opening UDP proves only local socket creation. It does not prove vehicle identity, MAVLink framing, radio health, authority, user permission, actuator state or emergency-stop behavior. A connected `DatagramSocket` only receives from the configured host/port, which is a useful input boundary but not cryptographic authentication. No packet is sent by the transport settings UI.

The default buffer accepts a complete UDP payload up to 65,507 bytes so the raw byte stream is not silently truncated by the transport. A bounded shared-flow buffer drops raw packets when no Phase 3 consumer subscribes; packet counters still record receipt. This is intentional while no MAVLink session is active and must be revisited with decoder backpressure policy.

## Validation

Validated on 2026-09-29 with `:app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`.

- Unit tests: 25 passed.
- Lint: completed without errors.
- Emulator UI tests: 5 passed.

New unit coverage includes endpoint validation, UDP send/receive/close behavior, transport-manager ownership/error cleanup, and explicit Bluetooth/serial unsupported behavior.

## Hardware assumptions and next phase

The actual UDP peer address and ports remain operator-provided configuration, not a default Mama UGV assumption. No Bluetooth or serial module, VESC path, ArduPilot endpoint, MAVLink signing key, radio topology or source port mapping has been assumed.

Next is Phase 3, MAVLink lifecycle and decoding: define how a selected transport supplies a trusted MAVLink session; validate framing/version/signing policy; map valid heartbeat state without equating socket open to vehicle health. Do not enable controls in Phase 3 without the later authorization and hardware safety gates.
