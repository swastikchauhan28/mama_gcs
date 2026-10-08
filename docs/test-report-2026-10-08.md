# Mama GCS expanded feature acceptance

The map-recovery follow-up passed **five focused Android tests**, **102 JVM tests**, and lint with **zero errors and 28 existing warnings**. The expanded full run completed **46 tests: 44 passed and two failed**, in the mission map-dialog and system document-picker workflows. A post-restart rerun passed the library case but failed those same two workflows. Android's **System UI not responding** window was then confirmed as the current input-focus owner. This build is still **not accepted as fully tested**: stable-device reruns, hardware and several end-to-end checks remain. Software test success does not certify rover operation. No vehicle commands were sent.

## Map loading and retry acceptance

Map load failures now discard the cached style reference, so map buttons and telemetry source updates cannot continue using a failed style. Waypoint selection also requires a loaded, non-failed style. The default MapTiler provider, credentials, transport and vehicle-control behavior are unchanged.

Two new `MapLoadRecoveryTest` cases use temporary local style files and synthetic vehicle positions with the real MapLibre loader. They passed on the Android 14 emulator and verify:

- Missing configuration disables zoom, Center, Follow and Fit draft even when position and draft data exist.
- Invalid styles display the generic failure panel without exposing the local URI or raw parser content. Retrying an unchanged invalid source fails again without enabling controls.
- Repairing the same style file and pressing Retry restores controls, vehicle/track/heading layers and position-source updates.
- A native reload failure after success clears the app's previous style state. Retry recovers, and configuration removal followed by another failure/retry cycle also recovers.

These tests require neither a provider key nor internet access and remove their own temporary files. They validate native style parsing and application recovery, not DNS failures, HTTP authorization/quota errors, partial tile failures or offline-region support. Existing online-map tests separately cover successful provider loading and gestures.

The baseline full run completed **44 tests: 43 passed, one failed, zero errors or skips**, in 14m 16s. `MissionMapDialogTest` timed out on its first edit tap; the captured semantics showed the mission editor without a dialog. The test now requires both a camera-idle event and the requested fitted camera position before projecting touch coordinates, and checks window input focus. This closes a test synchronization gap; it does not conclusively establish the original timeout's cause or change production gesture timing. The subsequent focused run passed **all five map tests**, including the real mission dialogs, in a successful 7m 29s Gradle run with JVM tests and lint.

The next full run completed **46 tests: 44 passed, two failed, zero errors or skips**, in 19m 11s. Both new map-recovery cases passed. The mission-dialog test failed its new input-focus precondition before injecting a touch. The document-picker test timed out waiting for its first export dialog, reporting `foreground=null`; a device check also found no focused window while DocumentsUI was the focused activity. The emulator reported high load and heavy memory use. Those observations do not conclusively prove an emulator-only cause.

The mission test now waits for the map window to become visible and regain input focus before every gesture, while retaining its immediate focus assertion and all dialog/storage checks. This handles the gap between Compose becoming idle and Android completing a window transition. The test emulator was restarted without wiping its data; no production picker behavior was changed.

The post-restart three-case run completed in 7m 35s: **one passed, two failed**. Library operations passed. The map-dialog test timed out waiting for focus before its first tap, and the picker test timed out with `foreground=android`. A subsequent window dump identified `Application Not Responding: com.android.systemui` as the focused window; the inspected screenshot shows **System UI isn't responding**. The emulator was awake and configured not to sleep during ordinary test durations. Further UI retries were stopped; the focus wait is not claimed as a complete fix. The screenshot establishes an environment blocker at capture time, not the cause of every earlier failure. A final diagnostic-only test change reports map attachment, visibility and root-focus state on timeout. `assembleDebugAndroidTest` passed after that change in 24s; its runtime behavior remains unverified.

Baseline results are retained locally in `app/build/qa-map-recovery/baseline44/results/`, focused results in `app/build/qa-map-recovery/focused5/results/`, the expanded full run in `app/build/qa-map-recovery/full46-before-focus-wait/results/`, and the failed post-restart run in `app/build/qa-map-recovery/post-reboot3/results/`. The inspected system-dialog screenshot is `app/build/qa-map-recovery/system.png`. These are ignored build artifacts, not committed evidence files.

## Mission dialog and library acceptance

Changes after `bafe2d0` make library errors visible in the editor, explain why Save copy is disabled when the library is full, disable map-point selection while the draft is unavailable or saving, and disable confirmation in already-open coordinate dialogs while saving. Persistence formats and vehicle controls are unchanged.

Four new Android cases cover:

- Real library copy/save, case-insensitive duplicate-name rejection, open/delete cancellation and confirmation, and New draft cancellation and confirmation. Opening a copy or clearing the editor does not replace the committed draft until Save draft. Deleting a library copy does not delete the active draft.
- Native map-point taps and long-presses through the real edit/add dialogs, ViewModel and DataStore. Tests compare prefilled coordinates, preserve waypoint identity on edit, verify cancellation, and require explicit saving before committing changes.
- A full-library fixture with Save copy disabled and an explanation, plus a library-error fixture visible without reopening the library.
- An already-open coordinate dialog that cannot confirm while saving but still permits cancellation.

The first targeted run passed three of four cases. Map runs intermittently timed out waiting for the edit dialog; a later diagnostic failure found a second UI root, consistent with an unexpected dialog. The test now checks the visible waypoint and queues tap-up immediately after tap-down; only deliberate long-presses hold the touch. This mitigates a suspected timing issue without changing app gesture handling or weakening dialog/storage assertions. **The final four-case run passed with zero failures, errors or skips**, in a successful 4m 58s Gradle run. The JVM rerun passed all 102 tests and lint completed with zero errors and 28 existing warnings before the final test-only timing adjustment. Repeat device/load acceptance remains necessary; the initial timeout cause is not conclusively established.

The tests use synthetic routes on the Android 14 emulator and restore draft/recovery data; library cleanup removes only the uniquely named test copy. Full-library and deletion-error presentation use controlled UI fixtures, not a forced physical storage failure.

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

The original full-run results below are retained as history, not the current status of the repaired cases. Earlier runs are retained in `app/build/qa-fixes-20261008/full-before-map-sync/` and `app/build/qa-fixes-20261008/full-before-picker-sync/`. The inspected landscape screenshot is `app/build/qa-fixes-20261008/fullscreen-landscape.png`. Generated Android results under `app/build/outputs/androidTest-results/connected/debug/` are overwritten on each run and may represent a filtered test selection.

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
| Geographic basemap | PASS for online loading and local style recovery | Physical phone screenshot and emulator style load; native local-style failure/retry, missing configuration, disabled controls and restored position updates pass. Real provider/network error scenarios remain. |
| Map zoom, pan, Center and Follow | PASS on emulator | Camera assertions with changing synthetic positions; drag disables follow; missing position disables Center/Follow. |
| Route fit, marker selection and long press | PARTIAL | Native component gestures and a focused real-dialog run pass. The latest full run exposed missing input focus in the real mission-dialog test; the test now waits for focus. Repeat full-suite/device/load acceptance remains. |
| Landscape operator layout | PASS on tested emulator | Toolbar is outside the map viewport; Center, Follow and attribution are unobscured. Other screen sizes remain unverified. |
| Position track | PASS at repository level | Stationary jitter/invalid coordinates ignored; latest 2,000 points retained; new session clears history. Rendered marker, heading and track still need visual acceptance against a known trace. |
| Manual mission editor | PASS for tested actions | ViewModel actions, invalid edits, add/edit/reorder/outline/save/landscape/relaunch, local document exchange and targeted map-dialog workflows pass. Open coordinate dialogs cannot confirm while saving. |
| Recovery | PARTIAL | Obsolete-copy cleanup and normal recovery pass, including failure/race cases and real storage separation. Operating-system process-kill acceptance remains. |
| Import preview and confirmation | PARTIAL | Earlier real picker cancellation, preview cancellation, both formats' confirmation and explicit save passed. The latest workflow retries failed before import at the export dialog; stable-device repetition remains. Invalid content/provider-error handling has ViewModel coverage. Other providers and process death remain. |
| GeoJSON and GPX export | PARTIAL | Earlier real CreateDocument cancellation and both exported files' contents passed, including GeoJSON outline retention and GPX omission. The latest full run timed out opening its first export dialog with no foreground window. Repeat acceptance, other providers and interrupted writes remain. |
| Local route library | PASS for targeted workflows | Real UI/storage copy, duplicate-name rejection, open/delete confirmation and cancellation, plus New draft/explicit-save separation. Capacity and error presentation pass with UI fixtures; storage-failure recovery and process interruption remain. |
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

The initial run added `MissionWorkflowTest` (8), `MapAcceptanceTest` (2), `LandscapeOverlapTest` (1), and two cases to `VehicleRepositoryImplTest`. The repair follow-up adds four more mission workflow tests, extends the real-storage cleanup check, strengthens the layout assertion, corrects the orientation assertion context and synchronizes native map gestures. Subsequent phases add a Foundation UI retry-button regression, the real document-picker workflow, `MissionLibraryUiTest`, `MissionMapDialogTest`, two Foundation UI cases and two `MapLoadRecoveryTest` cases. Production changes cover local recovery persistence, planning UI error/busy states, map failure-state guards and dashboard layout; vehicle commands remain unavailable.

Use the emulator only for the instrumentation task:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug :app:connectedDebugAndroidTest --rerun --max-workers=2 --console=plain --continue
```

To repeat the four mission-dialog/library cases, keep the emulator serial set and run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --rerun '-Pandroid.testInstrumentationRunnerArguments.class=com.mamadrones.gcs.MissionLibraryUiTest,com.mamadrones.gcs.MissionMapDialogTest,com.mamadrones.gcs.FoundationUiTest#libraryFailureAndCapacityAreExplainedWithoutHiddenActions,com.mamadrones.gcs.FoundationUiTest#openWaypointDialogCannotConfirmWhileDraftIsSaving' --max-workers=2 --console=plain
```

To repeat only local map failure/retry acceptance without provider access:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --rerun '-Pandroid.testInstrumentationRunnerArguments.class=com.mamadrones.gcs.MapLoadRecoveryTest' --max-workers=2 --console=plain
```

The online map tests require the existing authorized MapTiler configuration and network access. Keys are not included in this report. Test reports are under `app/build/reports/`; earlier complete Android results are also retained under `app/build/qa-full-20261008/`. The phone basemap screenshot is retained locally at `app/build/qa-manual/phone-operate.png`. These generated artifacts are not committed.

## Remaining acceptance work

1. Resolve the emulator System UI ANR or use a stable disposable test device. Rerun the affected library/map-dialog/picker sequence, then the complete 46-test suite and additional screen sizes/Android versions. The post-restart rerun still failed; the latest full run is not green.
2. Extend the passing local document-picker workflow to other providers, picker rotation and interrupted operations. Extend map/library acceptance to process interruption and actual storage failures.
3. Extend local style-recovery acceptance to real map-provider/network errors. Test phone UDP/SITL reception, background closure, disconnect/reconnect and stale-data handling end to end.
4. Have the hardware team identify the Cube Orange BLE radio among the discovered devices. Record its identity, service/notification UUIDs, telemetry-port wiring/baud rate and firmware version. Then test read-only GATT reception in a secured setup.
5. Complete the device/accessibility/soak checklist in the previous report. Do not treat disabled or unfinished controls as tested vehicle functionality.
