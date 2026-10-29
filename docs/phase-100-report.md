# Phase 100 — elapsed-time RC activity recorder

2026-10-29. User approved implementation and offline emulator testing. Baseline `0647c05253774c3e0e504861aef223aedbb70a2e`, clean; model/version unavailable. See D-100-01.

Goal: use elapsed current-session, selected heartbeat and accepted RC evidence for local activity start, range observation, interruption and stop; preserve bounded historical ranges and explicit restart. Five seconds remains an unverified app diagnostic; no physical safety or calibration claim. PWM timing is outside scope.

## Behavior and boundaries

`RcPwmScreen` supplies Android elapsed realtime to `RcInputActivityPanel`; `RcInputActivity` reads the active session start, selected system/component, selected heartbeat and RC sample and their accepted-observation timestamps. It requires nonfuture current-session evidence within five seconds. Missing evidence fails closed. The page-local snapshot stores an elapsed session key, last-check and last-sample timestamps and up to 18 minimum/maximum ranges; it contains no raw message history and is discarded on leaving the page. Disconnect, stale evidence or session/source changes interrupt the recorder and retain historical ranges until cleared or explicitly restarted. Two samples sharing the same elapsed millisecond remain indistinguishable by the existing timestamp-based deduplication.

The radio and selected-autopilot state are untrusted inputs. The change adds no connection, command, dependency or external destination. It does not verify identity, radio wiring, calibration, hardware movement or safe operation. Five seconds is a diagnostic timeout, not a failsafe. Android deep-sleep behavior and physical hardware remain unverified. PWM still uses calendar time.

## Verification and review

- `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: fresh 454 JVM tests passed, debug app and test APK built; lint passed. The lint report task and some other tasks were up-to-date (28 executed, 69 up-to-date).
- Targeted `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RcInputActivityUiTest,com.mamadrones.gcs.RcPwmScreenTest#portSelectionDoesNotMixOutputs,com.mamadrones.gcs.RcPwmScreenTest#realRcDisplayTimerHidesValuesWithoutNewMessages'`: 8/8 passed on Pixel_3a_API_34_extension_level_7_x86_64; Android health gate passed. Evidence: `app/build/acceptance-20261029-111828-76386d2f/` (ignored build output); light/dark narrow large-font screenshots were inspected.
- The first attempted install used an older checkout APK and the test APK path was missing. No tests ran against it. Both current APKs were then installed from the temporary build directory before the 8/8 run. An initial count of 389 was from stale checkout results; 454 is the fresh temporary-build count.
- Review guide: inspect elapsed session/freshness checks, interruption and bounded ranges in `RcInputActivity`, wiring of elapsed time through the screen, and added test fixtures. No new dependency or hardware command. `git diff --check` passed. The changed diff was reviewed for secrets; no dedicated gitleaks or trufflehog scanner was available, so this is not a repository-wide secret scan. Commit/push require separate authorization.
- Recovery: baseline revision `0647c05253774c3e0e504861aef223aedbb70a2e` is recorded, not certified as a tested release. Review and revert this phase's changes explicitly if needed; no schema migration or persistent data change was introduced.
