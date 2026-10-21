# Phase 45 — Classic RC receive evidence

## Goal and scope

Show fresh, selected-autopilot `RC_CHANNELS` receipt separately in the Classic connection checklist and explicit Classic evidence report. This is a read-only observation of the existing bounded in-memory telemetry state. No parser, source filter, transport, persistence, parameter, radio or command behavior changes.

## Receive path and limits

Classic SPP bytes enter the existing MAVLink parser and source selector. An accepted `RC_CHANNELS` updates `VehicleState.rcInput` and its bounded message observation. The checklist reads those timestamps only for the current open Classic session and five-second display window. Its boolean is neither a channel value nor proof of stick movement, receiver wiring, vehicle identity or physical safety. Missing RC data is not automatically a receiver fault.

## Verification — 2026-10-21

- Starting revision: clean `main` at `ced11d690dbdad392bdb4b9ab465266feb59c3d7`. The user subsequently authorized a focused commit and push; Git history and remote status establish the outcome.
- An initial sandboxed Gradle attempt could not access the existing Gradle wrapper cache; it did not run tests. The same command was retried with access to the configured cache:

  `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`

- Result: `BUILD SUCCESSFUL` in 6m 36s, 28 tasks executed and 69 up-to-date. The JVM test task executed; `app/build/test-results/testDebugUnitTest/TEST-*.xml` contains **381 tests in 62 suites, 0 failures, 0 errors, 0 skips**. The Android test APK compiled, but its instrumentation tests were not executed.
- `app/build/reports/lint-results-debug.xml` contains **0 errors and 32 warnings**, matching the recorded Phase 43 warning count. Build and lint outputs are local ignored artifacts, not committed evidence.
- ADB showed no attached device. The available AVD names were not verified disposable; none was launched or modified. Thus the new Compose fixture assertions have compilation coverage only, with no Phase 45 visual screenshot acceptance.
- No live Cube/R12/T12, parameter, firmware, actuator or disconnect test was performed. The October bench screenshots remain historical evidence of a failing undecoded stream, not a fresh Phase 45 result.

## Status and open work

Code and software checks complete, with emulator visual execution and hardware acceptance open. The latest physical stream still has bytes but zero validated supported messages; Phase 45 does not repair or identify that stream. Controls remain unavailable. A safe next step is a separately scoped, hardware-team-secured read-only bench observation using the existing byte, validation, heartbeat and RC stages. Do not alter radio/Cube settings based only on these counters.
