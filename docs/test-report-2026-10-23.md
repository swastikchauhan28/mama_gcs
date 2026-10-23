# Broad software verification — 23 October 2026

## Scope and environment

The operator requested testing of the whole implemented app. This is a broad **software** regression, not a claim that every path, hardware function or release gate is accepted. Starting revision: `61ea57331e2199c47ebe7b42df6c6f7791858ad8` on `main`; pre-existing dirty files were the Phase 49 Android UI test, `docs/HANDOVER.md` and `docs/phase-49-report.md`. They were preserved. No production source, dependency, configuration, Cube/radio setting or physical hardware was changed in this run.

The test device was `emulator-5554`, started from the Pixel 3a API 34 profile with `-read-only -no-snapshot-load -no-snapshot-save`. ADB listed no physical phone when the emulator started. Debug and Android-test APKs were installed in that read-only session. The two documented software batches exclude `BleRemoteStabilityAcceptanceTest` (4 methods requiring physical remote opt-ins). The map batch used the app's already configured MapTiler client key and online map service; no key value or raw telemetry was written to this report.

## Checks and actual results

| Check | Result | Evidence |
| --- | --- | --- |
| `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` | `BUILD SUCCESSFUL`; 97 tasks: 8 executed, 89 up-to-date. The JVM test task was **up-to-date**, not a fresh test in this command. Both APKs built. | Build console; `app/build/outputs/apk/` and `app/build/reports/lint-results-debug.txt` |
| `./gradlew.bat :app:testDebugUnitTest --rerun --no-daemon --max-workers=1 --console=plain` | Fresh task execution passed: 383 tests, 63 XML suites, 0 failures/errors/skips. | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `:app:lintDebug` report | 0 errors, 32 warnings. Warning count matches the prior baseline; warnings were not individually dispositioned in this run. | `app/build/reports/lint-results-debug.txt` |
| `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <installed adb.exe> -TestClass com.mamadrones.gcs.BleProfileReportActionsTest,com.mamadrones.gcs.MapAcceptanceTest` | `OK (7 tests)`; no failure/skip markers. | `app/build/acceptance-20261023-104006-1151087e/instrumentation.txt` |
| Same runner with all other 33 reviewed software-only Android test classes | `OK (121 tests)`; no failure/skip markers. Inventory total: 35 software classes / 128 methods. | `app/build/acceptance-20261023-104240-509b5d01/instrumentation.txt` |
| Android preflight / after each batch | `sys.boot_completed=1`; `dumpsys window` showed no `Application Not Responding:` or `Application Error:` dialog. | Current emulator checks during the run; commands in `docs/TESTING.md` |
| Visual and launch smoke | Inspected original-resolution Streets, Satellite and Hybrid map captures: basemaps, two numbered draft points, route line, home marker and map controls visible. Launched the installed app after tests; the disconnected screen displayed unknown mode/arming, unavailable position/speed/heading, and the physical-safety banner. | `app/build/acceptance-20261023-104006-1151087e/phase31-*.png`; `app/build/acceptance-20261023-104240-509b5d01/whole-app-launch-20261023.png` |

The second Android batch used this exact class-selection command (with the same runner/ADB arguments as the first batch):

```powershell
$classes = @(Get-ChildItem app/src/androidTest/java/com/mamadrones/gcs -Filter '*Test.kt' | Where-Object { $_.BaseName -notin @('BleRemoteStabilityAcceptanceTest','BleProfileReportActionsTest','MapAcceptanceTest') } | Select-Object -ExpandProperty BaseName | Sort-Object)
if ($classes.Count -ne 33) { throw "Unexpected software class count: $($classes.Count)" }
$names = ($classes | ForEach-Object { 'com.mamadrones.gcs.' + $_ }) -join ','
./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb 'C:\Users\swastik\AppData\Local\Android\Sdk\platform-tools\adb.exe' -TestClass $names
```

The combined JVM/Android suites cover software paths for accounts/settings, BLE/Classic screens, telemetry inspection, maps, mission planning/storage/exchange, parameter review, RC/PWM views, reports and visual layout. Route export/import used Android DocumentsUI and a uniquely named local test file on the read-only emulator. These results do not imply every UI branch was manually visited or every feature works with physical devices.

## Gaps and next safe checks

- No Cube/R12/T12 live MAVLink heartbeat or RC acceptance: the physical-path record still has radio-only `RADIO_STATUS` but no confirmed Cube heartbeat at the phone. No physical remote stability/disconnect, movement, arming, stopping, spray, hydraulics, firmware or parameter test was run or authorized by this broad software request.
- No independent human walkthrough of a complete mission draft/reopen/export/import path was performed. Automated tests exercise that route, including the real Android document picker, but a manual operator acceptance path remains separate.
- No production release, signing/recovery, customer defense-brief teach-back or owner risk acceptance was established. A passing emulator suite does not change the monitoring-only/disabled-command boundary.
- Local `app/build/` logs and screenshots are ignored and may be removed by a clean. This report preserves results and paths, not raw captures or credentials. Do not present those files as durable release evidence without an approved sanitized archive.

Next: perform the hardware-team-secured, read-only Cube-origin heartbeat trace at Cube TELEM2, R12 UART2 and phone-side T12 Bluetooth; then repeat the bounded Mama GCS Classic receive check only if the route produces valid Cube frames. Keep physical controls unavailable.
