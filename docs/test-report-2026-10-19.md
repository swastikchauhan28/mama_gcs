# Mama GCS software regression — 19 October 2026

## Initial result (before visual fixes)

The current working tree, including Phases 40–42, passed **370 JVM unit tests** and **118 Android software instrumentation tests**. The final successful batches contain no failures or skipped tests. Debug app and instrumentation APK builds succeeded. Android lint completed with **0 errors and 32 warnings**. This is software regression evidence, not acceptance of the physical rover or every possible device/environment combination.

No production or test source was changed during the initial regression run. Existing uncommitted implementation changes were preserved. No vehicle commands were sent. No commit or push was performed. A subsequent user-requested visual repair is documented at the end of this report.

## Checks and evidence

| Check | Result | Local evidence |
| --- | --- | --- |
| JVM unit suite, explicitly rerun | 370 passed; 61 suites; 0 failures/errors/skips | `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/index.html` |
| Debug application and test APK | Both built successfully | `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Android lint | 0 errors, 32 warnings | `app/build/reports/lint-results-debug.html` and `.txt` |
| BLE profile report and native online maps | 7 passed; 128.004 seconds | `app/build/acceptance-20261019-150854-2b2c86ae/instrumentation.txt` |
| Remaining software instrumentation classes | 111 passed; 1,099.553 seconds | `app/build/acceptance-20261019-151128-bb8d7ea7/instrumentation.txt` |
| Working-tree whitespace check | `git diff --check` passed | Git emitted line-ending conversion warnings, not whitespace errors |

The 7-test and 111-test batches are disjoint and together cover all 118 software tests in the current instrumentation inventory. Four opt-in physical tests in `BleRemoteStabilityAcceptanceTest` were deliberately excluded, not counted as passes. No physical phone was visible to ADB during final acceptance.

Lint warnings remain, principally dependency/toolchain update notices and existing Compose/API/style warnings. Dependencies were not upgraded as part of testing.

## Coverage

- MAVLink framing, CRC and payload validation, source selection, session reset, stale telemetry, RC/PWM, reported home and version information, message inspection, raw byte-signature counters and diagnostic reports.
- Classic, BLE and UDP software lifecycle/state handling; receive selection, explicit confirmations, cancellation, report previews/exports and BLE stability history/recovery using controlled fixtures.
- Native map rendering for Streets, Satellite and Hybrid; map gestures, draft overlays, home marker, style recovery, camera/follow behavior and saved preferences.
- Local mission editing, explicit save, recovery, route library, coordinate dialogs, timing, GeoJSON/GPX exchange and real Android document-picker import/export/cancellation.
- Parameter-file parsing/review/comparison, hardware summaries, report controls and missing-data handling.
- Navigation, light/dark themes, portrait/landscape layouts, fullscreen behavior, speed observations and unavailable-control safety presentation.
- JVM tests also cover local accounts, authorization, deny-by-default drive policy and the standalone VESC measurement reducer. This does not constitute a security audit or a live VESC integration test.

The final screenshot pull contains 37 images under `app/build/acceptance-20261019-151128-bb8d7ea7/screenshots/files/`. An earlier partial pull is also retained at the parent `screenshots/` level. Screenshots are generated from fixtures, not a live vehicle. Layout-only screenshots with blank map backgrounds are not basemap-loading evidence; the dedicated native-map tests provide that evidence separately.

## Initial visual findings

Screenshot inspection found two usability issues despite the automated passes. These were recorded, not changed, in this testing task:

1. **Portrait instrument readability:** `screenshots/files/qa/station-operate-portrait.png` shows the heading label/value partly clipped beside the fixed compass at the horizontal instrument-strip edge. The strip is horizontally scrollable in `DashboardScreen.kt`, but the initial viewport does not expose the whole heading field. Existing accessibility/navigation checks do not assert full text visibility at this boundary.
2. **Light-theme settings contrast:** `screenshots/files/qa/station-settings-landscape-light.png` shows dark General settings header/subtitle text on a dark background, while the cards/sidebar are light. Existing theme/persistence tests do not measure visual text contrast.

The reviewed Hybrid map screenshot displays the basemap, route and home markers. Mission layout, Classic connection-check and BLE report-preview screenshots were also inspected. Automated success is not a claim of flawless visual acceptance.

## Earlier attempts and emulator repair

Earlier attempts are retained as failed/aborted evidence; their passing cases were not used to inflate the final totals.

- The existing AVD experienced System UI ANRs and a system Bluetooth-service crash. The runner refuses to start through Android crash/ANR dialogs; dialogs were not dismissed to force acceptance.
- An attempted host-GPU configuration with Vulkan disabled caused the native map engine to crash with “No Vulkan compatible GPU found.” That attempt also had a report-window focus timeout.
- A subsequent attempt with Vulkan enabled still encountered map-window focus failures while a System UI ANR owned input focus. Another reduced-display retry also encountered an ANR. These observations explain concrete environment blockers but do not prove the cause of every earlier timeout.
- A new isolated Android 14/API 34 x86_64 AVD was created under ignored `app/build/qa-avds/`, using a 720×1480 display, density 294, 2 GB RAM and software graphics with Vulkan available.
- The isolated image's virtual Bluetooth service aborted while starting `HciHalHidl`. Disabling Bluetooth and always-on BLE scanning in that emulator and rebooting resolved the startup blocker. The final runs therefore validate Bluetooth UI/state logic with fixtures, **not a real Bluetooth radio**.
- All 118 software tests then passed on that isolated emulator. The earlier report-focus and native-map failures passed in the final run without source changes. A final window check showed the launcher rather than an ANR/crash dialog.
- The temporary density override on the original AVD was reset; `wm density` confirmed physical density 440 with no override. Both task-launched emulators were stopped after evidence collection/restoration.

Earlier logs include `app/build/acceptance-20261019-143856-ff29b553/`, `app/build/acceptance-20261019-144623-388f7fbc/` and `app/build/acceptance-20261019-145647-f571ff74/`. Emulator logs and temporary AVDs remain in ignored build directories for diagnosis.

## Not verified / still required

1. Real phone → identified T12 remote → R12 → Cube telemetry, including validated heartbeat, measurements and changing RC channels. Software tests do not resolve the previously observed physical stream with bytes but no valid messages.
2. Physical BLE/Classic connection stability, remote power-loss detection, real reconnect/background behavior and the opt-in process-death/recovery sequence. The existing physical-test harness uses explicit `T12_485` opt-ins; the currently reported `T12_131` must be positively identified before any future physical run.
3. Actual UART routing/baud-rate agreement, SBUS input, motor-side mapping, ESC response, speed calibration, radio-loss failsafe and independent emergency stop.
4. Live VESC telemetry and spray/hydraulic integration. Their hardware adapters are not implemented merely because the UI and reducer tests pass.
5. Driving, arming, mission upload/execution and app emergency-stop commands remain unavailable. The software must not be treated as ready to operate machinery.
6. Broader Android-version/device coverage, field/network-failure endurance and production security validation remain separate acceptance work.

## Reproduction

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
.\gradlew.bat :app:testDebugUnitTest --rerun --no-daemon --max-workers=1 --console=plain
```

After installing both APKs on a healthy disposable emulator, run `scripts/Test-AndroidAcceptance.ps1` with its verified emulator serial and a comma-separated class list. First batch: `BleProfileReportActionsTest` and `MapAcceptanceTest`. Second batch: all remaining `*Test.kt` classes, excluding those two and `BleRemoteStabilityAcceptanceTest`. Use fully qualified `com.mamadrones.gcs.*` class names. Do not run another UI automation client on the emulator during instrumentation. The native online-map acceptance tests require the configured map provider and network access.

## Visual repair follow-up

At the user's request, the two screenshot findings were addressed after the initial regression:

- `DashboardScreen.kt`: below 600dp available width, the speed/More row and heading/compass row are separate. The heading is no longer inside the horizontally clipped instrument strip. Wider screens retain the existing strip. Heading, battery, GPS fix and direction are also available in the scrollable More dialog, so the compact layout does not remove access to those readings.
- `StationShell.kt`: a root Material `Surface` explicitly paints the current theme's background and supplies its matching content color. Previously, transparent page content exposed the Android window background while its text followed the Compose theme.
- `VisualLayoutRegressionTest.kt`: two tests check heading geometry at 320dp and 392dp, font scales 1.0 and 1.3, missing/numeric headings, and the More action; they also sample actual rendered header background/ink through Light → Dark → Light transitions and require at least 4.5:1 contrast.

The first heading test used `TextLayoutResult.hasVisualOverflow`, which flagged the fully visible HEADING label even with ample parent width. The diagnostic screenshot `app/build/heading-overflow.png` shows the complete text. The final assertion checks actual line extents, no ellipsis, and visible measured bounds with one physical pixel of rounding tolerance. It does not permit hidden text. Earlier test-only failures remain in `app/build/acceptance-20261019-154406-814fd907/` and `app/build/acceptance-20261019-154756-68449589/`.

Both final visual regression tests passed in 35.913 seconds: `app/build/acceptance-20261019-155309-a810ed07/instrumentation.txt`. The production build and all 370 JVM tests passed after the production changes. After the final assertion adjustment, the instrumentation APK and lint were rebuilt successfully; lint remains at 0 errors and 32 warnings. Hardware behavior is unchanged and the physical acceptance gaps above still apply.

The related acceptance batch also passed **33/33 tests**, with no failures or skips, in 542.34 seconds: `app/build/acceptance-20261019-155421-f3fd0d86/instrumentation.txt`. It covers `FoundationUiTest`, `RoverWorkspaceUiTest`, `ImmersiveWindowTest`, `SettingsPersistenceTest`, `BasemapSettingsUiTest`, `LandscapeOverlapTest`, `RoverSpeedMonitorUiTest` and `MapAcceptanceTest`. Combined with the two new tests, **35 targeted UI tests passed on the fixed build**. The earlier complete 118-test run predates these visual changes and is not represented as a complete rerun of the now-120-test software inventory.

Fresh screenshots were visually inspected and confirm resolution of both original findings:

- Portrait heading: `app/build/acceptance-20261019-155421-f3fd0d86/portrait-fixed.png`.
- Light-theme settings header: `app/build/acceptance-20261019-155421-f3fd0d86/settings-light-fixed.png`.

The heading text and compass have separate, unobscured space; settings header/subtitle text is readable on the painted light background. All checks remained hardware-independent. No commit or push was performed in this repair task.
