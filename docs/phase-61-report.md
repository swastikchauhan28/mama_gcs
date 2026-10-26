# Phase 61 — enlarged mobile workspace text

Date: 2026-10-26. Source baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`. The separate uncommitted Phase 60 diagnostics and existing `desktop.ini` were preserved.

## Goal, finding and design

Check whether the mobile workspace stays readable at 320 dp and 2× system text. Phase 59 covered 1.3×. The `StationShell` header passed new checks for two long, unellipsized titles, the full disconnected shortcut label, its position below the title and visible safety access. A real 2× emulator screenshot then showed `Plan` and `Drive` splitting mid-word in the adjacent Operate map toolbar. See [D-061-01](DECISIONS.md#d-061-01-check-enlarged-mobile-workspace-text-before-changing-layout).

`DashboardScreen` now gives that toolbar 120 dp instead of 72 dp when the map is narrower than 600 dp and system text is at least 1.8×. The third label becomes `More` at that scale; its existing `more` destination is unchanged. The mode/arming status card uses the remaining horizontal space beside the toolbar. Action labels are constrained to one line. At normal text size, the toolbar stays 72 dp, the third label remains `Systems`, and the original card spacing remains. The controls read only local UI state and navigate to existing pages; they persist no data, open no new transport and send no vehicle command. The transient font/width branch has two outcomes and no retained data. The wider overlay consumes more map area; a smaller viewport or larger text scale may need another arrangement.

## Fresh verification

- `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: passed, `BUILD SUCCESSFUL in 3m 42s` (14 executed, 65 up-to-date). The init script changes only temporary local build output; repository build configuration is unchanged.
- On the disposable SwiftShader Pixel 3a API 34 emulator, `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#enlargedOperateMapActionsKeepWholeLabelsBesideStatus'`: `OK (1 test)` in 10.594 s, Android healthy. The test checks Plan, Drive and More as complete single-line labels, and no toolbar/status-card overlap. Evidence: `app/build/acceptance-20261026-120022-78a2d3f2/`.
- The expanded header test `VisualLayoutRegressionTest#mobileHeaderShowsFullWorkspaceTitleAboveConnectionShortcut` passed `OK (1 test)` in 5.566 s, Android healthy. Evidence: `app/build/acceptance-20261026-115156-34fa16ea/`. It ran before the toolbar-only production edit; that edit did not touch `StationShell`.
- After restoring emulator text scale to 1.0, `RoverWorkspaceUiTest#portraitMapAndSafetyRemainAccessible` passed `OK (1 test)` in 33.405 s, Android healthy. Evidence: `app/build/acceptance-20261026-120133-8ce30138/`. Emulator font scale was confirmed restored to `1.0`.
- `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: passed, `BUILD SUCCESSFUL in 6m 16s`. Fresh JVM XML totals: 389 tests, 0 failures/errors/skips. Lint: 0 errors, 28 warnings. Some Gradle tasks were up to date; this was not a full Android UI suite.
- Visually inspected real emulator captures at 2×: `app/build/phase61-before.png` has broken toolbar words; `app/build/phase61-after.png` shows Plan, Drive and More complete with separate status card and visible safety footer. Both files are ignored local evidence, not tracked product assets. The after capture displayed a map-style loading/error placeholder, so it does not establish map tile availability.

## Limits and review

This verifies one emulator size and text scale, not every device or physical phone. The full Android UI suite, Cube/R12/T12 path and rover behavior remain unverified. Phase 60's intermittent screenshot timeout and first-run boot-service ANRs remain recorded separately.

Review `DashboardScreen.kt` for the ≥1.8× narrow-screen branch, unchanged destinations, one-line labels and status-card spacing. Review `VisualLayoutRegressionTest.kt` for the added 2× assertions without relaxed existing checks. Inspect the screenshots and focused Android health evidence. Review the separate Phase 60 diff independently. No dependency, permission, schema, secret, external destination or vehicle-command path was added. Reverse only this UI/test/docs diff after review to roll it back; no migration applies. The source baseline is not a production-qualified release. Phase 61 remains uncommitted and unpushed pending a separate request.
