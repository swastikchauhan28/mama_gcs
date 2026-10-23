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
- The later agent-operated manual route walkthrough below exercised edit/save/reopen/export/import on the disposable emulator. Blank-draft creation and app-process restart were checked in a subsequent follow-up below; independent human operator acceptance remains separate.
- No production release, signing/recovery, customer defense-brief teach-back or owner risk acceptance was established. A passing emulator suite does not change the monitoring-only/disabled-command boundary.
- Local `app/build/` logs and screenshots are ignored and may be removed by a clean. This report preserves results and paths, not raw captures or credentials. Do not present those files as durable release evidence without an approved sanitized archive.

Next: perform the hardware-team-secured, read-only Cube-origin heartbeat trace at Cube TELEM2, R12 UART2 and phone-side T12 Bluetooth; then repeat the bounded Mama GCS Classic receive check only if the route produces valid Cube frames. Keep physical controls unavailable.

## Manual local-route walkthrough — 23 October 2026

Starting revision `7535d0d`, clean `main`. The agent used the existing Pixel 3a API 34 emulator in `-read-only -no-snapshot-load -no-snapshot-save` mode; ADB listed only `emulator-5554`, and `sys.boot_completed=1`. No physical device was connected. The first sandboxed ADB call could not start its daemon; the host-context call succeeded. Preflight `adb -s emulator-5554 shell dumpsys window` showed the launcher, and the final check showed Mama GCS as the current activity; neither showed an `Application Not Responding:` or `Application Error:` dialog.

- Opened the installed app disconnected: mode/arming and instruments were unknown or unavailable, and the physical-safety banner was visible.
- Used an existing saved, two-point synthetic draft rather than clearing it or creating a blank one. Reordered its points, saved, navigated to Operate and reopened Plan. The editor showed no unsaved changes, with waypoint 1 `-35.3640000, 149.1660000` and waypoint 2 `-35.3630000, 149.1650000` (143 m straight-line estimate).
- Exported `Original_draft.geojson` through Android DocumentsUI into the emulator's local Downloads. Read-only `adb -s emulator-5554 shell ls -l /sdcard/Download/Original_draft.geojson` found a 585-byte file; read-only `cat` confirmed GeoJSON point sequence 1/2 and those coordinates. The file was not committed or uploaded.
- Imported that same file through DocumentsUI. The preview showed `Original draft`, two waypoints and 143 m. After explicit user approval, selected **Replace working draft** and saved the imported route. The editor returned to no unsaved changes; both waypoint coordinates/order matched the export.
- Inspected **Review**: two points, no zero-length legs, outline not configured, and `ROUTE SHAPE DEFINED · REVIEW REQUIRED`. Inspected the route and review UI in both Dark and Light themes without apparent clipping; the local-plan label remained visible and mission Upload/Download/Start/Pause/Resume stayed unavailable.

This is a manual agent-operated UI path, not an independent human acceptance or release approval. It did not create a route from a blank draft, restart the Android app process, test GPX, verify geospatial safety, contact the Cube/R12/T12, or exercise vehicle commands. The route file and theme change are confined to the read-only emulator session. Screenshots were visually inspected in-session but not archived as durable evidence. No production code, dependencies, Cube/radio settings or wiring were changed.

## Blank-draft and process-restart follow-up — 23 October 2026

Later evidence supersedes only the blank-draft and process-restart gaps noted above. With explicit operator approval to replace the disposable emulator's local test draft, the agent used the same read-only Pixel 3a API 34 emulator (`emulator-5554`), not a physical phone. Mama GCS displayed a new `Untitled route` with zero points. The agent added two synthetic WGS84 coordinates, `(1.0000000, 1.0000000)` and `(1.1000000, 1.1000000)`, using **Add coordinates**. The editor showed two points and a 15,724 m straight-line estimate. These arbitrary coordinates are UI fixtures, not a route for physical use.

The agent tapped **Save draft** and observed **No unsaved changes**. The targeted command `adb -s emulator-5554 shell pidof com.mamadrones.gcs` returned `4406`; `adb -s emulator-5554 shell am force-stop com.mamadrones.gcs` exited successfully. After relaunching Mama GCS from the emulator home screen, `pidof` returned `5208`. Opening **Plan** showed `Untitled route`, two points, 15,724 m, waypoint 1 `(1.0000000, 1.0000000)`, **No unsaved changes**, and the `LOCAL PLAN · Not uploaded to rover` label. The previously entered second coordinate and two-point count support persistence; this run did not scroll to re-inspect waypoint 2 after restart. `adb -s emulator-5554 shell dumpsys window` showed `com.mamadrones.gcs/.MainActivity` focused, with no matching `Application Not Responding` or `Application Error` line.

This is a fresh, agent-operated local persistence check, not independent operator acceptance, exhaustive visual coverage, GPX acceptance, physical telemetry validation or release approval. No app data was cleared, no phone or hardware was touched, and no production code or dependencies changed. The earlier manual-walkthrough limitations remain historical facts of that earlier run.
