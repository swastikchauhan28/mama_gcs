# Phase 122 — Android system font scaling and mission map fit

2026-10-30. User approved implementation and emulator testing. Baseline `8f7ded86f8d5a4a8b95f4272a42a7c268e2c7841` was clean; it is a recovery reference, not a verified release. Model/version unavailable. Decisions D-122-01 through D-122-03.

## Scope and finding

Use a disposable emulator's Android system font setting rather than a Compose-only override. The real mission activity uses a synthetic one-point draft, adds two points, fits in portrait, rotates, and removes points while checking three, two and one in landscape. The test verifies system font scale, controls, matching rendered label features, marker pixels, and pairwise separation. It backs up and restores the disposable emulator's draft, recovery draft and basemap. Host commands save the emulator's exact font setting and restore it in `finally`; interrupted native processes can leave emulator-local draft data behind. No phone, hardware, vehicle link, mission upload or new destination was used. Production route bounds remain `MissionDraft.MAX_WAYPOINTS` 250; tests use at most three points.

The first actual-2x run failed in landscape because the test attempted to scroll a control with no scrollable parent. Evidence `app/build/acceptance-20261030-150545-8df08921/`. The normal-1x test passed `app/build/acceptance-20261030-150657-29105493/` and the original font setting was restored. Fixed the test's orientation-specific navigation without removing assertions.

The corrected 2x test initially passed `app/build/acceptance-20261030-151501-01dbb986/`, but manual review showed overlapping landscape markers in `app/build/phase122-emulator/phase122-landscape-3-overlap.png`. Its first restoration check saw `1.0` instead of original unset after a delete; a second delete and a settled recheck confirmed `null` (`final-font-restoration-settled.txt`). The stronger separation assertion subsequently failed after 35 seconds with points 17–23 pixels apart; evidence `app/build/acceptance-20261030-153817-cd277db1/`. This confirms the initial passing test was insufficient to establish readable separated markers.

## Change

At large-text planning scale only, cap the normal 48 dp camera-fit padding to one fifth of the smaller measured native map dimension. In a short landscape map this leaves sufficient fitting height for the tested route; normal-font fit, single-point camera and mission data are unchanged. Smaller padding places markers nearer viewport edges; this is a display trade-off and does not guarantee arbitrary layouts or physical navigation accuracy. The real-activity test now requires both rendered features and marker pixels plus pairwise separation, and retains failure images.

## Verification

- Final production/test build: `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded (20 executed, 77 up-to-date). Fresh JVM results: 478 tests; 0 failures, errors or skips.
- With actual emulator `settings put system font_scale 2.0`, `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.MissionMapWorkflowAcceptanceTest#androidLargeFontFitsOneTwoAndThreePointsInBothOrientations'` passed, Android health gate clear. Evidence `app/build/acceptance-20261030-154817-e47517c7/`. Actual activity configuration verified scale >=1.8. Each one/two/three-point scenario was exercised in portrait and landscape.
- With emulator scale 1.0, `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.MissionMapWorkflowAcceptanceTest#androidNormalFontKeepsPlanningControlsVisible'` passed with a healthy Android gate. Evidence `app/build/acceptance-20261030-154921-7922937d/`.
- Original font setting was unset (`null`); restoration was verified as `null` after the tests and again after regressions (`app/build/phase122-emulator/fix-font-restoration.txt`, `fix-font-final.txt`). The task-owned emulator was stopped without wiping data.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.SmallMapFitAcceptanceTest,com.mamadrones.gcs.MissionMapDialogTest#mapSelectionAndLongPressRequireConfirmationAndExplicitSave'`: **3 passed**, health gate clear. Evidence `app/build/acceptance-20261030-155013-7bd09700/`.
- Reviewed synthetic `phase122-portrait-1.png`, `phase122-portrait-3.png`, and final `phase122-landscape-3.png` under `app/build/phase122-emulator/`; the landscape markers are separated. The landscape top bar truncates the workspace title to `Pl...` at this text scale; that separate accessibility issue remains open. Some screenshots may show incomplete basemap tiles; map-provider reliability is not proven.
- Startup Bluetooth crash and repeated System UI ANRs are preserved in `app/build/phase122-emulator/` startup and separation ANR logs. Android error dialogs were not dismissed; a later clean launch permitted the tests. Raw ignored build evidence is temporary.

## Remaining and recovery

Full Android regression, enlarged-text landscape header truncation, smaller viewports, other Android versions/devices and languages, provider outages, full-resolution tile loading, physical hardware telemetry and real field accuracy remain unverified. The older MapLibre native test-query crash from Phase 120 remains a separate unresolved lifecycle risk. This software test is not mission execution or release approval. To roll back after review, revert Phase 122 against `8f7ded8`; that baseline is not a verified release, and no rollback was performed. Commit/push require explicit request.
