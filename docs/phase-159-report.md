# Phase 159 — coverage controls and toolbar investigation

Date: 2026-11-03. User approved implementation and emulator testing. Baseline clean `221c59a3e8f518c9ca08f35bb00a6f94d450ac74`; model/version unavailable. See D-159-01.

## Scope and findings

Phase 158 screenshots showed coverage content partially clipped at the boundary of a fixed toolbar. Inspection of `MissionScreen` shows a toolbar followed by a weighted row containing the scrollable `LazyColumn`; the screenshots alone cannot distinguish ordinary viewport-edge clipping from an unreachable control in the app. No production fix is justified until a reproducible defect is demonstrated. The UI test fixture instantiates production `MissionScreen` with in-memory synthetic geometry; it does not test the full app shell or live hardware. No persistence, network, vehicle actions, or dependencies were changed.

Added normal-text and 320 dp / 2× fixture tests to compare toolbar and editor bounds and assert the coverage settings and comparison buttons are fully within the editor when scrolled into view. They check unchanged synthetic draft and no dispatched mission actions. These new checks are compiled but have not run; no visual verification or real-app reproduction was completed.

## Verification and attempts

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 88 tasks, 10 executed and 78 up-to-date; build included compilation of the new test APK. No fresh JVM suite or full regression was run.
- Initial health attempt ran before boot completed (`app/build/phase159/preflight.txt`, ignored). After boot, the 30-second preflight detected one ANR (`preflight-ready.txt`); exit/window/crash evidence was saved. No test ran.
- A no-wipe restart later reached boot, but the next preflight detected a System UI ANR dialog (`preflight-retry.txt`, `retry-window.txt`, `retry-exit-info.txt`, ignored). No dialog was dismissed.
- A third no-wipe startup remained offline initially and did not report completed boot within the follow-up window (`final-boot.txt`, `final-window.txt`, ignored). The disposable emulator was stopped. No test APK was installed and no UI assertions or screenshots were captured in Phase 159. The prior emulator instability remains unresolved.

## Remaining

Run the new tests at normal and Android system font 2× on a healthy disposable emulator, restore the font setting, and review screenshots. Confirm behavior in the full app shell before deciding whether a production change is necessary. Preserve failures; no full accessibility, hardware or release acceptance is established.


## 2026-11-03 follow-up — healthy emulator and full activity

After the recorded failures, a no-wipe disposable emulator passed a 30-second health preflight (`app/build/phase159/complete-preflight.txt`, ignored). Installed debug and test APKs with data-preserving updates. The acceptance runner reported Android healthy for each focused run; its GPU-option discovery was unavailable, so the GPU selection was not independently verified by that runner.

- `FieldCoverageUiTest#normalTextCoverageControlsStayBelowToolbar`: `OK (1 test)` in 9.605 seconds. Evidence: `app/build/phase159/complete-normal.txt` and `app/build/acceptance-20261103-174743-82ba89b5/`.
- `FieldCoverageUiTest#enlargedTextCoverageControlsStayBelowToolbar`: `OK (1 test)` in 10.386 seconds, with system font 2× and 320 dp fixture. Evidence: `complete-large.txt` and `app/build/acceptance-20261103-174810-d0a9e04b/`. Original system font 1.0 was restored and independently checked (`complete-font.txt`).
- Added `FieldCoverageAppTest#enlargedTextCoverageControlsStayBelowToolbarInApp`, launching the actual MainActivity without replacing the saved emulator draft. At system font 2× it returned `OK (1 test)` in 20.953 seconds with Android healthy. Evidence: `complete-app.txt` and `app/build/acceptance-20261103-175234-1d8218e1/`. Original font 1.0 was restored (`complete-app-font.txt`).
- Reviewed `phase159-normal-coverage-settings.png`, `phase159-large-coverage-options-open.png`, and both `phase159-app-coverage-*.png` under ignored `app/build/phase159/`. The targeted controls were fully visible after scrolling in the tested screens. The Phase 158 screenshot showed an item at the scroll boundary; the tested controls are not permanently hidden in these configurations. No production layout change was made.

This does not cover all text sizes, orientations, controls, devices or accessibility services. It is not a full regression, hardware verification or release acceptance. Earlier System UI ANRs remain undiagnosed despite this healthy run.

- After adding the real-activity test, `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded: 69 tasks, 10 executed and 59 up-to-date.
