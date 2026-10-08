# Mama GCS expanded feature acceptance

The final complete emulator suite passed **40 of 40 Android tests**, with **102 JVM tests passing** and **zero lint errors**. The three original failing cases now pass after the repairs below. This build is still **not accepted as fully tested**: hardware and several end-to-end checks remain. Software test success does not certify rover operation. No vehicle commands were sent.

## Repair results

The follow-up changes are based on `6d7ad4d` and were validated on the Android 14 emulator. The phone was not updated or used for this repair run.

- **Recovery:** returning to the saved draft now clears the obsolete recovery copy without changing the committed route or library. Undo, New, import and library-open transitions share the same persistence handling. Cleanup is serialized with writes; failures display a warning. A follow-up UI fix keeps Save draft enabled after cleanup failure even when the draft is unchanged, so the suggested retry is usable.
- **Landscape layout:** the operator toolbar occupies its own measured row below the map. Tests check separation from the entire map viewport, Center and Follow, and verify Route Plan navigation. A new landscape screenshot also shows controls and attribution unobscured; its blank map background is not basemap-loading evidence.
- **Rotation test:** the assertion now reads the displayed activity's configuration instead of the instrumentation application context, and independently verifies that rendered width exceeds height. The complete edit/save/rotate/reopen workflow passes. This supports a test-context issue rather than requiring a production orientation change.

Validation:

- 19 of 19 targeted Android tests passed, including all three original failures and four additional recovery cases.
- 102 JVM tests passed; lint completed with zero errors and 28 existing warnings.
- The subsequent complete Android run passed 37 of 38 tests. All three original failures passed. An intermittent waypoint-tap timeout appeared in `MapAcceptanceTest`.
- That map test was then changed to wait for native camera-idle events before projecting the tap position, and to enqueue touch-down without blocking on a busy UI thread. Both map tests passed in the focused rerun (2 of 2) and in both subsequent complete 40-test runs.
- The next phase added two tests. Its first complete run passed 39 of 40; only the new document-picker test failed when DocumentsUI rejected an accessibility click during a drawer transition. The test now reacquires the control and waits for an accepted click without weakening its file-content or persistence assertions.
- **Final complete run: 40 of 40 Android tests passed, zero failures, errors or skips**, in a successful 14m 20s Gradle run. This includes the new recovery retry-button regression and document-picker acceptance. One intervening attempt ran zero tests because APK installation hit an emulator connection timeout; retrying after verifying the connection succeeded.
- The JVM suite was explicitly rerun: **102 passed, zero failures, errors or skips**. Lint completed successfully with **zero errors and 28 existing warnings**. The final change after that lint run affects only picker-test click synchronization.

The original full-run results below are retained as history, not the current status of the repaired cases. Earlier runs are retained in `app/build/qa-fixes-20261008/full-before-map-sync/` and `app/build/qa-fixes-20261008/full-before-picker-sync/`. The inspected landscape screenshot is `app/build/qa-fixes-20261008/fullscreen-landscape.png`. The latest complete Android results are under `app/build/outputs/androidTest-results/connected/debug/`.

## Document picker acceptance

The new `RouteDocumentPickerTest` passed on the Android 14 emulator using the real Android DocumentsUI and local Downloads provider. It verifies:

- GeoJSON and GPX exports produce readable files with the expected name and waypoint coordinates. GeoJSON retains the three-vertex outline; GPX omits it as documented.
- Cancelling export or the import picker leaves the saved draft unchanged and permits another attempt.
- Cancelling an import preview leaves the saved draft unchanged. Confirming an import changes the working draft only; explicit Save draft commits it.

The test preserves prior local draft/recovery data and deletes only its two uniquely named temporary files. It requires English DocumentsUI on a disposable emulator. Cloud providers, denied URI access, picker rotation and operating-system process death remain unverified. Initial harness runs exposed missing Compose-frame synchronization and a non-clickable Downloads heading match; both were corrected before the passing run. No production document-exchange code change was needed.

## Environment and device checks

- Baseline: `5dc0164`, plus the test changes described below.
- Emulator: Pixel 3a, Android 14 / API 34. Storage-writing instrumentation runs target this emulator explicitly, not the user's phone.
- Physical phone: A059, Android 16, connected through wireless ADB. The current debug APK was installed in place with `install -r`; no app data was cleared and the app was not uninstalled.
- The phone launched Mama GCS, displayed a geographic MapTiler basemap with labels and attribution, and showed UNKNOWN/disconnected telemetry. Systems and Motors screens were opened; VESC telemetry remained explicitly unavailable.
- The first user-initiated BLE scan showed **No BLE advertisements found**. A read-only system check then found Bluetooth disabled (`BLE_ON` scan-only state), with SCAN and CONNECT permissions granted. The empty result does not establish that the radio was absent.
- After the user enabled Bluetooth, the adapter reported ON and a subsequent scan displayed multiple unnamed BLE devices with signal strengths and no advertised service UUID. Discovery and both empty/populated result screens are verified on this phone. No device was identified as the rover or connected. GATT discovery, CCCD subscription and real Cube Orange MAVLink reception remain blocked on identifying the correct radio.

## Initial automated results

- The complete JVM suite passed: **102 tests, zero failures, errors or skips**. This includes two new tests for track filtering, the 2,000-point bound and reset on a new session.
- The focused mission workflow run passed 7 of 8 tests. The failed recovery regression is described below.
- Both focused native-map acceptance tests passed after correcting the test input timing and restarting an emulator whose System UI had stopped responding. They use synthetic positions and do not open a vehicle link.
- The landscape overlap regression failed, matching the previously observed screenshot defect.
- The expanded complete Android run executed **34 tests: 31 passed, 3 failed, zero skipped** in 11m 19s. Both map tests passed in this full run as well. Failures were the two regressions below and `MissionPlanningTest.editReorderAndSaveDraftSurvivesNewViewModel`.
- The earlier baseline passed 23 Android instrumentation tests; see [the previous report](test-report-2026-10-07.md). That earlier success does not override the current failures.

Native map gestures use real device-time events. Earlier attempts failed while a System UI ANR dialog intercepted input, and one tap helper emitted enough synchronous events to be interpreted as a long press. These test-environment/harness failures are not counted as additional app defects. The final focused map run passed both tests.

## Original defects

### Discarded edit returns from recovery

1. Start with a saved draft named `Untitled route`.
2. Rename it to `Temporary edit` and wait for automatic recovery persistence.
3. Rename it back to `Untitled route`; the UI state is no longer dirty.
4. Recreate the mission ViewModel with the same repository.
5. Actual result: `Temporary edit` is restored. Expected: the saved draft remains current.

`MissionPlanViewModel.dispatch` cancels the pending recovery job when the working draft equals the saved draft, but does not clear an already-written recovery copy. The regression uses the real ViewModel with an in-memory repository to isolate the state transition. It tests reopening the ViewModel, not an operating-system process kill. Other branches with the same recovery handling also warrant review.

Evidence: `MissionWorkflowTest.undoToSavedDraftMustNotResurrectOldRecoveryOnReopen`.

Resolved by the explicit `clearRecovery` operation and shared recovery synchronization described above. Regression coverage includes cleanup failure, an older in-flight write and a new edit immediately after undo.

### Landscape toolbar covers Center

The operator toolbar overlaps the map Center control. The measured bounds were toolbar `(350,739)-(1288,871)` and Center `(1162,673)-(1355,805)` on the test emulator. Previous visual review also found attribution obscured. The new assertion covers Center; it does not independently assert attribution bounds.

Evidence: `LandscapeOverlapTest.operatorToolbarMustNotCoverCenterControl` and the earlier landscape screenshot.

Resolved by measuring the toolbar outside the map viewport. The stronger regression now checks the entire toolbar rather than only the bounds of its text.

## Original orientation timeout

`MissionPlanningTest.editReorderAndSaveDraftSurvivesNewViewModel` originally timed out while waiting for the instrumentation target-context configuration to report landscape after requesting activity rotation. The preceding add/edit/reorder/outline/save assertions completed; the later landscape and relaunch steps did not. It now passes in both the targeted and full repair runs using the activity configuration and rendered-size assertions. No timeout was increased and no production orientation behavior was changed.

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
| Landscape operator layout | PASS on tested emulator | Toolbar is outside the map viewport; Center, Follow and attribution are unobscured. Other screen sizes remain unverified. |
| Position track | PASS at repository level | Stationary jitter/invalid coordinates ignored; latest 2,000 points retained; new session clears history. Rendered marker, heading and track still need visual acceptance against a known trace. |
| Manual mission editor | PASS for tested actions | ViewModel actions, invalid edits and UI add/edit/reorder/outline/save/landscape/relaunch checks pass. Local document-provider exchange now passes; full map-dialog workflows remain separate acceptance work. |
| Recovery | PARTIAL | Obsolete-copy cleanup and normal recovery pass, including failure/race cases and real storage separation. Operating-system process-kill acceptance remains. |
| Import preview and confirmation | PASS for local Downloads provider | Real Android picker cancellation, GeoJSON preview cancellation, both formats' confirmation and explicit save pass. Invalid content/provider-error handling also has ViewModel coverage. Other providers and process death remain. |
| GeoJSON and GPX export | PASS for local Downloads provider | Real CreateDocument cancellation and both exported files' contents pass, including GeoJSON outline retention and GPX omission. Other providers and interrupted writes remain. |
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

The initial run added `MissionWorkflowTest` (8), `MapAcceptanceTest` (2), `LandscapeOverlapTest` (1), and two cases to `VehicleRepositoryImplTest`. The repair follow-up adds four more mission workflow tests, extends the real-storage cleanup check, strengthens the layout assertion, corrects the orientation assertion context and synchronizes native map gestures. The next validation phase adds a Foundation UI retry-button regression and the real document-picker workflow. Production changes are limited to local recovery persistence, its retry UI and dashboard layout.

Use the emulator only for the instrumentation task:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug :app:connectedDebugAndroidTest --rerun --max-workers=2 --console=plain --continue
```

The online map tests require the existing authorized MapTiler configuration and network access. Keys are not included in this report. Test reports are under `app/build/reports/`; the complete Android run is also retained under `app/build/qa-full-20261008/`. The phone basemap screenshot is retained locally at `app/build/qa-manual/phone-operate.png`. These generated artifacts are not committed.

## Remaining acceptance work

1. Repeat acceptance across additional screen sizes and Android versions. The final complete Android 14 emulator suite now passes all 40 tests; one passing run is not a long-duration reliability measurement.
2. Extend the passing local document-picker workflow to other providers, picker rotation and interrupted operations. Exercise full map-dialog and library UI workflows.
3. Test map failure/retry, phone UDP/SITL reception, background closure, disconnect/reconnect and stale-data handling end to end.
4. Have the hardware team identify the Cube Orange BLE radio among the discovered devices. Record its identity, service/notification UUIDs, telemetry-port wiring/baud rate and firmware version. Then test read-only GATT reception in a secured setup.
5. Complete the device/accessibility/soak checklist in the previous report. Do not treat disabled or unfinished controls as tested vehicle functionality.
