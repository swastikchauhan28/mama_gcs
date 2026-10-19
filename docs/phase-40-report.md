# Phase 40 — Classic HC-05 receive-evidence export

## Delivered

**Application settings → Bluetooth / HC-05** now provides **Capture Classic evidence** for the recorded phone route: Cube → R12 → T12 / HC-05 → phone.

- Capture is available only while this app owns an open Bluetooth Classic session and no UDP/BLE telemetry link competes for telemetry. It does not open a socket, start a decoder, change baud rate, transmit a heartbeat, subscribe, or send any MAVLink/VESC/rover command.
- The memory-only snapshot records the current Classic connection-check stage, checklist booleans, fresh measurement-group labels and bounded decoder aggregate counters. It records the capture time, not raw receipt timestamps.
- A session can be captured even when it has no Cube data. This preserves the distinction between socket-only testing and received autopilot telemetry. It does not turn missing data into a failure of the remote-only connection check.
- Review and Android text-file export require the Classic session and all competing telemetry links to be closed. This avoids trying to keep a foreground-only receive socket open while the system document picker is active.
- Close retains the frozen snapshot; Discard removes it. Picker cancellation, picker failure and write failure retain it for retry. A successful write clears it. It is not restored after process recreation.
- Reports exclude paired-device name/address, MAVLink bytes and payloads, vehicle IDs, coordinates, account data and transient provider errors. The report is capped at 12 KiB and output is explicit UTF-8.

## Boundaries

This is an evidence helper, not a radio trace, trusted pairing record, hardware identity check, parameter readback or command approval. A receiving checklist result cannot prove physical wiring, every radio hop, sensor accuracy, VESC feedback, brake/coast response, physical stop, connection-loss behaviour or enforcement of the 8 km/h maximum. No rover movement, arming, mode, mission, spray, hydraulic or emergency-stop command is introduced.

The currently accepted configuration baseline remains unchanged: the operator-reported route uses HC-05/T12/R12/Cube with MAVLink, the Cube photo indicates TELEM1, and the reference parameter file uses serial 57600. This screen does not test or alter those facts.

## Manual workflow

1. In a hardware-team-secured stationary setup, explicitly open the identified Classic telemetry session as usual. This phase does not connect automatically.
2. Wait until the existing HC-05 connection check shows the state you want to record. A socket-only observation is valid diagnostic evidence but is not telemetry reception.
3. Tap **Capture Classic evidence**. This freezes the displayed receive evidence without changing the link.
4. Disconnect the Classic session and close every other telemetry link. Tap **Review captured evidence**, read its limitations, then save it to an Android document-provider location if needed.
5. Associate the file with the intended hardware session manually before sharing it. Do not treat it as evidence that controls, a stop function or the speed limit work.

## Validation

- `:app:testDebugUnitTest` produced **365 JVM tests with zero failures/errors**, including four report-format/bounds/write tests and four snapshot/export-admission tests added in this phase.
- `git diff --check` passed.
- Final debug APK packaging, Android test APK packaging and lint were requested after the JVM run, but the local Codex approval service rejected the command because its usage limit was reached before Gradle started. This is an environment limitation, not a build result; rerun `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` before release or installation.

No physical radio, Cube, receiver, VESC or rover command is needed or used by the unit tests. Physical document-provider and radio acceptance remain separate work.
