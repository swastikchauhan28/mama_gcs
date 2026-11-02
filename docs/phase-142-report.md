# Phase 142 — route import and export verification

Date: 2026-11-02. Starting revision: `a6be3fe` on clean `main`, aligned with `origin/main`. Model: gpt-6.1-terra.

## Scope and results

The user approved verification of GeoJSON and GPX export/import, waypoint order and coordinates, and invalid input handling. The no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator was identified as `emulator-5554`. Existing Phase 140 app and test APKs were installed with `adb install -r`. No fresh build, JVM suite or lint was run in this phase.

The following were invoked with `adb -s emulator-5554 shell am instrument -w -e class <fully-qualified-class-and-method> com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`:

- `RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation`: `OK (1 test)`, 67.927 s. Uses Android DocumentsUI to export both formats and import each, checks order and coordinates, cancellation, preview and explicit save. GeoJSON retains the three outline vertices; GPX does not carry the outline. The test creates uniquely named synthetic files in emulator Downloads, deletes only those paths, and attempts to restore the prior draft/recovery in `finally`; interruption could leave test data behind.
- `MissionWorkflowTest#importPreviewCancelConfirmAndBothExports`: `OK (1 test)`, 0.203 s. Uses the real ViewModel with in-memory storage to verify preview/cancel/confirm, exports, and rejection of invalid content without changing the working draft. This invalid-content case did not use the Android picker.
- Four `MissionExchangeTest` methods for GeoJSON and GPX round trips and invalid documents: `OK (4 tests)`, 0.124 s. They check names, ordered coordinates, supported outline behavior, and rejection of malformed or unsupported input at the codec boundary.

Logs are in ignored `app/build/phase142/run-1.log` through `run-3.log`; they are temporary build artifacts. A bounded `dumpsys window` check found no matching Android error/ANR dialog after the tests. The emulator was stopped without wiping it. No physical phone or vehicle was used.

## Limits and next step

Six targeted tests passed; this is not the full app regression. An invalid-file selection through the system picker was not exercised, and software round trips do not establish mission upload, vehicle operation or field accuracy. Review the results and continue risk-based software regression. Hardware and release gates remain open. Commit/push require a separate request.
