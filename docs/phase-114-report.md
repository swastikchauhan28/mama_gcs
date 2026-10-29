# Phase 114 — Landscape header artifact investigation

2026-10-29. User approved investigation and phone testing. Baseline `e04fb57d53d5f2d6ca960e347a1ab8489e0d364b`, clean; model/version unavailable. Decision D-114-01 records alternatives and risks.

## Finding and change

The synthetic light landscape screenshot reproduced white icons over header text. A controlled comparison on the phone showed recognizable Android status icons over the header when the fixture status bar was visible, and a clear header when hidden. The production `MainActivity` hides system bars; a separately observed live dark landscape screen had a readable header. This is evidence for the fixture overlay in the tested landscape case, not proof about every device or production state. No production UI code was changed.

`StationHeaderInsetsUiTest` uses an isolated fake shell to compare portrait/landscape and light/dark with system bar requests; it asserts requested inset visibility and header semantics and saves screenshots. The portrait immersive screenshot still showed system icons despite the inset assertion, so those screenshots do not establish a complete portrait rendering fix. The focused Foundation light settings capture hides the status bar temporarily and restores its prior visible state, without changing the shared fixture or saved user preferences. System navigation bar handling differs from production and remains outside this correction.

## Verification

- Phone `StationHeaderInsetsUiTest`: 2 tests passed, with eight fixture captures. Landscape light before/after images were visually inspected; fixture portrait light was also inspected. Results are in `app/build/phase114-phone-20261029/` (ignored build artifacts, not permanent release evidence).
- Phone `FoundationUiTest#secondaryScreensAndThemeAreReachable`: first final run failed before capture because `menu-more` was absent after opening the menu; a retry passed (1 test). Cause of the first failure is unconfirmed. The new `settings-light-final.png` was inspected and its header was clear. Preserve `foundation-final.txt` and `foundation-retry.txt` while available.
- `gradlew.bat -I "%TEMP%\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 10 tasks executed and 87 up to date. Unit test and debug assembly tasks were up to date, not fresh executions; Android test compilation and lint analysis ran.
- Existing app and its saved theme/routes were not intentionally changed; test APK was updated in place and screenshot artifacts written to the app's external QA folder. No hardware connection or vehicle commands were used.

## Remaining

Investigate the intermittent menu failure if it recurs. A live production light-theme capture was not taken because the user requested saved data remain unchanged. Eleven data-mutating phone tests remain excluded; the complete Android suite and hardware heartbeat remain unverified. Screenshots are observations, not automated pixel assertions or a production release gate. No commit or push was authorized for this phase.
