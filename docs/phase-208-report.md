# Phase 208 — Read-only RC mapping check

## Scope and result

The operator approved a read-only check of the T12/Cube channel mapping after Phase 207. On 19 November 2026, the USB-debugged phone was connected to T12_485 by Bluetooth Classic. Mama GCS showed a connected selected-autopilot session and 16 recently received RC channels. The operator reported motor/ESC outputs disconnected and the rover secured. No Cube parameters, radio settings, wiring, vehicle commands or saved app data were changed.

The app's receive-only RC activity check was started with the operator reporting centred sticks. The operator then reported one steering left/right sweep with switches and the other stick untouched. The stopped observation recorded RC1=0 µs span, RC2=0, RC3=145, RC4=0, RC5=0, RC6=450, RC7=450 and RC8=450. A separate held-left snapshot showed RC5 and RC7 at 1951 µs while RC8 was 1501 µs. These observations conflict with treating Phase 207's single RC8 snapshot as a unique steering-channel assignment. The activity check reports sampled variation, not which physical input caused it; the source of the multiple changes is unverified. Local ignored screenshots: `app/build/phase208-steering-left-held.png` and `app/build/phase208-steering-stopped.png`.

The accepted older parameter file lists `RCMAP_ROLL=1` and `RCMAP_THROTTLE=3`. Those are historical file values, not a current Cube readback. The operator said Cube USB and Mission Planner were ready, but no current `RCMAP_` values or screenshot was received before the session was interrupted. On 20 November, neither Mission Planner nor the USB-debugged phone/Cube was visible. ArduPilot's Rover documentation says `RCMAP_ROLL` and `RCMAP_THROTTLE` choose the steering and throttle input channels: https://ardupilot.org/rover/docs/common-rcmap.html . No setting is inferred or changed from this reference.

## Status and next safe check

**Incomplete:** the current Cube mapping and actual steering/throttle assignment remain unverified. When the setup is available, read `RCMAP_ROLL`, `RCMAP_THROTTLE`, `RCMAP_PITCH`, `RCMAP_YAW` and relevant `RCx_OPTION` values from the connected Cube without writing them; compare with a fresh, single-control-at-a-time RC activity check. Keep outputs isolated. Do not label an RC channel as a vehicle control or arm/move the rover based on the screenshots. Baseline commit `752fcf6`; Git was clean at the start of this read-only check. No software tests were run because no app code changed. This report is documentation only.
