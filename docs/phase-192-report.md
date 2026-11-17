# Phase 192 — Retained warning-report count on Operate

Date: 2026-11-17. Baseline: `b25d3f1`, clean `main` worktree. Scope: software-only warning display and synthetic tests; no hardware available.

## Goal and design

The Operate banner previously showed only the newest severity 0–4 `STATUSTEXT`. It now also shows how many such **reports** remain in the selected autopilot's current-session, last-50-message history. Repeated messages count separately, while routine messages do not count. `VehicleRepositoryImpl` owns the bounded list, `forDisplay()` hides it from Operate when disconnected, `currentAutopilotWarningReportCount` projects it, and `AutopilotWarningBanner` renders the label. The count adds no state, persistence, telemetry request, network destination or command.

## Verification

- Fresh command: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --init-script "$env:TEMP\mama-gcs-phase185-build.gradle" --offline --no-daemon --max-workers=1 --console=plain` completed `BUILD SUCCESSFUL in 3m 29s` (97 tasks: 28 executed, 69 up-to-date). JUnit XML: 503 tests in 80 suites, 0 failures/errors. Lint XML: 0 errors, 20 warnings. The new unit test covers repeats, routine exclusion, empty/reset source list, link loss and unchanged source state.
- The verified disposable Pixel 3a API 34 emulator launched no-wipe with `-gpu swiftshader_indirect` and passed the required 30-second health preflight. Fresh app and Android-test APKs installed. `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.AutopilotWarningUiTest` returned `OK (5 tests)` in 23.87 seconds. Post-run Android health passed with 0 new crash/ANR events and no error dialog. Evidence: ignored `app/build/acceptance-20261117-122229-863c13fe/`. The runner listed the GPU option as unavailable; direct process inspection confirmed `-gpu swiftshader_indirect`.
- Ignored `app/build/reports/phase-192-warning-report-count-landscape.png` and `app/build/reports/phase-192-warning-report-count-portrait.png` were inspected. Both show the two-report label, latest compass warning, Messages link and instruments without clipping or overlap. The synthetic map is black because the fixture has no map source. The UI test also asserts that three retained warning reports count despite a routine message, that the banner disappears on an empty simulated source list and link loss, and that a new simulated warning shows a count of one.
- Live Cube warnings and physical-fault accuracy: unavailable and unverified. An empty-list fixture verifies the UI response, not the real telemetry session reset. This is targeted synthetic UI coverage, not a full app regression.

## Risks and next step

The number is retained reports, not distinct or active faults. Repeated reports inflate it; older reports can be evicted after 50 messages, and a reported fault may have resolved. The banner disappears on link loss; this does not prove vehicle health or a physical stop. Review the scoped diff and verify a real Cube message only when hardware is available. The user separately requested commit and push after this report was prepared; this does not close the live Cube acceptance gap.
