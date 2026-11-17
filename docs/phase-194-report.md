# Phase 194 — Focused warning history from Operate

Date: 2026-11-17. Baseline: `ad82142`, clean `main` worktree. User approved a software-only focused warning entry and emulator verification; no hardware was connected.

## Goal, path and ownership

Tapping the Operate warning banner now navigates to `diagnostics/warnings`. `MamaGcsApp` maps this internal route to the existing Link & messages workspace, and `DiagnosticsScreen` starts with Warnings only selected, places the bounded message history before report controls, and offers search through a visible button. The ordinary `diagnostics` route retains its existing heading, report-control order, always-visible search and all-message default. The selected-autopilot `VehicleState.statusTexts` list remains the sole in-memory source, capped at 50 by the repository; `ConsolePanels.statusTexts` still performs the display filtering. The route and filter are UI state, with no schema, network, dependency, permission, command or retention change.

Hiding focused search clears its query so a hidden term cannot keep messages filtered. If retained messages clear while the focused route is open, the card displays `NO RETAINED MESSAGES · 0 / 0 shown` instead of the old warning. Neither state confirms that the physical fault has resolved.

## Verification and corrections

- Fresh command: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --init-script "$env:TEMP\mama-gcs-phase185-build.gradle" --offline --no-daemon --max-workers=1 --console=plain` completed `BUILD SUCCESSFUL in 3m 54s` after the final production layout change. JVM XML: 503 tests in 80 suites, zero failures. Lint XML: zero errors, 20 warnings. A later test-only APK rebuild passed after the cleared-history assertion was added.
- The disposable Pixel 3a API 34 emulator passed a 30-second no-wipe health preflight before UI testing. Earlier UI runs on this phase failed the landscape `assertIsDisplayed` check: first the expanded search field, then the duplicate in-page heading, pushed the warning row below the viewport. The focused layout now collapses search and uses the station-shell heading; normal Diagnostics remains unchanged.
- A first cleared-history assertion used only `NO RETAINED MESSAGES` as an exact match; the captured screen showed the correct full status `NO RETAINED MESSAGES · 0 / 0 shown`. The assertion was corrected to match that full status, without weakening the cleared-warning check. The intermediate failed logs remain under ignored `app/build/acceptance-20261117-134658-855962e3/`, `app/build/acceptance-20261117-135437-e57171d3/`, `app/build/acceptance-20261117-140543-b215c1a7/` and the targeted diagnostic run.
- Final command: `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.AutopilotWarningUiTest` returned `OK (8 tests)` in 53.796 seconds. The post-run Android health gate found zero new crash/ANR events and no error dialog. Ignored evidence: `app/build/acceptance-20261117-141251-e9d0a8ce/`.
- Inspected ignored `app/build/reports/phase-194/warning-focused-landscape-final.png`, `warning-focused-portrait-final.png` and `warning-cleared-final.png`: selected filter and first warning row are visible without scrolling in both orientations; after the synthetic list clears, the empty status is visible. Tests also cover normal unfiltered entry, turning the filter off/on, search and clearing a hidden query.

## Limits and recovery

Emulator fixtures prove display/navigation behavior only. A reported warning can be stale or spoofed, and Warnings only hides routine messages until disabled. No live Cube `STATUSTEXT` delivery, physical fault or full app acceptance was checked. This source-only change has no migration or persisted data; a reviewed revert of the Phase 194 diff would restore the previous banner route and Diagnostics order. Do not treat baseline `ad82142` as a qualified release. The next safe step is owner review of the scoped diff; live Cube verification requires separately available, authorized bench hardware.
