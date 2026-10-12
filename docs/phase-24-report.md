# Phase 24 — Read-only hardware file summary

## Delivered

Open **Rover configuration → Parameter file**, import a two-column UTF-8 parameter export, and select **Hardware summary**. Toggle it off to return to raw search and file comparison. Clear review removes both files and the summary selection.

- Presentation-only projection of the current imported snapshot, with original value text, source lines and the existing file SHA-256. Reference files, search/category filters and live vehicle state do not affect the summary.
- Motor signal settings plus logical SERVO1–32 groups when any function, minimum, trim, maximum or reversal field is present. Limited function labels cover 0, 26, 70, 73 and 74; other values explicitly remain uninterpreted. Logical numbers are not asserted to be physical MAIN/AUX connectors, and roles are not verified wheel directions.
- Logical SERIAL0–9 groups when any protocol, baud or options field is present. Limited protocol labels cover -1, 1, 2, 5, 11 and 22. Baud remains its original file code; no hardware route or radio baud agreement is inferred.
- Selected remote-input, arming/disconnect and speed fields. Missing fields explicitly show NOT IN FILE, not defaults. Zero GCS/throttle failsafe-enable values receive a review note; nonzero values do not receive a safety pass. Override expiry is not represented as a guaranteed stop.
- Enum matching accepts exact integral decimal/exponent/hex representations without changing raw evidence. Fractions and overflow cannot be rounded into known enum labels. File-comparison semantics remain exact-text comparison as in Phase 22.
- Imported files remain memory-only. Replacing the current file recomputes the projection; clearing removes it. Existing failed-import retention is unchanged. The user's supplied parameter export is not bundled in source or assets.

## Boundaries

This is a limited ArduPilot reference aid, not a firmware-matched decoder, parameter validator, exhaustive hardware inventory or operational safety assessment. It cannot establish export date, authenticity, the currently installed firmware, physical wiring, VESC telemetry route, motor direction, brake behavior, speed limiting, emergency-stop function or link-loss performance. The rest of the file is available in raw review.

There are no parameter writes, message requests, motor tests, command senders, new connections, safety-profile approvals or changes to drive admission. No phone or rover is needed to use this phase. The earlier unresolved light-theme mission-editor contrast and operation-map load-completion findings are outside this phase.

## Reference basis

- [ArduPilot Rover motor and servo configuration](https://ardupilot.org/rover/docs/rover-motor-and-servo-configuration.html): steering/throttle function identifiers and the distinction between configuration and physical motor direction.
- [ArduPilot serial configuration](https://ardupilot.org/copter/docs/common-serial-options.html): logical port numbering and protocol identifiers.
- [Rover parameter definitions](https://ardupilot.org/rover/docs/parameters.html): GCS/throttle failsafe-enable and RC override concepts. Version-specific behavior must be checked against the actual installed build.

## Validation

- Debug and Android instrumentation APKs built successfully.
- JVM suite: **228 tests, zero failures, errors or skips**, including eight new hardware-summary tests.
- Initial build, unit tests, test-APK build and lint succeeded in 6m 5s. Lint: **0 errors, 30 existing warnings**.
- Final corrected test-APK build and lint succeeded in **4m 13s**, with no new lint warnings.
- Targeted emulator rerun: **7/7 passed, 146.171 seconds**, including both new summary cases and all five existing parameter-screen cases. Evidence: `app/build/acceptance-20261012-160513-1f61a72d/instrumentation.txt` ends `OK (7 tests)`. The full Android suite has not been rerun for Phase 24.
- API 34 isolated emulator `MamaRepair`, serial `emulator-5556`; app/test APKs installed with `adb install -r`. The final run kept this emulator awake to avoid display sleep. Startup again needed the observed System UI Wait action before preflight. No error dialog was dismissed and no additional UI automation client was attached during instrumentation. No physical phone, radio or rover was connected.
- `git diff --check` passed. The temporary emulator was stopped after the successful run. Changes are uncommitted; no firmware or parameter upload was performed.

The first targeted run (`app/build/acceptance-20261012-154938-4347a971/instrumentation.txt`) completed two existing cases successfully, then stalled in the new replacement/clear test. It was explicitly terminated and reports a process crash; this is an aborted run, not a pass. The new tests now scroll to individual reference-label text instead of oversized section cards and have 120-second limits. A diagnostic capture was black, so it does not establish the visible layout or a unique root cause. No behavior assertion was removed. The emulator also required a System UI Wait action before instrumentation, after a startup ANR; no dialog was dismissed during the run.

Automated fixtures cover left/right roles following the file rather than assumed wiring, absent values, unknown/fractional/overflow enum values, exact integral numeric forms, serial labels without connector inference, failsafe caveats, bounded numeric ordering, independent snapshots, current-versus-reference UI behavior and replacing/clearing the current file. Existing parameter review and comparison regressions are retained.

## Manual review

1. Import a local file and select Hardware summary. Check the current-file fingerprint and source warning.
2. Inspect logical outputs. Compare role, reversal and range values with the raw file; do not perform a motor test from this app.
3. Inspect serial and failsafe groups. Missing entries must remain NOT IN FILE. Unknown enums must not become known roles or protocols.
4. Toggle back to raw review, add a reference, then return to the summary. It must still represent only the current file.
5. Replace or clear the current file. Old summary values must disappear. Failed imports should retain the prior file with the existing error message.
