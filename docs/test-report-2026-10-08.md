# Mama GCS expanded feature acceptance

Testing on 2026-10-08 found two reproducible application defects and an unresolved mission-editor orientation-test timeout. This build is **not accepted as fully tested**. Software test success does not certify rover operation. No vehicle commands were sent, and production code was not changed.

## Environment and device checks

- Baseline: `5dc0164`, plus the test changes described below.
- Emulator: Pixel 3a, Android 14 / API 34. Storage-writing instrumentation runs target this emulator explicitly, not the user's phone.
- Physical phone: A059, Android 16, connected through wireless ADB. The current debug APK was installed in place with `install -r`; no app data was cleared and the app was not uninstalled.
- The phone launched Mama GCS, displayed a geographic MapTiler basemap with labels and attribution, and showed UNKNOWN/disconnected telemetry. Systems and Motors screens were opened; VESC telemetry remained explicitly unavailable.
- The first user-initiated BLE scan showed **No BLE advertisements found**. A read-only system check then found Bluetooth disabled (`BLE_ON` scan-only state), with SCAN and CONNECT permissions granted. The empty result does not establish that the radio was absent.
- After the user enabled Bluetooth, the adapter reported ON and a subsequent scan displayed multiple unnamed BLE devices with signal strengths and no advertised service UUID. Discovery and both empty/populated result screens are verified on this phone. No device was identified as the rover or connected. GATT discovery, CCCD subscription and real Cube Orange MAVLink reception remain blocked on identifying the correct radio.

## Automated results

- The complete JVM suite passed: **102 tests, zero failures, errors or skips**. This includes two new tests for track filtering, the 2,000-point bound and reset on a new session.
- The focused mission workflow run passed 7 of 8 tests. The failed recovery regression is described below.
- Both focused native-map acceptance tests passed after correcting the test input timing and restarting an emulator whose System UI had stopped responding. They use synthetic positions and do not open a vehicle link.
- The landscape overlap regression failed, matching the previously observed screenshot defect.
- The expanded complete Android run executed **34 tests: 31 passed, 3 failed, zero skipped** in 11m 19s. Both map tests passed in this full run as well. Failures were the two regressions below and `MissionPlanningTest.editReorderAndSaveDraftSurvivesNewViewModel`.
- The earlier baseline passed 23 Android instrumentation tests; see [the previous report](test-report-2026-10-07.md). That earlier success does not override the current failures.

Native map gestures use real device-time events. Earlier attempts failed while a System UI ANR dialog intercepted input, and one tap helper emitted enough synchronous events to be interpreted as a long press. These test-environment/harness failures are not counted as additional app defects. The final focused map run passed both tests.

## Confirmed defects

### Discarded edit returns from recovery

1. Start with a saved draft named `Untitled route`.
2. Rename it to `Temporary edit` and wait for automatic recovery persistence.
3. Rename it back to `Untitled route`; the UI state is no longer dirty.
4. Recreate the mission ViewModel with the same repository.
5. Actual result: `Temporary edit` is restored. Expected: the saved draft remains current.

`MissionPlanViewModel.dispatch` cancels the pending recovery job when the working draft equals the saved draft, but does not clear an already-written recovery copy. The regression uses the real ViewModel with an in-memory repository to isolate the state transition. It tests reopening the ViewModel, not an operating-system process kill. Other branches with the same recovery handling also warrant review.

Evidence: `MissionWorkflowTest.undoToSavedDraftMustNotResurrectOldRecoveryOnReopen`.

### Landscape toolbar covers Center

The operator toolbar overlaps the map Center control. The measured bounds were toolbar `(350,739)-(1288,871)` and Center `(1162,673)-(1355,805)` on the test emulator. Previous visual review also found attribution obscured. The new assertion covers Center; it does not independently assert attribution bounds.

Evidence: `LandscapeOverlapTest.operatorToolbarMustNotCoverCenterControl` and the earlier landscape screenshot.

## Unresolved test failure

`MissionPlanningTest.editReorderAndSaveDraftSurvivesNewViewModel` timed out at line 57 while waiting for the instrumentation target-context configuration to report landscape after requesting activity rotation. The preceding add/edit/reorder/outline/save assertions completed; the later landscape and relaunch steps did not. This test passed in the earlier baseline but failed in the expanded full run. Whether this is an app orientation issue, test-context issue or emulator timing issue remains undetermined. It is not recorded as a pass or silently classified as an environment problem.

## Feature acceptance matrix

PASS means only the stated checks passed. PARTIAL means additional interactions or environments remain unverified. BLOCKED requires external evidence or hardware. NOT IMPLEMENTED must not be mistaken for a working feature.

| Feature | Status | Evidence and remaining work |
| --- | --- | --- |
| Launch and fullscreen | PARTIAL | Emulator launch/recreation and orientation checks; physical phone launch. More cutouts, devices, large fonts and accessibility remain. |
| Main and secondary navigation | PARTIAL | Automated navigation fixtures and real-activity checks; phone Operate, Systems, Motors and BLE screens. Not every phone screen interaction was repeated. |
| Dark, Light and System themes | PASS for existing regression | Theme selection and persistence tests; not a complete contrast/accessibility audit. |
| Geographic basemap | PASS for online loading | Physical phone screenshot plus successful emulator style load. Provider/network failure and retry acceptance remain. |
| Map zoom, pan, Center and Follow | PASS on emulator | Camera assertions with changing synthetic positions; drag disables follow; missing position disables Center/Follow. |
| Route fit, marker selection and long press | PASS at map component level | Real native gestures and callbacks. Full dialog-confirm/cancel chain from a map gesture remains to be exercised. |
| Landscape operator layout | FAIL | Toolbar overlaps Center. Attribution was also obscured in prior visual review. |
| Position track | PASS at repository level | Stationary jitter/invalid coordinates ignored; latest 2,000 points retained; new session clears history. Rendered marker, heading and track still need visual acceptance against a known trace. |
| Manual mission editor | PARTIAL | ViewModel add/edit/reorder/remove/name/outline/save/new operations and invalid edits pass. Full-run UI test passed through save, then timed out at landscape rotation; later relaunch steps were not reached. |
| Recovery | FAIL | Discarded edit can return after reopening. Automatic recovery and corrupt-copy handling have isolated tests; process-kill acceptance remains. |
| Import preview and confirmation | PASS at ViewModel level | GeoJSON preview/cancel/confirm, invalid content and provider-error handling. Android document-picker end-to-end import remains unverified. |
| GeoJSON and GPX export | PARTIAL | Codec round trips and ViewModel export cleanup/errors pass. Android CreateDocument/provider end-to-end output remains unverified. |
| Local route library | PARTIAL | Actual persistence and ViewModel open/delete/copy separation tests. Full UI confirmation/full-library acceptance remains. |
| Local route and outline review | PASS for existing geometry tests | Bounds, duplicate legs, crossings and concave outline cases. Not physical obstacle, terrain or enforced-geofence validation. |
| UDP transport and parser integration | PARTIAL | Real loopback datagrams, fixed peer filtering, counters, cleanup and endpoint persistence pass. Current phone-to-SITL reception and network/background transitions remain unverified in this run. |
| MAVLink decoding and source policy | PASS for supported automated cases | Eight supported message types, CRC, truncation, identity/filtering, heartbeat timeout and diagnostics. Independent captures, fuzzing and long-duration reception remain. |
| BLE discovery | PARTIAL | Phone empty-result screen followed by discovery of multiple unnamed devices after enabling Bluetooth; permissions granted. Identifying the rover, permission denial/retry and Bluetooth-off error handling remain. |
| BLE GATT and MAVLink reception | BLOCKED | Advertisements are present but none identified as the rover. Existing fixtures do not establish real hardware reception. |
| Telemetry, health and diagnostics | PARTIAL | Mapping/freshness/UNKNOWN/counters/health-evidence tests pass. Live values must still be compared against Cube Orange data. |
| Drive and safety gating | PASS for disabled-state tests | All admission blockers and connected/disconnected fixture checks keep commands unavailable. Not a verified physical stop system. |
| Vehicle commands and emergency stop | NOT IMPLEMENTED | No movement, arm/mode, deadman or physical-stop command path. |
| Mission upload and execution | NOT IMPLEMENTED | Local route planning only; no vehicle upload/start/pause/download. |
| VESC, spray and hydraulics | NOT IMPLEMENTED live integration | VESC reducer groundwork and unavailable-state screens exist; no validated live hardware adapters. |
| Authentication and pairing | NOT IMPLEMENTED production integration | Deny-by-default policy groundwork is not trusted user authentication or vehicle pairing. |
| Offline regions and background reception | NOT IMPLEMENTED | Cached map tiles are not supported offline-region management; links are foreground-owned. |
| Soak, release and device matrix | NOT VERIFIED | Release distribution/signing, long runs, memory/performance, broad Android versions, accessibility and hardware disconnect stress remain. |

## Test changes and reproduction

Added `MissionWorkflowTest` (8), `MapAcceptanceTest` (2) and `LandscapeOverlapTest` (1). Added two cases to `VehicleRepositoryImplTest`. Known failing regression assertions are intentionally retained; production fixes are outside this testing request.

Use the emulator only for the instrumentation task:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest --max-workers=2 --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --console=plain
```

The online map tests require the existing authorized MapTiler configuration and network access. Keys are not included in this report. Test reports are under `app/build/reports/`; the complete Android run is also retained under `app/build/qa-full-20261008/`. The phone basemap screenshot is retained locally at `app/build/qa-manual/phone-operate.png`. These generated artifacts are not committed.

## Remaining acceptance work

1. Correct and rerun the two confirmed regressions; diagnose the mission-editor landscape timeout before accepting the affected features.
2. Exercise Android document-picker import/export/cancellation with disposable routes on the emulator, plus full map-dialog and library UI workflows.
3. Test map failure/retry, phone UDP/SITL reception, background closure, disconnect/reconnect and stale-data handling end to end.
4. Have the hardware team identify the Cube Orange BLE radio among the discovered devices. Record its identity, service/notification UUIDs, telemetry-port wiring/baud rate and firmware version. Then test read-only GATT reception in a secured setup.
5. Complete the device/accessibility/soak checklist in the previous report. Do not treat disabled or unfinished controls as tested vehicle functionality.
