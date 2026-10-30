# Phase 121 — Fit draft with larger text and constrained maps

2026-10-30. User approved Phase 121 and emulator testing. Baseline `08ef3e347e9e4f35a7f2c115aa48e6821ebe0e48` was clean; baseline is a recovery reference, not a verified release. Model/version unavailable. Decisions D-121-01 and D-121-02.

## Goal and finding

Exercise one-, two- and three-point synthetic routes at 2x Compose text scale in portrait-shaped 300x320 dp and landscape-shaped 350x230 dp map fixtures, using regular and compact controls. These are constrained map areas in a landscape emulator activity, not separate physical phone orientations or a system-wide accessibility setting. The fixture uses the existing configured map provider and no saved operator draft, phone, vehicle link or hardware.

The initial `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.SmallMapFitAcceptanceTest'` produced two failures after one-point checks; Android health gate was clear. Evidence: `app/build/acceptance-20261030-142538-02620f50/`. Screenshots `app/build/phase121-emulator/failure-phase121-standard-portrait-2.png` and `failure-phase121-compact-portrait-2.png` show two markers overlapping after excessive zoom-out: the expanded 2x controls left almost no space for the right-inset fit. The earlier `phase121-standard-portrait-1.png` showed a single marker partly covered by Fit draft; it was superseded by the final screenshot of the same name after the fix. The initial screenshots are test-owned temporary data under ignored build output.

## Change

For planning maps at font scale 1.8 or greater, expose a Map controls button and start the controls collapsed. Pressing Fit draft collapses the group so it cannot cover the preview; multi-point fitting uses the available map width. Users can reopen the controls. Normal-text control behavior is unchanged. Single-point fit keeps its previous centered camera but no longer has the expanded controls over it after fitting. This changes map presentation, not mission coordinates, persistence, network destination or vehicle commands. Smaller available map height and an extra tap are trade-offs. No claim is made for arbitrary screen sizes, locale lengths, provider outages or physical accuracy.

## Verification

- Initial test APK build: `:app:assembleDebugAndroidTest` succeeded (10 executed, 50 up-to-date).
- After the fix: `gradlew.bat -I %TEMP%\mama-gcs-phase58-local-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded (25 executed, 72 up-to-date). Fresh JVM results: 478 tests, 0 failures/errors/skips.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.SmallMapFitAcceptanceTest'`: **2 passed**, Android health gate clear, 2048 MB emulator. Each case tests one, two and three points in each fixture shape. Evidence `app/build/acceptance-20261030-143936-92246d7c/`.
- Updated existing enlarged-text coverage test to assert automatic collapse, reopening, and manual closing. `:app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded afterward (9 executed, 60 up-to-date).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#selectedCoverageReachesOfflineMapInPortrait,com.mamadrones.gcs.FieldCoverageUiTest#enlargedTextCoverageMapControlsRemainUsable,com.mamadrones.gcs.MissionMapDialogTest#mapSelectionAndLongPressRequireConfirmationAndExplicitSave'`: **3 passed**, Android health gate clear. Evidence `app/build/acceptance-20261030-144950-8993b125/`. The prior two-test small-map run used the same production APK; the later test-only edit did not change those tests.
- Visual review of final `phase121-standard-portrait-1.png`, `phase121-standard-portrait-2.png`, `phase121-standard-landscape-3.png`, and `phase121-compact-landscape-3.png` in `app/build/phase121-emulator/` shows numbered markers clear of the collapsed controls. Some captures show a plain/partially loaded basemap; the checks are for marker and label visibility, not tile completeness.
- Emulator startup failures preceded successful runs: initial System UI ANR (`startup-window.txt`, `startup-anr.txt`); two post-fix ANRs (`fixed-startup-*`, `fixed-retry-*`); and an ANR before the regression batch (`regression-anr-*`), all under `app/build/phase121-emulator/`. The runner did not dismiss dialogs. A lower-memory launch passed the small-map tests, and a later clean restart passed the regression batch. The task-owned emulator was stopped without wiping data.

## Remaining and recovery

Full Android regression, real devices, actual system font-scaling behavior across Android versions, smaller viewports, expanded-controls visibility before fitting, physical Cube telemetry, field coverage accuracy, and release/owner review remain unverified. The prior MapLibre test-query native crash remains documented in Phase 120; no production lifecycle guarantee follows from these tests. To roll back, review and revert this phase's source/test changes against `08ef3e3`; that commit is not a verified release and no rollback was executed. Commit/push require separate authorization.
