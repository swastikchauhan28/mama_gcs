# Phase 8 VESC telemetry groundwork

Date: 2026-10-05. Scope: prepare a read-only VESC measurement boundary without claiming that a VESC controller is connected.

## Implemented

- A provisioned inventory of controller IDs and vehicle-specific freshness timeout is required to create a telemetry accumulator. No controller count, identity or timeout is guessed by the app.
- Decoded samples are admitted only for IDs in that inventory. Empty samples, non-finite values, negative input voltage, out-of-range normalized duty ratio, negative receive times and out-of-order samples are rejected. Mechanical RPM and electrical RPM remain separate; there is no pole-pair or wheel-speed conversion.
- A stale controller reports `STALE` with its last receive time but no retained measurements or fault assertion. Session clear removes all measurements. The motor panel also suppresses values for every non-connected controller.
- The motor screen explains that there is no live VESC input or motor command route. Its future VESC state is independent of the MAVLink heartbeat display filter.

## Not integrated

The accumulator does not discover controllers, parse CAN/UART/VESC packets, authenticate a gateway, subscribe to a transport, or publish into the live `VehicleState`. The current app therefore still displays VESC telemetry as unavailable. No motor output, brake or emergency-stop command was added. The telemetry source must be validated before using these models for decisions; a finite numeric sample is not proof of a healthy or correctly identified motor.

## Hardware facts needed for the live adapter

Supply the exact VESC/controller model and firmware version for each motor; controller count and stable ID mapping; CAN/UART/gateway topology and wiring; protocol documentation and telemetry message/rate mapping; whether mechanical RPM is reported or requires pole pairs; sensor availability and fault-code definitions; and a measured freshness interval for each route. See [hardware integration gates](hardware-integration.md). Once confirmed, implement and test a source-specific read-only adapter before any command path is considered.
