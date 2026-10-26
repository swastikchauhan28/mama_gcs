# Phase 64 — Enlarged-text mission editor header

Date: 2026-10-26

## Goal and scope

Check that the mission editor's File / Library action, draft title and Save draft action remain usable on a narrow phone at 2× text. The edit is limited to the mission editor header. No route persistence, file handling, telemetry, hardware or vehicle-command behavior is changed.

## Finding and change

At 320 dp and 2× text, the original single-row header constrained the draft title to a narrow middle column; the full draft name was not displayed. For widths below 680 dp when font scale is at least 1.8, the header now puts File / Library and Save draft in a wrapping action row, then shows the draft name and summary at full width with wrapping. Other widths and text scales retain the previous row.

The edit is in `app/src/main/java/com/mamadrones/gcs/presentation/screens/MissionScreen.kt`. The regression test is in `app/src/androidTest/java/com/mamadrones/gcs/VisualLayoutRegressionTest.kt`. It checks title visibility/non-ellipsis, both actions on-screen, and that the actions do not overlap.

## Verification

- Build: `gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL** (final build: 6 tasks executed, 73 up-to-date). APKs were installed on the disposable `emulator-5554`.
- Focused layout test: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#missionHeaderActionsAndDraftNameFitAtLargeText'` — **PASS, `OK (1 test)`**, Android health gate passed, SwiftShader emulator.
- Existing normal-text save-path regression: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FoundationUiTest#recoveryCleanupFailureAllowsSavingAnUnchangedDraft'` — **PASS, `OK (1 test)`**, Android health gate passed. This run was before the final test-only bounds assertions; production source was unchanged afterward.
- The first focused run against the stale APK under `app/build/outputs` matched zero tests (`OK (0 tests)`). That is not counted as a pass. The correct APKs are built under the configured temp build directory; after installing those APKs, the pre-fix test exposed the clipped title and the post-fix test passed. The original failure evidence is preserved.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` both completed on the final source/test state. No full Android suite or physical hardware run was performed.

Evidence directories:

- Pre-fix title failure: `app/build/acceptance-20261026-142851-0bcd97b7/`
- Final focused test: `app/build/acceptance-20261026-143343-0d3c28fb/`
- Normal-text save behavior: `app/build/acceptance-20261026-143214-1c7d539d/`

## Limits and review

This is a controlled Compose fixture at one width and English labels, not a real-device survey. Other locales, smaller widths and text scales above 2× need review. The stacked header consumes more vertical space at enlarged text, but keeps its actions and draft metadata visible. No migration or release rollback is needed; rollback is limited to the Phase 64 header/test/docs diff.

Review `MissionScreen.kt`'s `enlargedMobileHeader` branch and the focused test. Phase 64 is uncommitted. Phases 60–63 and the unrelated `desktop.ini` were preserved. Commit/push was not requested.
