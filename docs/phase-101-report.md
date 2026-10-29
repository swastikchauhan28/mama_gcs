# Phase 101 — elapsed PWM display freshness

2026-10-29. User approved implementation and offline emulator testing. Baseline `cd41b87a6f609855c4ea3ad4c057f7f8278a06e5`, clean; model/version unavailable. See D-101-01.

Goal: display each selected PWM port only with current-session elapsed heartbeat, accepted message and per-port sample evidence. The five-second window is an unverified diagnostic, not a failsafe. No hardware actions.

Initial build attempt: `:app:compileDebugUnitTestKotlin` failed because the new test used an unimported `assertNotNull`. Replaced it with the already imported `assertTrue`; no production behavior changed. Rerun pending.

## Behavior and boundaries

`MavlinkSession` timestamps received chunks using elapsed realtime, selects the autopilot before routing, and the router passes the existing elapsed timestamp to `VehicleRepositoryImpl`. The repository retains at most four port samples, each with 16 bounded output fields and calendar plus volatile elapsed receipt times; a new receive session clears them. `RcPwmScreen` supplies its 500 ms elapsed tick, and `RcPwmPanels.pwm` hides values unless the selected port, session, connection, heartbeat and accepted heartbeat/SERVO_OUTPUT_RAW observations are current. The accepted PWM observation is message-wide; individual freshness is enforced by the port's own selected-source receipt timestamp. Calendar timestamps remain in the existing telemetry evidence report. No persistence or external destinations are added.

The input stream and source IDs are untrusted; checks do not authenticate a sender or establish wiring, calibration, physical movement or a safe stop. Five seconds is an unverified display timeout. Missing timing hides values; emulator tests do not validate hardware rates or Android deep sleep. No firmware settings, actuator commands or transport filters were changed.

## Verification and review

- The initial `:app:compileDebugUnitTestKotlin` failed on an unimported test assertion; the test-only fix was made using the existing import.
- Rerun: `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`. Fresh JVM results: 456 tests, 0 failures/errors/skips; both debug APKs built and lint passed with 0 errors and 28 warnings (22 tasks executed, 75 up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RcPwmScreenTest,com.mamadrones.gcs.RcInputActivityUiTest'`: 12/12 passed with Android health gate on Pixel_3a_API_34_extension_level_7_x86_64. Targeted synthetic fixtures only, not a full-app or hardware test. Evidence: `app/build/acceptance-20261029-113420-c464e047/`; stale-port screenshot visually inspected.
- Review guide: trace elapsed timestamp from `MavlinkMessageRouter` through `VehicleRepositoryImpl` into each `PwmOutputState`; inspect per-port freshness in `RcPwmPanels` and the screen tick and tests. No new dependencies or permissions. The changed diff was reviewed for secrets; no dedicated gitleaks or trufflehog scanner was available, so this is not a repository-wide secret scan. `git diff --check` passed.
- Recovery: baseline `cd41b87a6f609855c4ea3ad4c057f7f8278a06e5` is recorded, not a certified release; review a scoped inverse and test it before any separately authorized rollback. No database migration or persistence change. Commit/push require separate authorization.
