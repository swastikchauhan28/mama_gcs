# Rover station UI

Mama GCS uses QGroundControl as a layout reference, not as a feature checklist. The operating workspace is map-first and keeps essential rover state visible while leaving MapLibre attribution and controls unobstructed.

## Workspaces

- **Operate rover** — full map, compact route/drive/systems rail, ground speed, heading, battery, GPS and rover-heading dial.
- **Plan route** — local route map with separate Route, Field outline and Review tabs plus a collapsible file/library section.
- **Rover configuration** — observed vehicle summary, drive safety, health, drive motors, spray and hydraulic equipment.
- **Analyze tools** — received instruments plus link, decoder and autopilot-message diagnostics.
- **Application settings** — General, UDP link, Bluetooth/BLE, Maps, and Accounts & audit categories.

## Rover adaptations

Aircraft-only actions such as takeoff, landing, altitude planning, VTOL transitions, ADS-B and Remote ID are not exposed. Rover ground speed, heading, direction, route distance, motors, spray and hydraulics take their place. Route drafts and field outlines stay explicitly local until a real mission protocol is implemented.

## Safety boundary

The UI never turns received telemetry into permission to drive. Forward/reverse, steering, speed, arm/disarm, mission execution, spray, hydraulics and app emergency stop remain disabled until their command paths and safety requirements are implemented and validated. The persistent safety strip directs operators to the independent physical safety system.

## Responsive behavior

Landscape shows category sidebars and a wide planning editor. Portrait uses horizontally scrollable category chips and a compact instrument drawer. Daylight is the default for new installations; Dark and System modes remain available and existing saved preferences are preserved.
