# Phase 125 — Map-test stability repetitions

Date: 2026-10-31. User approved three repetitions of the affected 27-test group on the disposable emulator. Starting revision `78bb9e7e253c118c96bbf10064eb43626ef6ab47`, clean and synchronized. Model/version unavailable. No application or test code changed.

## Method and results

- Checked current APKs with `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: build successful, **79 tasks up-to-date**; this was not a fresh unit-test or lint run. Log: `app/build/phase125/build.txt`.
- Started the task-owned `MamaPhase116` API 34 emulator without snapshot or wipe, using `-gpu swiftshader_indirect`; verified boot and no Android error dialog, then installed both APKs with data-preserving updates. The original `font_scale` was unset and remained unset. Tests use emulator-local synthetic data and may use the previously configured map provider. No physical device was selected.
- Ran `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass 'com.mamadrones.gcs.ImmersiveWindowTest,com.mamadrones.gcs.LocalRepositoryTest,com.mamadrones.gcs.MapLoadRecoveryTest,com.mamadrones.gcs.MavlinkInspectorUiTest,com.mamadrones.gcs.MissionCoordinateEntryUiTest,com.mamadrones.gcs.MissionExchangeTest'` three times consecutively, stopping on failure.
- Run 1: **27/27 passed**, no recorded skips, new crash records or ANR events; healthy gate: `app/build/acceptance-20261031-104617-6873ec54/`.
- Run 2: **27/27 passed**, no recorded skips, new crash records or ANR events; healthy gate: `app/build/acceptance-20261031-104912-b15dc00d/`.
- Run 3: **27/27 passed**, no recorded skips, new crash records or ANR events; healthy gate: `app/build/acceptance-20261031-105207-64290157/`.

These are three repetitions of the same 27 tests, not 81 distinct tests. Each evidence directory has instrumentation output, Android health, crash and ANR logs. `app/build/phase125/runs.txt` records the sequence. Build artifacts are local ignored files and may be removed when build output is cleaned. Android health log buffering and same-second event filtering retain the limitations described in `docs/TESTING.md`.

## Limits and open work

The agreed three-run acceptance target passed on this emulator configuration. Earlier MapLibre native source-query SIGSEGV and emulator System UI freezes remain in the [Phase 124 report](phase-124-report.md); their root causes have not been proven fixed. Three successful runs cannot establish that a crash will never recur. This was not a complete current app regression, a physical Cube/R12/T12 test, field-coverage accuracy test, or production release gate. Human review and relevant hardware, security and release acceptance remain open. No commit or push was requested for this phase.
