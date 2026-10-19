# Phase 43 — read-only RC input activity check

## Scope

Analyze telemetry → RC & PWM now includes an explicit local observation of up to 18 already source-filtered RC inputs. Start requires an active receive session, selected source IDs, connected state, a fresh heartbeat and a fresh usable RC_CHANNELS sample. Both timestamps must be within the current session, not in the future, and no older than five seconds. This does not authenticate the vehicle.

The latest fresh sample forms the starting baseline. New sample timestamps extend each channel's observed minimum and maximum; span is their difference. Missing/invalid channels do not invent a value or erase earlier historical ranges. The reported channel count is respected, unavailable sentinel values are excluded and no calibrated range or centre value is assumed. These are screen-sample observations, so rapid intermediate changes may be missed.

Link loss, stale heartbeat/RC data, invalid channel evidence, source/session/transport changes or backwards timestamps interrupt collection. Restoring the link does not resume an interrupted check. Manual stop validates the final sample before freezing the ranges. Clear discards the observation; leaving the page also discards it. Nothing is saved, exported or automatically shared. No neutral/endpoint calibration, stick-to-motor assignment, stream request, parameter write or vehicle command is added.

## Bench procedure when valid telemetry is available

1. Use only the hardware-team-secured Cube/R12/T12 setup with motor/ESC outputs physically isolated. The app cannot stop the rover.
2. Establish a current accepted Cube heartbeat and fresh RC_CHANNELS in the app. An open Bluetooth link alone is insufficient. If Start observation is disabled, read the explanation and diagnose the receive path first.
3. Open Analyze telemetry → RC & PWM and tap Start observation.
4. Move one remote stick at a time and note which numbered channel's range changes. Do not assume the value must be 1500 at centre.
5. Tap Stop observation and review the historical ranges. Use Clear observation before another check.

The October 19 screenshots have bytes but no validated supported messages; this phase cannot make those bytes into valid telemetry or certify the wiring. It adds a check to use once the receive issue is resolved. Motor direction, braking, link-loss response, real speed enforcement and control acceptance remain unverified.

## Validation

The final code passed `testDebugUnitTest`: **380 tests in 62 suites, zero failures/errors/skips**, including 10 new activity-reducer tests. Both `assembleDebug` and `assembleDebugAndroidTest` passed. `lintDebug` passed with **0 errors and 32 existing warnings**. The full check was repeated after the final UI retention guard was added.

Five new Android fixture tests compile: missing-evidence gating, range accumulation/stop/clear, source-change interruption/page disposal, real screen-timer expiry without incoming packets, and narrow large-font scrolling in both themes. The planned targeted run also includes the two existing RC/PWM tests, four workspace tests and two visual-regression tests (13 total).

**Initial UI attempts were blocked.** On October 19, the first cold start of the isolated API 34 MamaRegression emulator (2 GB RAM, four cores) produced an Android System UI ANR. The acceptance script correctly refused to begin instrumentation. A clean restart with 1.5 GB RAM/two cores also produced startup ANRs in System UI and Google services, plus a System UI subscription-ID exception. No application assertions ran in those attempts. The emulator was stopped after diagnosis; no error dialogs were dismissed to bypass acceptance. Windows had roughly 1.0–1.3 GB free RAM during these failures, with OneDrive using about 1.8 GB. The operator was asked to quit unused apps before another attempt; memory pressure is a contributing possibility, not a proven sole cause.

**Earlier retest after the operator closed unused apps: 12 of 13 UI tests passed.** Windows had about 4 GB free RAM before startup. MamaRegression booted with 2 GB RAM/four cores without the earlier error dialogs, both APKs installed, and the acceptance script completed the targeted run. All five new RC activity tests passed, along with the empty RC/PWM monitor test, four workspace tests and both visual-regression tests. The existing `RcPwmScreenTest.portSelectionDoesNotMixOutputs` failed at line 31: after scrolling to and tapping Port 1, the test could not find the expected `1701 µs` value. An isolated rerun reproduced the same failure. No application code or test assertions were changed for that retest. The later repair and results are recorded below.

Local evidence (ignored build artifacts):

- Full targeted run: `app/build/acceptance-20261019-163945-b72fa51d/instrumentation.txt` — 13 run, 1 failure.
- Isolated reproduction: `app/build/acceptance-20261019-164319-1c101fd7/instrumentation.txt` — 1 run, 1 failure.
- `rc-activity-light.png` and `rc-activity-dark.png` in the full-run directory were visually reviewed: the captured large-font status, safety instructions and Stop observation button are readable without overlap in both themes. Automated checks also scrolled to channel 18 and exercised Stop/Clear.

After the environment is healthy, install both built APKs on emulator-5556 and run:

```powershell
./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5556 -TestClass 'com.mamadrones.gcs.RcInputActivityUiTest,com.mamadrones.gcs.RcPwmScreenTest,com.mamadrones.gcs.RoverWorkspaceUiTest,com.mamadrones.gcs.VisualLayoutRegressionTest'
```

## PWM test repair and final targeted retest

The failure was in test scrolling, not the port-selection state logic. The chip's nearest scroll parent is the horizontal selector row. Calling `performScrollTo()` only on the chip did not bring that row into the outer vertical page viewport after the new RC activity panel pushed it below the fold. The captured `pwm-before-page-scroll.png` confirms that the selector was offscreen. The fixed helper first scrolls the row vertically into view, then scrolls the desired chip horizontally and performs the actual tap. It asserts visibility, enabled state and selected state, then checks the displayed PWM value and absence of other-port values. No arbitrary delay, direct invocation of the selection callback, or relaxed value assertion was introduced.

The original test now covers Port 0 → 1 → 0. A new 200 dp-wide fixture covers four ports and Port 0 → 3 → 0, exercising both scroll axes and rejecting mixed readings. `pwm-port-1-selected.png` visually confirms the selected chip after the corrected scrolling. Only the UI tests changed for this repair; production PWM display logic, telemetry and hardware controls were unchanged.

Verification on October 19:

- Test APK build and lint passed; lint remains **0 errors, 32 existing warnings**. The unchanged **380-unit-test** suite was validated as up-to-date by Gradle, with zero recorded failures/errors/skips.
- A diagnostic attempt on the old emulator failed before reaching the port check because Android System UI froze and the fixture had no Compose hierarchy (`app/build/acceptance-20261019-165057-ad76f08e/instrumentation.txt`). This was an environment failure, not an additional PWM assertion result.
- A separate clean API 34 `MamaPwmRegression` emulator was created under ignored build artifacts, preserving the existing AVD. It used 2 GB RAM, four cores and Bluetooth disabled. Startup reached the launcher without error dialogs before tests began.
- **All 14 targeted UI tests passed**, including all three RC/PWM tests, five RC activity tests, four workspace tests and two visual regressions. JUnit reported `OK (14 tests)` in 287.074 seconds. Evidence: `app/build/acceptance-20261019-170653-8e41cb09/instrumentation.txt`. PWM screenshots and the RC light/dark screenshots are saved alongside that log.
- **Environment caveat:** the post-run window check found `Application Not Responding: system` after the successful JUnit result. The test assertions passed, but this was not an error-free emulator session or stability soak. No dialog was dismissed to force a pass. The emulator was stopped after evidence collection; broader emulator stability remains a separate issue.

The PWM assertion failure is resolved. No physical hardware acceptance is claimed; the latest undecodable serial stream still needs its separate bench investigation.
