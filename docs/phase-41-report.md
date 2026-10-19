# Phase 41 — Classic serial decoder diagnosis

## Reason

The operator's 19 October bench screenshots showed a powered T12/R12/Cube setup and an open Classic socket. One decoder session reported 120,221 input bytes, zero decoded supported MAVLink messages, 419 checksum failures and 643 unsupported-ID candidates. RC input remained NOT RECEIVED. Those counters establish byte reception, but they do not identify the bytes as Cube MAVLink; unsupported IDs have no known CRC extra in this app and are not validated messages. The screenshots do not prove which physical hop or serial setting caused the failure.

## Changes

- The Classic connection check now distinguishes a current byte stream with no recently checksum and payload validated supported MAVLink from a stream with validated messages but no fresh selected-autopilot heartbeat. The first case displays **BYTES ARRIVING · MAVLINK NOT CONFIRMED** and tells the operator to inspect the T12/R12 telemetry route and serial format before RC stream settings. All evidence expires after five seconds or on a new session.
- The checklist and Phase 40 Classic snapshot include a separate **Recent validated MAVLink** result. Unsupported message IDs are explicitly described as unverified candidates in the live decoder panel and exported reports.
- The parser now rescans after an unsupported-ID candidate because its CRC extra is unknown and its length cannot be trusted. This permits a later valid supported frame to be found even if it appears within an unverified candidate. It does not relax CRC or payload validation for supported messages. No bytes or raw message bodies are logged or exported.

## Bench retest

1. Connect the identified T12 Classic serial device in **Application settings → Bluetooth / HC-05** while the receiver and Cube are stationary and the motor outputs are disconnected.
2. Observe the connection check and **Analyze telemetry → Link & messages** for 15–20 seconds. If bytes increase but validated MAVLink remains NOT CONFIRMED, record the Classic evidence snapshot. The hardware team should inspect the separate TELEM1/R12 UART telemetry path, TX/RX, common ground and matching serial format using the accepted 57,600-baud Cube baseline. Do not infer a fault from LEDs alone or change parameters blindly.
3. Once a supported heartbeat is decoded and accepted, inspect **Message inspector** for `RC_CHANNELS`, then move one stick at a time in **RC & PWM**. Only at this later stage investigate the Cube's RC stream rate if `RC_CHANNELS` is absent.

The BLE connection stability screen tests a different transport and cannot supply Classic MAVLink evidence. This phase performs no radio subscription, stream request, Cube configuration write, motor or actuator command.

## Validation

- Added parser recovery and Classic five-second evidence tests; updated UI and report fixtures for the new checklist line.
- `git diff --check` passed.
- Gradle unit-test execution was attempted, but the sandbox denied access to the existing Gradle cache. Escalated execution was not performed because automatic approval review reported capacity failure. The unit tests and Android UI tests still need to run before installing this phase on the phone.

This is a receive diagnosis, not a physical wiring certification or proof of rover control, stop behaviour or speed enforcement.
