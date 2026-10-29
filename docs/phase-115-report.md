# Phase 115 — Intermittent menu investigation

2026-10-29. User approved this bounded investigation and phone testing. Baseline `83992bef4b789a5bc03ee2ba09cee41f33c0af3f`, clean; model/version unavailable. See D-115-01.

## Finding and scope

Phase 114's first Foundation UI run failed because `menu-more` was not found after tapping the station menu; its retry passed. That log does not identify whether the popup failed to open, closed, or was affected by activity recreation. No cause was established in this phase, and production navigation and shared test assertions were not modified.

Added `StationMenuRepeatUiTest`: a test-only in-memory `StationShell` alternates between rover configuration and application settings 12 times in each orientation. Each cycle asserts the destination is visible, invokes one navigation callback, verifies the resulting route/title, and checks the popup closes. This synthetic shell performs no persistence or transport operations. Rotation is restricted to the fixture activity and restored. The prior failing Foundation flow uses synthetic app data and may load the existing map provider. Its screenshot capture overwrites a preexisting test-owned QA screenshot; no routes or preferences were intentionally modified.

## Verification

- `gradlew.bat -I "%TEMP%\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 7 tasks executed and 53 up to date.
- `gradlew.bat -I "%TEMP%\mama-gcs-phase58-local-build.gradle" :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 3 tasks executed and 31 up to date; Android test lint analysis ran and lint report was up to date.
- Phone: installed only the test APK in place, then `adb shell am instrument -w -r -e class com.mamadrones.gcs.StationMenuRepeatUiTest com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner`: 2 passed, 24 total navigation cycles, `app/build/phase115-phone-20261029/menu-test.txt`.
- Phone: `adb shell am instrument -w -r -e class com.mamadrones.gcs.FoundationUiTest#secondaryScreensAndThemeAreReachable com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner` ran twice separately: 1 passed in each run, `foundation-1.txt` and `foundation-2.txt` in the same ignored build evidence directory. The original Phase 114 failure remains recorded; repetition cannot rule out a rare defect. No ANR/error window was observed in the final window check, which is not a complete crash-history assessment.

## Remaining

Investigate the original failure if it recurs, capturing popup state and rotation evidence; do not claim a fix without a demonstrated cause. Eleven data-mutating phone checks remain excluded, the full Android suite remains incomplete, and the physical Cube heartbeat remains unverified. Production readiness is not established. Commit and push require separate authorization.
