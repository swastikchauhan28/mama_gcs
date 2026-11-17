# Phase 191 — Search retained autopilot messages

Date: 2026-11-17. Baseline: `744fe5b`, clean `main` worktree. Scope: read-only Diagnostics UI and its synthetic tests; hardware unavailable.

## Goal and path

Finding a named warning among the at most 50 selected-autopilot `STATUSTEXT` messages required scrolling. Diagnostics now accepts a short text query and combines trimmed, case-insensitive message matching with the existing warnings-only filter. The field has an explicit Clear action; the card keeps shown/retained counts and explains zero matches. `VehicleRepositoryImpl` continues to own the bounded in-memory list; `ConsolePanels.statusTexts` projects it, and `DiagnosticsScreen` owns the saveable display query. The query can survive UI recreation through Android saved state, but is not written to app storage. Search does not mutate, request or transmit messages.

## Verification

- Fresh command: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --init-script "$env:TEMP\mama-gcs-phase185-build.gradle" --offline --no-daemon --max-workers=1 --console=plain` completed `BUILD SUCCESSFUL in 8m 14s` (97 tasks: 64 executed, 33 up-to-date). JUnit XML: 502 tests in 80 suites, 0 failures/errors. Lint XML: 0 errors, 20 warnings. The new unit test covers trimmed case-insensitive search, combination with severity filtering, zero matches, clear query and unchanged source state.
- Two initial no-wipe starts of the verified Pixel 3a API 34 emulator with `-gpu swiftshader_indirect` failed the required health preflight with Android System UI ANR dialogs before APK installation or instrumentation. Those failures were preserved; no dialogs were dismissed. After the user closed unused Chrome windows, a fresh no-wipe start passed the 30-second health preflight. Memory pressure was observed, but its causal role is unproven.
- The fresh app and Android-test APKs installed on that disposable AVD. `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.AutopilotWarningUiTest` returned `OK (5 tests)` in 30.009 seconds. Its post-run gate found 0 new crashes/ANRs and no error dialog. Evidence: `app/build/acceptance-20261117-115411-05241647/`. The runner recorded the GPU option as unavailable; direct process inspection confirmed `-gpu swiftshader_indirect`.
- `app/build/reports/phase-191-message-search-portrait.png` and `app/build/reports/phase-191-message-search-landscape.png` were inspected. The portrait image shows the query, Clear action, selected Warnings only chip, `1 / 3 shown` count, full warning and caveat without clipping. The landscape image shows the search field, filter and message panel without overlap. Images are ignored local evidence, not tracked source.
- Live Cube messages and physical fault behavior: unavailable and unverified. This is targeted synthetic UI coverage, not a full app regression.

## Risks and next step

Filters can hide a message from the current view, and a previous warning can remain after its physical cause ends. Counts and visible controls help disclose this, but absence from the last-50 retained view is not a safety verdict. Review the scoped diff; verify a real Cube message only when hardware is available. The user separately requested commit and push after this report was prepared; this does not close the live Cube acceptance gap.
