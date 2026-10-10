# Phase 18 — Read-only Cube parameter-file review

## Delivered

Rover configuration → Parameter file opens a local `.param` / `.parm` text export using Android's document picker. No vehicle connection, account escalation or storage-wide permission is required. The importer reads the selected document only; it never writes to it, persists its URI permission or uploads its contents.

- Accepts UTF-8 two-column `NAME,VALUE` or whitespace-separated `NAME VALUE`, optional BOM, blank lines and `#` comments. Decimal/scientific and unsigned 32-bit hexadecimal literals are displayed exactly as imported. Five-column QGC exports and firmware-specific text/string values are unsupported.
- Rejects malformed encoding, control characters, invalid parameter names, duplicate names (even identical duplicates), non-finite values and extra columns. Parsing is atomic: a failed replacement keeps the previous snapshot with an explicit warning.
- Bounds input to 512 KiB, 10,000 parameters, 1,024 characters per line and 64 characters per value. Reads/decoding run off the UI thread; streams close on success or failure. Clearing or replacing an import prevents a cancelled job from publishing old results. A provider blocked in synchronous I/O may remain blocked until that provider returns; cancellation is not a guaranteed provider timeout.
- Provides name search, six name-based categories, original line numbers, raw values and an exact-byte SHA-256 fingerprint. The fingerprint is not authentication. Categories cover motor outputs, remote inputs, telemetry ports, safety/failsafes, speed/steering and other values.
- Retains the review in the navigation ViewModel's memory only. Rotation can retain it; leaving the workspace or process death can discard it. Clear review removes only the in-memory snapshot, never the original document.

## Safety boundary

This is file evidence, not a live parameter download, firmware identification, completeness check or approved vehicle profile. Missing entries are not replaced with defaults. No parameter writes, telemetry changes, motor control, pairing or safety-gate changes are included. The review is not connected to vehicle state or command admission.

The hardware team's current full export and exact firmware version are still needed. Compare motor output settings with the reported MAIN OUT 1 right / MAIN OUT 2 left wiring. Remote input values of 1000/1500/2000 must not be treated as confirmed ESC calibration. Logical serial port settings cannot prove which physical telemetry bridge is attached, and speed settings do not establish enforcement of the reported 8 km/h limit. Physical emergency stop and link-loss behavior still need independent validation.

## How to check

1. Have the hardware team save the existing Cube parameter configuration to a text file without changing it. Copy it to the phone.
2. Open Menu → Rover configuration → Parameter file → Import .param. Select the file. Opening an external picker backgrounds the app and therefore closes foreground-only telemetry links; reconnect manually if needed.
3. Confirm the displayed raw values and line numbers match the file. Search `SERVO`, `RCMAP`, `SERIAL` and `FS_`, and try the category filters.
4. Import a malformed or duplicate-name file: an error should appear and the previous file remain visible. Cancel the picker: no replacement should occur.
5. Clear review: the values disappear but the original file remains available in the file manager. Controls remain unavailable throughout.

## Validation

The debug app and instrumentation APK compile. All 150 JVM tests pass with zero failures, errors or skips, including 11 new parser tests and four ViewModel tests. These cover accepted formats, exact raw values, duplicates, non-finite values, unsupported formats, bounds, stream closure, categories, fingerprints, replacement failure and cancellation. Two new Compose fixture tests compile but were not executed on a device in this phase. Debug lint passes with zero errors and 29 warnings; none reference the new parameter-review files. `git diff --check` passes.

Verified with JDK 17: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`.

Device document-picker acceptance, visual layout acceptance and real Cube-file compatibility remain unverified; no rover connection or commands are part of these checks. The earlier Phase 17 emulator system failures are not treated as passing UI evidence. No physical phone or existing emulator was modified in this phase.

## References

- [ArduPilot pymavlink parameter text loader/save format](https://github.com/ArduPilot/pymavlink/blob/master/mavparm.py) — the app deliberately rejects malformed/duplicate input instead of skipping or overwriting it.
- [Rover motor and servo configuration](https://ardupilot.org/rover/docs/rover-motor-and-servo-configuration.html).
- [Logical serial port configuration](https://ardupilot.org/rover/docs/common-serial-options.html).
