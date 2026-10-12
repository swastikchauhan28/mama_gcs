# Phase 21 — Read-only RC input and PWM output monitor

## Delivered

Analyze tools → RC & PWM shows the selected autopilot's received RC_CHANNELS (65) and SERVO_OUTPUT_RAW (36) data. Existing UDP, BLE and Bluetooth Classic receive paths share the decoder; both MAVLink wire versions are supported for these messages. It is an observation tool, not a control/calibration page.

- RC: first 18 raw channels in microseconds, reported channel count, and device-specific raw RSSI. The reported count can exceed 18. Unused UINT16_MAX channels and channels beyond the count are unavailable; count zero does not produce invented neutral inputs. RSSI 255 is unknown and other values are not converted to percentages.
- PWM: numbered output fields 1–16, with independent samples keyed by the reported port. At most the first four distinct ports are retained per session; subsequent updates to retained ports continue. No MAIN/AUX, left/right, steering or throttle mapping is inferred.
- MAVLink 1 carries only the first eight output fields. MAVLink 2 extension fields 9–16 restore partial/trailing-zero bytes; zero, omitted and unavailable extensions cannot be distinguished and are labeled ZERO / NOT PROVIDED. Base-field zero remains a raw value, not proof of an inactive actuator.
- CRC validation, signed-frame rejection and selected system/component filtering apply before these samples update the repository. Data before autopilot heartbeat selection or from another source is ignored.
- The monitor updates its display age every 500 ms. It hides numbers when the link/heartbeat is not current, the sample predates the session, the clock is inconsistent, or the sample is older than five seconds. This is a display policy, not a hardware timeout or failsafe. No sensor/actuator readiness is inferred.
- New sessions clear both sample collections. Disconnect retains historical samples in memory but hides their values in this monitor. There is no sample recording or persistence.
- The existing telemetry report adds RC and per-port PWM receipt times only; channel values and RSSI are excluded. The report remains previewed and saved only on explicit request.
- The screen uses the station's existing responsive cards, port selector and scrollable layout. No new dependency or permission is added.

## Boundaries

This does not verify receiver wiring, motor mapping, neutral calibration, physical PWM at an output pin, wheel movement, stop behavior or remote takeover. The reported 1000/1500/2000 remote values are not assumed to be ESC calibration. These samples do not satisfy the drive admission checklist and cannot enable commands.

The app sends no stream-rate request, heartbeat, parameter write, RC override or servo command. If the sender does not already emit these two messages, the monitor stays NOT RECEIVED. RC_CHANNELS_RAW (35) and ACTUATOR_OUTPUT_STATUS (375) are not added in this phase. SERVO_OUTPUT_RAW is an older message retained here for the installed rover's PWM-oriented diagnostics; support does not prove that the installed firmware emits it.

## Acceptance still needed on hardware

Use only an independently secured, hardware-team-approved stationary test setup. Do not arm or move the rover just to test this screen. The app cannot stop machinery.

1. Connect the identified phone/remote telemetry path and check that an autopilot heartbeat is received.
2. Open Analyze tools → RC & PWM. If absent, have the hardware team verify which messages the approved telemetry configuration already emits; do not guess or change live settings from the app.
3. Compare channel numbers and raw readings against a trusted ground-station inspection of the same Cube. Any physical-input changes must follow the hardware team's test procedure.
4. Check each reported output port independently. Use the actual Cube parameter export and wiring evidence to interpret outputs; the monitor never assigns motors.
5. In a simulator or safely isolated setup, stop only the RC/PWM message stream while keeping heartbeat traffic. Values must hide after five seconds. Closing the link must also hide them; a new session starts empty.
6. Prepare a telemetry report and confirm it contains receipt times, not RC/PWM channel values.

## Validation

Verified on 2026-10-12 with `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`:

- Debug app and Android test APK built successfully.
- All 196 JVM tests passed, with zero failures, errors or skipped tests. The 16 added tests cover parser offsets/lengths/CRC and signed policy, fragmentation and wire versions, repository bounds/reset, source filtering with no transmissions, display freshness, and report privacy.
- Lint completed successfully. The report lists 0 errors and 29 warnings.
- `git diff --check` passed.
- Two new Compose fixture tests compiled but were not executed: no emulator or phone was connected to ADB during verification.

Physical phone, visual layout and rover acceptance remain unverified. No rover connection, configuration change or command was performed. Changes have not been committed or pushed in this phase.

## Protocol references

- [MAVLink RC_CHANNELS definition](https://mavlink.io/en/messages/common.html#RC_CHANNELS).
- [MAVLink SERVO_OUTPUT_RAW definition](https://mavlink.io/en/messages/common.html#SERVO_OUTPUT_RAW).
- Official generated wire layouts and CRC extras: [RC_CHANNELS](https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_rc_channels.h), [SERVO_OUTPUT_RAW](https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_servo_output_raw.h).
