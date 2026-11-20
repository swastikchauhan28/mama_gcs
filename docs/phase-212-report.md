# Phase 212 — Plain decimal outline edit coordinates

## Goal and baseline

The user approved a software-only local planning UI phase on 2026-11-20. Baseline `e375be1`, clean `main`; model/version unavailable. RC hardware work remained paused. The Phase 211 screenshot showed a stored outline longitude as `8.993216059187306E-5` in the edit dialog, although the dialog asks for decimal degrees and uses a decimal keyboard. See D-212-01.

## Change and data path

`MissionScreen.openFenceEditor` reads a selected `DraftWaypoint` from the current `MissionDraft.keepInFence` list (at most 64 vertices) and places its latitude/longitude in volatile saveable dialog text. Scientific-form `Double.toString()` values are now expanded with `BigDecimal.valueOf(value).toPlainString()` only when the plain result fits the existing 32-character input bound. Ordinary decimal strings and signed zero retain their canonical form. Exceptionally small values requiring more than 32 plain characters retain scientific notation rather than being truncated or rounded. `WaypointDialog` still parses a finite in-range `Double`, and the existing expected-vertex snapshot guard and `EditFenceVertex` action remain unchanged. A no-edit Apply must therefore send the same Double coordinates. The local working draft, recovery and explicit save flow remain as before; no schema, account, permission, dependency, network, external destination or vehicle command changed. This formatting is not a survey or positioning accuracy claim.

The 2×-text synthetic Compose test uses a negative small latitude, a small positive longitude and negative zero. It checks the displayed decimal text and exact `EditFenceVertex` actions on no-edit Apply. It captures a screenshot in the disposable test app and does not write an operator draft.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 9m 42s`. JVM XML reports: 533 tests in 84 suites, zero failures/errors/skips. Both debug APKs built; lint XML: zero errors, 27 warnings.
- The existing disposable `MamaGcs_Phase210_API34` AVD was identified by its process command line, not an operator phone. A fresh `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30` passed before installation. The app and test APKs were installed with `adb install -r`; no data was cleared.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#editingSmallOutlineCoordinatesKeepsExactValuesAtLargeText'` — `OK (1 test)` and Android healthy, zero new crash/ANR events. Evidence: `app/build/acceptance-20261120-172845-a2201b66/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.WaypointKeyboardUiTest#addWaypointCanDismissImeAndConfirmAtLargeText'` — `OK (1 test)` and Android healthy, zero new crash/ANR events. Route coordinate entry remains separately checked on the same build. Evidence: `app/build/acceptance-20261120-173011-27a69d13/`.
- Visual inspection of ignored `app/build/phase212-plain-outline-edit.png` showed both plain decimal coordinates, field labels, Cancel and Apply legible at 2× font. `git diff --check` passed before the final documentation update; Git emitted only working-copy LF/CRLF conversion warnings.

## Limits and recovery

The extreme-value scientific-notation fallback is a documented length-limit behavior, not directly exercised by the focused emulator test. The test proves unchanged Apply dispatches the same Double values for its synthetic samples; it does not prove physical field accuracy, spoken screen-reader behavior, saved-draft recovery or rover safety. Owner diff review and the broader release gate remain open. Source rollback would review and reverse only this Phase 212 diff from `e375be1`, rebuild and rerun the checks; no rollback or production-qualified artifact was verified. Commit/push only on request.
