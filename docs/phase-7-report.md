# Phase 7 Drive safety gate foundation

Date: 2026-10-02. Scope: define and display deny-by-default drive-command admission evidence. No vehicle commands are sent and no control action is enabled.

## Implemented

- Added pure `DriveSafetyGate` policy and immutable evidence/check/assessment models.
- Every evidence flag defaults to false. The gate admits an intent only when the full checklist is satisfied; an empty checklist cannot pass.
- Required categories include trusted operator identity and DRIVE permission, provisioned vehicle identity, authenticated MAVLink peer, fresh heartbeat and required telemetry, an approved vehicle safety profile, validated command transport, verified independent physical emergency stop, verified link-loss failsafe, allowed mode/arming conditions, and a live deadman lease.
- The Control screen shows each requirement and explicitly reports the drive path as locked. No operator UI can mark evidence verified.
- Drive buttons and emergency stop remain disabled because there is no trusted identity provider, evidence source, command transport, command sender, deadman implementation, or resulting-state confirmation.

## Boundaries and required inputs

This is safety-gate groundwork, not operational authorization or a vehicle-readiness verdict. The policy is not wired to a drive command boundary, and its evidence must only later be populated by trusted, validated providers. MAVLink signing, replay protection, command ACK/state confirmation semantics, hardware routes, physical safety validation, and trusted user authentication remain unimplemented.

Before command work, establish the facts listed in [hardware integration gates](hardware-integration.md), including the exact Rover firmware/mode policy, authenticated link/signing configuration, deadman timeout and expiry behavior, independent emergency-stop behavior, link-loss behavior, telemetry freshness thresholds, and a controlled test plan for the selected vehicle. Do not infer these values from SITL or from UI preferences.

## Validation

`:app:compileDebugKotlin --offline` passed. No tests were added or run in this step. No emulator, SITL movement test, or physical vehicle test is claimed.
