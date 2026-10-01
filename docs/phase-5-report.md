# Phase 5 health evidence and readiness gate

Date: 2026-10-01. Scope: make supported MAVLink telemetry easier to inspect without inventing a health, safety, or machine-readiness verdict. This phase remains receive-only and does not enable any control.

## Implemented

- Added a pure `HealthAssessmentEvaluator` that projects the current vehicle state into transparent evidence: MAVLink heartbeat/liveness, GPS, battery, and autopilot sensor-status evidence.
- Each evidence item is labelled only as `NOT RECEIVED`, `REPORTED`, `REPORTED ISSUE`, or `NOT CONFIGURED`. The app does not call a reported sample healthy merely because it arrived.
- A GPS sample with no fix, unknown fix, or missing coordinates is surfaced as a protocol-reported issue. A MAVLink battery charge state reported as low, critical, emergency, failed, or unhealthy is surfaced as a protocol-reported issue. An enabled SYS_STATUS sensor bit that ArduPilot does not report healthy is also surfaced as a protocol-reported issue.
- Added the Health screen’s **Operational readiness** and **Readiness gate** cards. The readiness state is always `NOT ASSESSED`, with clear blockers: no vehicle-specific health profile/hardware limits, no configured VESC/spray/hydraulic telemetry routes, and no control authority in this receive-only build.

## Boundaries and required inputs

No universal stale timeout, battery voltage limit, GPS quality limit, pressure/flow range, motor temperature limit, or sensor-fault mapping was added. Those values are hardware, battery, installation, and operating-procedure specific. The current MAVLink `SYS_STATUS` bitmap is an autopilot-reported protocol fact; it is not a complete vehicle health evaluation.

Before a later phase can produce a real readiness assessment, the project needs an approved vehicle profile containing at least:

- vehicle and battery configuration, chemistry, minimum/critical voltage or state-of-charge limits, and measurement source;
- expected MAVLink message rates and acceptable staleness windows for each use case;
- meaning of the enabled/healthy autopilot sensor bits for the installed Rover;
- VESC controller inventory, telemetry route, temperature/current/fault limits, and link-loss behavior;
- spray and hydraulic controller mappings, feedback sensor ranges, interlocks, and required confirmed-off behavior; and
- a validated physical safety, authority, and test procedure.

The health page cannot grant control authority, arm a vehicle, or replace the physical safety system. Authentication, verified vehicle identity, command safety gates, and confirmed outcomes are separate future requirements.

## Validation

This phase has not run a new build or device test. A device build/test run is required before release; use the existing Gradle commands in the README. The documented Rover SITL-to-phone setup remains the integration path for visual validation, but its telemetry must not be interpreted as a physical-vehicle health profile.
