# Phase 207 — RC/PWM display timing and live stick check

## Symptom and scope

On 19 November 2026 the operator reported that RC & PWM did not show stick changes. Baseline `0994c2f`, clean `main`; model/version unavailable. The receive-only screen consumes accepted, selected-autopilot `RC_CHANNELS` and `SERVO_OUTPUT_RAW` through `VehicleState`. No parser, source selection, radio setting, Cube parameter, command, permission or persistent data changes were made.

## Cause and change

The USB-debugged phone's live Classic session showed 16 fresh RC channels, but another capture briefly showed `CLOCK INCONSISTENT · VALUES HIDDEN`. `RcPwmScreen` had supplied a 500-ms ticker time to a newer telemetry state. A packet arriving between ticks could therefore appear to come from the future. The panels' future-timestamp guard was working as designed; the screen's display time was stale.

The screen now samples Android elapsed time when telemetry state or the ticker changes and passes that one time to RC, PWM and the RC activity panel. The ticker still expires stale values. The injected clock has a production default and supports a deterministic UI test. See D-207-01. This changes no stored data and adds no network destination or dependency. A single clock read per recomposition is constant-size; samples remain held by the existing bounded vehicle state. Rollback is a source diff reversal from `0994c2f` followed by the same checks; no verified release artifact is designated.

## Verification

- Focused JVM panel tests and both debug APK builds passed.
- Disposable Pixel 3a API 34 emulator: the new between-ticks test passed (`OK (1 test)`); the combined `RcPwmScreenTest,RcInputActivityUiTest` suite passed (`OK (13 tests)`). It verifies immediate new RC/PWM display with the ticker paused and continued rejection of genuinely future timestamps. No hardware link opened in the emulator.
- Full command: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 4m 8s`. Fresh JVM XML: 533 tests in 84 suites, zero failures/errors/skips. Lint XML: zero errors, 27 warnings. The app/test APK tasks were up to date in this final gate after the focused build; the changed sources were compiled in the focused build.
- A data-preserving USB-phone `adb install -r` installed the updated app. In a new T12_485 Classic session, RC & PWM stayed `Connected` and `RECENTLY RECEIVED · NOT VERIFIED`, with 16 reported channels. The operator confirmed outputs disconnected and rover secured, then moved one stick at a time. Steering-left hold: RC8 1951 µs; centred: RC8 1501 µs. Throttle-forward hold: RC2 1051 µs; centred: RC2 1501 µs. Local ignored screenshots under `app/build/rc-pwm-*-held.png` and `app/build/rc-pwm-*-centred.png` show the observations. No vehicle command was sent.

## Limits and next check

This verifies live reported channel changes at the phone, not physical steering/throttle assignment, RCIN wiring, calibration, motor response, failsafe or source identity. The older accepted file lists `RCMAP_ROLL=1` and `RCMAP_THROTTLE=3`, whereas the operator-directed movements coincided with RC8 and RC2 changes. The currently loaded Cube mapping has not been read back, and the T12 stick/channel configuration is unverified. Read `RCMAP_ROLL`, `RCMAP_THROTTLE`, and the relevant radio calibration/channel mapping from the current Cube before labelling these as vehicle controls; do not change parameters based on these screenshots. Review the diff before release. Commit/push only on request.
