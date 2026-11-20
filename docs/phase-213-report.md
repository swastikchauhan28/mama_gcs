# Phase 213 — Preserve existing route waypoint coordinates on edit

## Goal and baseline

The user approved this software-only local planning change on 2026-11-20. Baseline `836f7c5` on clean `main`; model/version unavailable. RC hardware work remained paused. `MissionScreen.openEditor` previously used seven-decimal formatting for both existing waypoint edits and map-based Add. A no-edit Apply on a more precise existing waypoint could therefore dispatch rounded coordinates. See D-213-01.

## Change and data path

For an existing route waypoint (`id != null`), `openEditor` now uses the bounded, round-trip coordinate display helper introduced for outline edits in Phase 212. The helper shows a plain decimal when a scientific-form coordinate fits the 32-character input bound; it keeps the original scientific form for exceptional values whose plain expansion would exceed that bound. A blank typed Add is still blank, and map-based Add (`id == null`) still uses the seven-decimal prefill. The shared `WaypointDialog` retains finite/range validation and dispatches the existing local `MissionPlanAction.Edit`. A precise waypoint no longer changes merely because its edit dialog is opened and confirmed unchanged.

The route draft remains a bounded list of at most 250 `DraftWaypoint` values, read by the mission screen and written through the existing view-model/recovery/explicit-save path. No schema, account, permission, dependency, new external destination, telemetry or vehicle-command behavior changed. The existing configured basemap provider is contacted by the map workflow test; that is not a new app destination. Decimal preservation is not evidence of field survey or physical path accuracy.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 5m 8s`. JVM XML: 533 tests in 84 suites, zero failures/errors/skips. Both debug APKs built; lint XML: zero errors, 27 warnings.
- The existing disposable `MamaGcs_Phase210_API34` emulator was identified from its process command line. `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30` passed before installing both APKs with `adb install -r`. No operator phone, hardware link or data wipe was involved.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.WaypointKeyboardUiTest#editingPreciseWaypointWithoutChangesPreservesCoordinatesAtLargeText'` — `OK (1 test)` and Android healthy, zero new crash/ANR events. A small negative latitude and longitude with more than seven decimal places remained exact in the emitted local Edit action. Evidence: `app/build/acceptance-20261120-175022-a4477f37/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.WaypointKeyboardUiTest#addWaypointCanDismissImeAndConfirmAtLargeText'` — `OK (1 test)` and Android healthy, zero new crash/ANR events. Evidence: `app/build/acceptance-20261120-175121-8a1c5635/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.MissionMapDialogTest#mapSelectionAndLongPressRequireConfirmationAndExplicitSave'` — `OK (1 test)` and Android healthy, zero new crash/ANR events. This existing test used the configured online map, checked map Add prefill and the real local draft/recovery/save path on a disposable emulator; it restores its previous saved draft in `finally`. Evidence: `app/build/acceptance-20261120-175153-8b55ba21/`.
- Visual review of ignored `app/build/phase213-route-no-change-edit.png` at 2× text showed the full small negative latitude and precise longitude, labels, Cancel and Apply legible. `git diff --check` passed before final documentation, with only working-copy LF/CRLF conversion warnings.

## Limits and recovery

The no-edit precision check is synthetic action dispatch; the separate map test covers real local save behavior but not a no-edit edit of a high-precision saved route. Extreme-value fallback remains as in Phase 212 and was not directly exercised in this phase. These results do not establish physical route accuracy, rover safety or spoken screen-reader behavior. Owner diff review and the broader release gate remain open. Source rollback would review and reverse only the Phase 213 diff from `836f7c5`, rebuild and rerun the checks; no rollback or production-qualified artifact was verified. Commit/push only on request.
