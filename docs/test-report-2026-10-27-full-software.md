# Full non-hardware software test run — 2026-10-27

## Scope and baseline

- Goal: run every existing non-hardware JVM and Android acceptance test, build and lint the app, inspect representative UI output, and identify gaps. This was a test-only session; no production code or test assertions were changed.
- Baseline: `9bb331e73e321f7d53c9cd3c017d704f33d7389b` on `main`. Phases 71–75 were already uncommitted; unrelated `desktop.ini` was present and left alone.
- Device: disposable `Pixel_3a_API_34_extension_level_7_x86_64` emulator, API 34, `emulator-5554`. No operator phone, Cube, receiver, remote, or actuator was used. The acceptance runner could not confirm a GPU launch option, although the running emulator command line contained `-gpu swiftshader_indirect`.
- Android test inventory: 39 classes, 154 test methods. `BleRemoteStabilityAcceptanceTest` has four physical-remote tests and was excluded under the requested non-hardware scope. The other 38 classes and 150 methods were run in two disjoint batches.

## Fresh results

| Gate | Result | Evidence |
| --- | --- | --- |
| `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` with `--rerun-tasks --no-daemon --max-workers=1` | Build successful in 7m 50s; 97/97 tasks executed | `$env:TEMP\mama-gcs-phase58-app-build` |
| JVM unit tests | 389 tests in 64 suites; 0 failures, errors, or skips | `$env:TEMP\mama-gcs-phase58-app-build\test-results\testDebugUnitTest` |
| Android lint | 0 errors, 28 warnings | `$env:TEMP\mama-gcs-phase58-app-build\reports\lint-results-debug.xml` |
| Online map and BLE-profile report batch | 7/7 passed; Android health gate passed, 0 new crash lines, 0 new ANR events | `app/build/acceptance-20261027-120301-89588244/instrumentation.txt`, `android-health.txt` |
| Remaining non-hardware Android batch | 143/143 passed; Android health gate passed, 0 new crash lines, 0 new ANR events | `app/build/acceptance-20261027-120410-9085806f/instrumentation.txt`, `android-health.txt` |

The Gradle command used the existing local build init script:

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

The map batch command was:

```powershell
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -TestClass 'com.mamadrones.gcs.BleProfileReportActionsTest,com.mamadrones.gcs.MapAcceptanceTest'
```

The second batch used the same runner and a comma-separated `-TestClass` list generated from every remaining `*Test.kt` class, excluding those two classes and `BleRemoteStabilityAcceptanceTest`. The runner's saved instrumentation output records `OK (143 tests)`.

## Feature and endpoint coverage

- Local route planning, coordinate entry and validation, route library, import/export through Android DocumentsUI, map display and recovery, theme/layout, settings persistence, parameter review, telemetry/RC/PWM screens, speed-monitor UI and report actions were exercised by Android tests. Connection screens used controlled fixtures rather than radio hardware. The document-picker test operated on a disposable emulator, restored its prior draft, and removed only its uniquely named test files.
- The map acceptance batch exercised the configured online MapTiler style and rendering path, including gestures and draft/home overlays. A remote map service can change independently of this test run.
- JVM tests covered local account and authorization rules, route codecs and persistence logic, MAVLink parsing/session rules, Bluetooth transport simulation, reports, and a local UDP loopback integration. There is no general app HTTP backend to enumerate; the external software service used in this run was the configured map provider.
- Full-display captures were inspected for the satellite route map, the local route library, and the signed-decimal coordinate dialog with the Android keyboard. They rendered without a visible blocking overlap in these fixtures. Captures remain under `app/build/full-20261027-*.png` and are generated evidence, not release artifacts.

## Remaining limits

- Passing every existing software test does not establish every possible feature state. In particular, a full account setup/login/logout journey through the real Android UI is not covered end to end; account behavior is covered mainly by JVM tests and selected UI checks. A real app UDP endpoint through a separate external peer is also not covered by this run; UDP transport has local loopback coverage.
- Bluetooth Classic/BLE live connections, Cube MAVLink telemetry, RC input, receiver forwarding, and physical safety behavior remain untested here by request. The four physical BLE stability tests were skipped for that reason.
- Other Android versions, languages, keyboard implementations, screen readers, long-running field use, hostile-network behavior and a production release gate remain outside this run.
- The 28 lint warnings should be reviewed by category before release. A zero-error lint result does not mean zero warnings.
- The raw test and screenshot evidence is in ignored `app/build` and a local temporary Gradle directory; those paths may be removed by cleaning or the operating system. Preserve copies separately if needed for a release review.

## Status and next safe step

No failures remain in the existing non-hardware test inventory. Add targeted real-activity account and external UDP integration acceptance cases if those gaps must be closed before release; do not infer live hardware readiness from this run. No commit, push, deployment, hardware configuration, or vehicle command was performed.
