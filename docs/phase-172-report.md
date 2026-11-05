# Phase 172 — TCP telemetry client

Date: 2026-11-05. User requested a TCP connection by IP address and port and then deferred testing until a Cube session. Baseline was clean at `248d93551d0818b127377a3e256e24e403361434`; model `gpt-6.1-terra`. See D-172-01.

## Implementation

The TCP / Wi-Fi settings page accepts an IPv4 address and a port from 1 to 65535 after administrator sign-in. It opens a foreground TCP client and routes the received byte stream through the existing MAVLink session and decoder. The shared telemetry gate prevents concurrent telemetry producers. An open socket is displayed separately from a fresh autopilot heartbeat. The endpoint is held in memory, and disconnect, backgrounding, timeout, EOF or a full receive buffer closes the session; no automatic reconnection or MAVLink command transmission is implemented.

The path is `TcpTelemetryScreen` → activity-owned `TcpTelemetryViewModel` → `TelemetryLinkGate` / `TcpTransport` → `MavlinkSession` → existing parser, router and vehicle state. Activity ownership is intended to keep the link active while navigating to diagnostics. The TCP buffer is bounded at 128 chunks of at most 4096 bytes and is volatile. Android's default network selects the route; Wi-Fi is not explicitly bound. The server IP and port are not persisted or written to the existing UDP endpoint audit. TCP is unencrypted and does not authenticate the server or Cube. Existing MAVLink validation and source selection remain in force.

## Verification and open work

`./gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain` passed after the final code changes: 44 tasks, 9 executed, 35 up-to-date. No unit, emulator, synthetic-server or hardware tests ran at the user's request. Compile success does not establish runtime correctness. Validate fragmented frames, heartbeat reception, disconnect/timeout cleanup, competing links and UI behavior before operational use. A TCP-to-MAVLink Wi-Fi server, its IP/port, Cube serial wiring, baud/protocol and Android Wi-Fi routing remain unverified. A successful heartbeat is protocol evidence, not authenticated identity or physical safety.

Review `TcpTransport.kt`, `TcpTelemetryScreen.kt`, the settings navigation and the security notes. No new dependency or permission was added; the existing INTERNET permission is used. Rollback is a reviewed inverse of this scoped diff; no production-qualified rollback artifact is established. No commit or push is authorized by this implementation request.
