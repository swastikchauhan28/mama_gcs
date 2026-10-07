# Implemented-feature test report

## Scope

Tested the current working tree, including the uncommitted Phase 14 and 15 changes, on 2026-10-07. This is a software regression run, not rover acceptance or a safety certification. No real vehicle commands were sent. No production behavior was changed for these tests.

Environment: Windows host, JVM 17, Pixel 3a x86_64 Android 14 / API 34 emulator. No physical phone was attached through ADB. Only the debug variant and this emulator configuration were exercised.

## Results

The initial full run passed all 90 JVM tests and all 21 Android instrumentation tests. The expanded full run then completed successfully in 6m 45s:

- 100 JVM tests passed; 0 failures, errors or skips.
- 23 Android instrumentation tests passed; 0 failures, errors or skips. This includes every instrumentation class, not only Foundation UI.
- Debug assembly passed.
- Lint passed with 0 errors and 28 warnings.
- `git diff --check` passed (line-ending notices only).

Automated checks passed, but visual review found a landscape layout defect below. This is not an all-features acceptance sign-off.

Both test tasks are explicitly rerun, with no instrumentation-class filter:

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest --rerun --max-workers=2 --console=plain
```

Build and unchanged lint inputs may be reused by Gradle; the test tasks execute again. Reports are generated under:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

## Coverage by implemented area

| Area | Automated evidence | Remaining acceptance checks |
| --- | --- | --- |
| Startup and fullscreen | Real activity launch/recreation, hidden system bars, accessible connection shortcut, landscape map/navigation | More Android versions, cutouts, keyboard combinations, large fonts, TalkBack, tablets |
| Navigation and themes | Primary/secondary screens reachable, theme selection and DataStore persistence across recreation | Daylight/glove usability, all screen sizes; screenshots are not pixel-diff assertions |
| UDP | Real loopback datagrams, send/receive counts, closed-state errors, manager cleanup, saved endpoint persistence | Actual phone-to-WSL/Cube network, background close and network changes on phone |
| UDP-to-telemetry integration | Added real loopback socket → parser → router → repository test: fragmented heartbeat, coordinates/speed/heading, foreign-port filtering, retained closed diagnostics | Real firmware stream/SITL replay and long-duration traffic |
| BLE | UI fixtures for characteristic subscription, cancel, counts, disabled resubscription, link exclusivity policy | Actual scan/permissions, GATT/CCCD, notify/indicate, cancellation races, timeouts, radio disconnect/reconnect, fragmentation under load |
| MAVLink | Supported message parsing, CRC rejection, signed rejection policy, trailing zeros, source selection/filtering, heartbeat timeout, telemetry unit conversion and invalid-value handling | Other dialects/firmware, independent recorded captures, fuzzing, long-duration load; MAVLink 1 and signature verification are not implemented |
| Diagnostics | Chunk/message distinction, source filters, each rejection category, noise, session reset/history, BLE/UDP history separation | Actual radio counters compared with captured traffic |
| Telemetry panels and map state | UNKNOWN/stale handling, coordinate precision, waypoint hit testing, map container in portrait/landscape | Track bounds/reset, online tile success/failure/retry, gestures, heading/track rendering, center/follow/pan behavior with changing telemetry |
| Health evidence | Added missing/reported/problem cases; readiness stays NOT ASSESSED | Vehicle-specific thresholds and hardware health evaluation are not implemented |
| Drive and emergency stop | UI stays disabled even with connected synthetic telemetry; added all 12 independent admission blockers; role/session policy tests | No physical stop, actuator command, deadman or authenticated command path exists to test |
| Local mission editor | Invalid coordinate rejection, add/edit/reorder/remove/save/relaunch, portrait/landscape editor, route review and local outline checks | Map long-press/confirmation/tap editing and fit-to-route gestures on device |
| Local route geometry | Size bounds, stable identities, distance/date line, duplicate legs, self-crossing/degenerate outline, concave-outline segment checks | Not terrain, obstacle, physical trajectory or enforced-geofence validation |
| Recovery and route files | Committed/recovery storage separation, GeoJSON coordinates/outline, GPX ordered route/name escaping, malformed-file rejection | Android document-provider import/export UI, process-kill recovery, storage-full/provider cancellation |
| Route library | Added empty/full codec roundtrip, corruption/duplicate-ID bounds, real persistence, trimmed/case-insensitive name rules, separate copies and selected deletion | UI confirmation flows and full-library error presentation |
| VESC groundwork | Provisioned controller IDs, finite/in-order data, freshness and RPM/eRPM distinction in pure reducer | Live VESC decoding/link is not implemented |
| Spray, hydraulics, admin | Unavailable-state UI and deny-by-default authorization groundwork | Actual hardware adapters, authentication/user management and auditing are not implemented |

The table distinguishes direct automated checks from UI fixtures and untested device interactions. Passing the suite is not proof that every interaction or failure mode works. No percentage/line-coverage claim is made.

### Screenshot observations

The connected-telemetry fixture and light-settings screenshots were inspected. Telemetry cards, theme styling, bottom navigation and the unavailable emergency-stop warning rendered. The connected fixture's map area was blank at capture time, so that screenshot does not establish successful basemap/marker rendering. Foundation screenshots use a Compose test host (with system bars); fullscreen policy is verified separately by `ImmersiveWindowTest` using the real activity.

The real-activity landscape screenshot was also inspected: fullscreen and side navigation rendered, but the bottom ROUTE PLAN / MESSAGES / SAFETY GATE / SETUP toolbar partially overlapped the Center map control and covered part of the MapLibre attribution. The toolbar is positioned by `DashboardScreen.kt` near line 180 independently of the map controls in `VehicleMap.kt` near line 348. This layout issue remains unfixed in this testing-only change. Add a non-overlap regression check when correcting it.

Screenshots retained locally in ignored `app/build/qa-full-suite/`: `fullscreen-landscape.png`, `operate-connected-fixture-393.png`, and `settings-light-393.png` (plus other Foundation captures). Map areas were blank at these capture times; this does not determine whether the cause is loading timing, network/provider failure or rendering. A focused basemap acceptance check is still needed.

## Test changes in this run

- Added `DriveSafetyGateTest`, `HealthAssessmentEvaluatorTest`, `MissionLibraryCodecTest`, and `UdpMavlinkIntegrationTest` (10 JVM tests total).
- Added `LocalRepositoryTest` (2 Android storage tests). These restore previous endpoint settings and remove only entries created by the test. The library test skips rather than deletes existing user routes if there is insufficient room.
- Corrected `MissionPlanningTest` cleanup to restore a pre-existing unsaved recovery copy as well as the committed draft.
- Vehicle control code and safety restrictions were not changed.

## Manual acceptance checklist for the remaining implemented features

Use an emulator or a test phone with backed-up local routes first. Do not connect movement-capable machinery for a UI test. For actual hardware acceptance, the hardware team must independently secure the rover and provide its physical stop system; the app's stop button does not stop machinery.

1. **Screens/settings:** Visit Operate, Map, Drive, Plan and every Systems screen. Rotate portrait/landscape; select Dark, Light and System. Relaunch and verify saved theme. Increase font size and open coordinate/connection keyboards; verify fields, navigation and warnings remain reachable.
2. **Online map:** With an authorized MapTiler key/network, confirm attribution and tiles, zoom, and map errors/retry when network is unavailable. Do not assume cached tiles provide supported offline regions.
3. **Mission editor:** Add two coordinate waypoints; reject latitude 91. Long-press the map and cancel, then confirm a point. Tap its marker, edit, reorder and delete. Fit the route. Save/relaunch. Leave an unsaved edit, terminate/relaunch and check recovery. Back up important drafts first.
4. **Outline review:** Draw an enclosing outline, then move a waypoint outside it. Check the warning. Test a self-crossing outline and a route segment crossing outside a concave outline. These warnings are local geometry only.
5. **Files/library:** Export a test route to GeoJSON and GPX through Android's picker, import with preview, cancel once, then confirm. Verify coordinate order and precision. GeoJSON should retain the outline; GPX should not. Save two differently named library copies, test a duplicate name, cancel then confirm open/delete, and verify the other copy remains.
6. **UDP/SITL:** Follow [the phone/SITL guide](sitl-phone-telemetry.md). Confirm local/remote addresses and ports, open explicitly, verify byte/decoded/accepted counters and heartbeat connection. Compare GPS, speed, heading, battery, attitude and Rover HUD with simulator outputs. Check center/follow and pan-to-release-follow while simulated position changes. Use the simulator only; never move a real rover for this test.
7. **UDP failures/lifecycle:** Stop simulator output and verify heartbeat degradation and hidden stale operating values. Close the session and verify retained LAST SESSION/CLOSED counters. Start a new session and confirm reset. Background the app and verify foreground-owned link closes without automatic reconnection. Check invalid endpoint and occupied-port errors.
8. **BLE on phone:** Use the hardware-confirmed BLE module and its correct notification characteristic. Test permission denial/retry, Bluetooth off, scan, GATT inspection, cancel connection, subscribe, raw-byte counts and decoded heartbeat/telemetry. Disconnect remotely, check stale handling, then manually reconnect. Confirm another UDP/BLE session cannot take ownership while one is active. Follow [Phase 14's phone checklist](phase-14-report.md). A VESC BLE stream is not automatically MAVLink.
9. **Safety display:** With both synthetic connected and disconnected states, all movement, arm/mode, mission-execution and emergency-stop controls must remain unavailable. UNKNOWN equipment values must not become fabricated zeros or safe-state claims.
10. **Soak/device matrix:** Record app/firmware versions and run extended read-only reception, rapid navigation, repeated connect/disconnect, screen rotation, suspend/resume, network/radio loss and recovery on the intended phone/tablet. Capture Logcat errors and diagnostics before declaring hardware acceptance.

## Known limits

The lint warnings include dependency update notices, one API-level manifest-attribute notice and three Compose modifier-order warnings. They are not 28 functional test failures; they still warrant a separate maintenance pass. No dependency upgrade is included in testing.

Release signing/build distribution, physical BLE/Cube/VESC tests, real-phone network acceptance, background stress, accessibility, online-map provider acceptance and full document-picker interactions remain outside this run. Commands, mission execution, live VESC/spray/hydraulic integrations and production authentication are unfinished features, not passed tests.
