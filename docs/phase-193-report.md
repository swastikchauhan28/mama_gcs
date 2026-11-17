# Phase 193 — Warning path navigation acceptance

Date: 2026-11-17. Baseline: `2962942`, clean `main` worktree. Scope: a synthetic Android UI test and project records; no production app code changed.

## Goal and path

Prove the existing Operate warning banner opens the production Diagnostics route with the same retained messages, then test Warnings only, search and Clear there. The test supplies an in-memory `VehicleState` with one routine message and two warnings to `MamaGcsApp` in the existing debug `FixtureTestActivity`. It opens no telemetry transport, sends no command and touches no phone or Cube data. Production routing is `AutopilotWarningBanner` → `onNavigate("diagnostics")` → `MamaGcsApp` NavHost → `DiagnosticsScreen` → `ConsolePanels.statusTexts`.

## Verification and corrections

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --init-script "$env:TEMP\mama-gcs-phase185-build.gradle" --offline --no-daemon --max-workers=1 --console=plain` passed. JVM task was up-to-date: its XML has 503 tests across 80 suites, zero failures. Lint has zero errors and 20 warnings. The Android-test APK compiled freshly. Subsequent focused APK rebuilds passed after test-only edits.
- First disposable emulator boot failed `Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30` with an Android System UI ANR dialog before tests. The ANR events were kept under ignored `app/build/reports/phase-193/first-boot-anr.txt`. A no-wipe restart passed the 30-second health gate.
- Initial six-test run: five previous tests passed; the new test failed because Compose found an offscreen filter node but a coordinate tap did not activate it. An inspected semantics tree showed `Selected = false` and `3 / 3 shown`. The test now scrolls filter and search controls into view before tapping. A later run passed all six tests, but a screenshot refinement moved Clear offscreen and the final assertion failed. The test now scrolls Clear into view too. These were test interaction errors; no app behavior or assertion requirement was weakened.
- Final command: `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.AutopilotWarningUiTest` returned `OK (6 tests)` in 38.778 seconds; post-run Android health passed with zero new crash/ANR events and no error dialog. Ignored evidence: `app/build/acceptance-20261117-132803-d18d3b5b/`.
- The final synthetic screenshot `app/build/reports/phase-193/warning-result-final.png` was inspected: Diagnostics shows Warnings only, `CURRENT SESSION · 1 / 3 shown`, and the compass warning row. The test also asserts Operate’s two-report count, route arrival, routine exclusion, search, and restored two-warning count after Clear.

## Limits and next

This is production navigation with synthetic display state, not a MAVLink parser test, live Cube message acceptance, physical fault assessment, or full app regression. The warning may be stale or spoofed, and retained history is bounded to 50 messages. Review the test/record diff; check real Cube `STATUSTEXT` on authorized bench hardware when available. The user subsequently authorized commit and push of this phase; release and hardware operation remain separate.
