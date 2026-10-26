# Phase 68 — Search saved routes locally

Date: 2026-10-26
Baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`
Scope: local route-library dialog, one focused instrumentation test and working records. Hardware work remains on hold.

## Goal and behavior

At 320 dp and 2× text, the library dialog shows only about one saved route at a time. Its 25-entry bound makes finding a route by scrolling tedious. The dialog now filters saved route names as the user types, ignoring case and surrounding spaces. It shows a match count, a clear action and a distinct no-match message. Opening the library again clears the query. Existing Open/Delete confirmations still use each entry's stable ID.

## Execution path and data

- The File / Library action opens `MissionScreen`'s local library dialog and resets an in-memory `rememberSaveable` query. The query is limited to the route-name maximum of 80 characters.
- The dialog scans at most 25 `MissionLibraryEntry` items in the current `MissionPlanUiState`, displays matching entries in their original order, and sends the selected entry ID through the existing `MissionPlanAction` callback after confirmation. The filter does not write a draft, change the library repository, or contact telemetry or a vehicle.
- Inputs are local route names and touch/keyboard events. The rendered list is bounded by the existing library limit. There is no new external input, network destination, permission, dependency, schema or persistent field.
- The trade-off is that the search controls consume part of the dialog's vertical viewport; route cards can require scrolling at large text. Clearing or reopening returns the full list. Revisit if a narrower screen, other locale or larger text makes a control unreachable.

## Verification

- Build command: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL**; `79 actionable tasks: 14 executed, 65 up-to-date`. After the test screenshot was moved to include the search field, `:app:assembleDebugAndroidTest` passed again (`60 actionable tasks: 6 executed, 54 up-to-date`). The final source edit after these runs adjusted indentation only.
- Installed the current debug app and test APKs from `%TEMP%\mama-gcs-phase58-app-build\outputs\apk`, the init script's configured output path, onto disposable `emulator-5554`.
- New test: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.MissionLibrarySearchUiTest#searchFiltersRoutesAndKeepsActionsBoundToIdsAtLargeText'` — **PASS, `OK (1 test)`**. It checks mixed-case match, no-match, clear, query reset on reopen, and the exact IDs used by confirmed Open and Delete with synthetic entries and an in-memory callback. Android health recorded zero new crashes and ANRs. Evidence: `app/build/acceptance-20261026-160605-ec7f7bfc/`.
- Existing enlarged-text library regression: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#missionLibraryDialogKeepsLongRoutesAndActionsReachableAtLargeText'` — **PASS, `OK (1 test)`** against the new app APK, with zero new crashes and ANRs. Evidence: `app/build/acceptance-20261026-160703-f1a4f5ca/`.
- Captured and visually inspected `app/build/phase68-route-search.png`: at 320 dp / 2× text, the search field, Clear search, result count and matching route are readable without overlap. The route actions require scrolling and were exercised in the test.
- `$adbPath` was the Android SDK `platform-tools\adb.exe` under `%LOCALAPPDATA%`. `git diff --check` found no whitespace errors; Git reported only line-ending conversion warnings for existing modified files.

## Limits and review

These are two focused emulator tests, not the full Android inventory, lint, persistence, accessibility across all devices/locales or physical hardware acceptance. The image and callbacks use synthetic library entries. The full accumulated Phases 60–68 working diff remains uncommitted; no push was requested. Review the route-library block in `MissionScreen.kt`, the new `MissionLibrarySearchUiTest.kt` and D-068-01. Preserve unrelated `desktop.ini`. Rollback is a scoped revert of the Phase 68 UI/test/records; no data migration or vehicle action applies.
