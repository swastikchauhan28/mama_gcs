# Phase 69 — Keep route search visible while cards scroll

Date: 2026-10-26
Baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`
Scope: local library dialog layout, existing synthetic UI test and working records. Hardware remains on hold.

## Goal and change

Phase 68 put name search inside the same scroller as saved route cards. Scrolling to a later route hid the field and required returning to the top to refine the query. The dialog now keeps its error message, search field, Clear search and result count above a separate scrollable region containing route cards and empty/no-match messages. It retains the 440 dp maximum text height, the existing match logic and original entry order. Open/Delete confirmations still use stable route IDs; this change does not alter any action callback or repository operation.

## Data, boundaries and failure behavior

The dialog reads the current `MissionPlanUiState` library (bounded to 25 entries), the existing 80-character in-memory query and keyboard/touch input. Compose measures the outer column and gives remaining bounded height to the route list through a weighted scroller. The search state remains local to this screen and resets when the library is reopened. No external endpoint, permission, dependency, schema, file write, telemetry path or vehicle command was added.

The fixed controls reduce the route list viewport. At narrow width and large text, users may need to scroll farther to reach an action; the dialog Close action remains outside the list. A long library error, narrower screen, another locale or still larger text could crowd the content and needs separate evidence if supported. If the layout misbehaves, the scoped rollback restores the single scroll column without touching saved routes or hardware.

## Verification

- Build: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL in 3m 22s**, `79 actionable tasks: 14 executed, 65 up-to-date`. Both APKs were installed from the init script's `%TEMP%\mama-gcs-phase58-app-build\outputs\apk` directory onto disposable `emulator-5554`.
- Focused search/pinned-layout test: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.MissionLibrarySearchUiTest#searchFiltersRoutesAndKeepsActionsBoundToIdsAtLargeText'` — **PASS, `OK (1 test)`** with Android healthy and SwiftShader. It filtered synthetic routes, cleared/reopened search, checked exact Open/Delete entry IDs, scrolled to the last card, and asserted the search field remained displayed with its root top unchanged within 1 px. Evidence: `app/build/acceptance-20261026-162556-a61d52ea/`.
- Existing enlarged-text library regression: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#missionLibraryDialogKeepsLongRoutesAndActionsReachableAtLargeText'` — **PASS, `OK (1 test)`**, Android healthy and SwiftShader. Evidence: `app/build/acceptance-20261026-162713-996f9e98/`.
- Captured and visually inspected `app/build/phase69-pinned-library-search.png` after scrolling: the empty search field remains above the last route, whose Open/Delete actions and dialog Close are visible without overlap. The fixture uses in-memory callbacks and no saved user routes.
- `$adbPath` was Android SDK `platform-tools\adb.exe` under `%LOCALAPPDATA%`. No full Android inventory, lint, persistence run, real phone or physical hardware check was performed in this phase.

## Review and status

Review the library `AlertDialog` block in `MissionScreen.kt` and the changed `MissionLibrarySearchUiTest.kt` assertion/capture. The change is limited to Compose layout and its fixture. `git diff --check` found no whitespace errors; Git only emitted existing line-ending conversion warnings. Phases 60–69 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`; the owner has not yet reviewed this diff.
