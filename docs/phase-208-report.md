# Phase 208 — Read-only RC mapping check

## Scope and result

The operator approved a read-only check of the T12/Cube channel mapping after Phase 207. On 19 November 2026, the USB-debugged phone was connected to T12_485 by Bluetooth Classic. Mama GCS showed a connected selected-autopilot session and 16 recently received RC channels. The operator reported motor/ESC outputs disconnected and the rover secured. No Cube parameters, radio settings, wiring, vehicle commands or saved app data were changed.

The app's receive-only RC activity check was started with the operator reporting centred sticks. The operator then reported one steering left/right sweep with switches and the other stick untouched. The stopped observation recorded RC1=0 µs span, RC2=0, RC3=145, RC4=0, RC5=0, RC6=450, RC7=450 and RC8=450. A separate held-left snapshot showed RC5 and RC7 at 1951 µs while RC8 was 1501 µs. These observations conflict with treating Phase 207's single RC8 snapshot as a unique steering-channel assignment. The activity check reports sampled variation, not which physical input caused it; the source of the multiple changes is unverified. Local ignored screenshots: `app/build/phase208-steering-left-held.png` and `app/build/phase208-steering-stopped.png`.

The accepted older parameter file lists `RCMAP_ROLL=1` and `RCMAP_THROTTLE=3`. Those are historical file values, not a current Cube readback. The operator said Cube USB and Mission Planner were ready, but no current `RCMAP_` values or screenshot was received before the session was interrupted. On 20 November, neither Mission Planner nor the USB-debugged phone/Cube was visible. ArduPilot's Rover documentation says `RCMAP_ROLL` and `RCMAP_THROTTLE` choose the steering and throttle input channels: https://ardupilot.org/rover/docs/common-rcmap.html . No setting is inferred or changed from this reference.

## 20 November 2026 follow-up — current Cube parameter screenshot

The operator supplied a Mission Planner Full Parameter List screenshot showing a connected CubeOrangePlus reporting ArduRover V4.7.2. The displayed values are `RCMAP_PITCH=2`, `RCMAP_ROLL=1`, `RCMAP_THROTTLE=3`, and `RCMAP_YAW=4`. This is current-session screen evidence and agrees with the older file on these four values. It supersedes only the statement above that a current `RCMAP_` readback was missing. The screenshot does not show `RCx_OPTION` values, a radio calibration input display, or concurrent stick movements. It does not establish that the physical steering and throttle sticks are on channels 1 and 3. The phone-side observation recorded no RC1 change and only a small RC3 span while several other channels varied; whether transmitter mixing, receiver mapping, or another input explains the discrepancy remains unverified. No parameter write was requested or made.

## 20 November 2026 follow-up — direct Cube RC input screenshots

Three operator-supplied Mission Planner Radio Calibration screenshots from the connected Cube show changing input values, but do not label the operator's physical action at each capture:

| Capture time | Visible differing values | Other key values |
| --- | --- | --- |
| 10:35:42 | Roll/RC1 1926 µs | Pitch/RC2 1501; Throttle/RC3 1501; Yaw/RC4 1501 |
| 10:35:52 | Pitch/RC2 1951 µs; Yaw/RC4 1273 µs | Roll/RC1 1501; Throttle/RC3 1501 |
| 10:36:26 | Radio/RC5 1951 µs; Radio/RC8 1951 µs | Roll/RC1, Pitch/RC2, Throttle/RC3, Yaw/RC4 all 1501 |

This establishes that RC1 and RC2 can change in the Cube's direct input display in this Mission Planner session, correcting any broader inference from the earlier phone sweep that RC1 never changes. RC3 remains 1501 in these three static captures; that does not prove it cannot change. Physical stick/switch labels, simultaneity of movement, and `RCx_OPTION` values are still missing. Do not assign steering/throttle or change RCMAP from these snapshots alone. The screenshots are operator-supplied evidence, not an app software test.

## 20 November 2026 follow-up — labelled single-control captures

The operator supplied a labelled centred Radio Calibration capture at 10:41:20 with RC1–RC8 all 1501 µs. In the labelled steering-left capture at 10:43:18, RC1 alone among those eight was 1051 µs; RC2, RC3 and RC4 stayed 1501 µs. This supports the reported steering stick reaching the configured `RCMAP_ROLL=1` input. In the labelled throttle-forward capture at 10:44:18, RC2 was 1051 µs while RC1 and RC3 were 1501 µs. Thus the reported throttle stick changed RC2, whereas the current `RCMAP_THROTTLE=3` points at RC3. The captures show input values, not vehicle output, mode, failsafe or movement. They do not show relevant `RCx_OPTION` values or prove every throttle position/mode has the same channel behavior. No calibration or parameter write was requested or observed.

## 20 November 2026 follow-up — RC2/RC3 parameter readback

Connected Mission Planner Full Parameter List screenshots show `RC2_OPTION=0`, `RC3_OPTION=0`, both with `MIN=1100`, `TRIM=1500`, `MAX=1900`, and `REVERSED=0`. `RC2_DZ=0` and `RC3_DZ=30`. No auxiliary option is assigned to either channel in these displayed values. The labelled forward capture's RC2=1051 µs is below the displayed `RC2_MIN=1100`, and lower than trim; the physical forward/reverse effect is not established. ArduPilot Rover documentation describes forward throttle as normally the trim-to-maximum side for a bidirectional throttle and says RCMAP_PITCH/YAW should avoid the chosen steering/throttle channels. See https://ardupilot.org/rover/docs/common-radio-control-calibration.html and https://ardupilot.org/rover/docs/common-rcmap.html . This is a reason to review both channel mapping and transmitter direction/calibration together, not to write one guessed parameter. No setting was changed.

## Status and next safe check

**Partial diagnosis:** labelled screenshots support steering stick → RC1, matching current `RCMAP_ROLL=1`, and throttle stick → RC2, differing from current `RCMAP_THROTTLE=3`. Both `RC2_OPTION` and `RC3_OPTION` are 0, so the displayed options do not resolve the mismatch. Before proposing a parameter change, confirm the throttle axis returns to centre and changes RC2 across its safe test range with outputs isolated, review input direction and calibration, and plan a non-conflicting RCMAP set with the hardware owner. Read current failsafe/mode assignments before any later controlled configuration session. Do not write parameters, arm/move the rover or treat this as output/failsafe verification. Baseline commit `752fcf6`; Git was clean at the start of this read-only check. No software tests were run because no app code changed. This report is documentation only.
