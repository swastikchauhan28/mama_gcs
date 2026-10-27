# Phase 80 — Compare field coverage directions

Date: 2026-10-27. The user approved the side-by-side comparison and emulator tests. HEAD `847d8912af82ef8e87d95dc21abbdb940525ff77` with uncommitted Phases 76–79 and unrelated `desktop.ini`; preserve all. Model: gpt-6.1-terra. See [D-080-01](DECISIONS.md#d-080-01-compare-both-cardinal-coverage-directions).

## Behavior and boundaries

Plan route → Coverage → Coverage settings supplies the same field outline, working width, overlap and headland to Compare directions. The in-memory comparison planner runs north/south and east/west sequentially on the existing background dispatcher, then publishes both as independent `CoverageCalculation` results. Each card shows its own diagram, pass count, working length and modeled unplanned-area range, or an individual error. Cards are side by side and horizontally scrollable on narrow screens; no recommendation or automatic selection is made. The selected single-direction preview is not replaced. Editing a setting or outline cancels and clears the comparison; late calculations cannot publish stale results. The UI uses the existing 500-pass/64-vertex/128² limits per calculation, so comparison can take twice as much bounded work and canceled computation may finish in the background.

The comparisons omit turn paths, obstacles, overhang, actual tracking and soil coverage. Working lengths exclude turns; the ranges concern ideal geometric strips, retain sampling/numerical uncertainty and are not operational validation. No dependency, permission, persistence, new external destination, saved waypoint mutation, upload or command path is added. Invalid settings cannot start a comparison; a direction exceeding bounds remains visibly unavailable without hiding the other.

## Verification and recovery

Fresh JVM run passed 412 tests / 67 suites, with no failures, errors or skips. Initial emulator run `app/build/acceptance-20261027-155834-0f9cd86b/` finished 5/7 in 300.305 s: the enlarged-text fixture and the real-app landscape comparison could not display the first card's pass count when the header and cards occupied one tall lazy-list item. The standard-size three-shape comparison passed and a screenshot showed both metrics side by side. Split the header and cards into separate lazy-list items to improve vertical navigation, retaining the visibility assertions; a rebuild and emulator retry are required. Visual review also led to an explicit note that overlapping modeled ranges cannot establish which direction leaves fewer gaps.

The initial build completed in 12m 44s (97 tasks, 28 executed, 69 up-to-date); its compiled APK and initial emulator result predate the navigation and warning changes. The lint report was up-to-date, so it was not a fresh warning inventory. A complete rebuild and retry of the changed source are in progress.

Intermediate split-layout retry `app/build/acceptance-20261027-161038-831b7ff6/` finished 1/2 in 106.649 s: enlarged-text fixture passed, but the real-app portrait test's automatic scroll-to-card assertion failed. A screenshot of the enlarged-text layout showed cards were cramped, so enlarged text now uses a wider card with horizontal scrolling. The emulator helper will also try short user-like vertical swipes when a card is not yet displayed, retaining the visible-text assertions; whether the real app can actually reach both cards remains to be tested. Added a diagram color key for comparison-only use. Neither the wider-card change nor this helper has passed final acceptance yet.

The intermediate rebuild ran the JVM tests but failed lint in 9m 25s: `SuspiciousIndentation` at the comparison action's early-return guard, with 28 warnings in the fresh report. Source was being edited while this intermediate build ran. Both preview and comparison guards were rewritten with explicit braces; the lint check is retained and a fresh, stable-source build is required.

The next stable-source build passed Kotlin app compilation and the JVM task but failed Android-test compilation in 2m 28s: the new scroll helper used `isDisplayed()` as a matcher, whereas this Compose version exposes it as a node method. Corrected the test helper to call `target.isDisplayed()` and retained the visible assertions. Rebuild and final emulator acceptance remain required.

The corrected APK's targeted run `app/build/acceptance-20261027-162036-6f04bb6c/` completed 6/7 in 269.16 s. The real-app portrait/landscape check, enlarged-text check, independent errors and existing flows passed. The three-shape comparison test failed with a Compose `SnapshotStateObserver` multithreaded rendering/semantics exception; the stack did not identify a definitive application or emulator cause. The unchanged test passed separately in `app/build/acceptance-20261027-162530-e0c30491/` (1/1, 48.205 s, healthy Android). The original failure remains recorded; this retry does not establish its cause.

The first passing real-app landscape capture showed the pass-count line only partly above the footer even though Compose's `assertIsDisplayed` passed. Strengthened the test helper to require the whole target's vertical bounds inside the editor and scroll in small user-like steps in either direction. Final emulator run `app/build/acceptance-20261027-162947-926baf36/` passed **7/7 in 280.502 s** with a healthy Android gate; GPU option unavailable. Both pass-count lines fit vertically in the real app's portrait and landscape tests after user-like scrolling, and the local draft/recovery were preserved. The final enlarged-text fixture screenshot still shows the second card partially offscreen after automated horizontal scrolling; users need to swipe sideways and full-width/system-wide large-text and screen-reader accessibility remain unverified. The landscape comparison requires vertical scrolling for metrics and diagrams.

The stable-source full build succeeded in 7m 36s (97 tasks, 14 executed, 83 up-to-date): the 412-test JVM result was reused from the preceding build, which compiled the same production source; its fresh lint report contained 0 errors and 28 warnings. The test-only rebuild for stronger vertical visibility succeeded in 3m 50s (69 tasks, 9 executed, 60 up-to-date), with its lint report up-to-date. Inspected `app/build/accepted-phase80-comparison.png`, `accepted-phase80-app-landscape.png` and `accepted-phase80-large-comparison.png`; portrait metrics/diagrams and model caveats are readable. `git diff --check` and a scoped high-signal credential/URL scan were performed; this is not a whole-repository security audit. No dependency, permission, command boundary or existing test was removed.

Commands (PowerShell, repository root):

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
$phase80Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $phase80Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\debug\app-debug.apk"
& $phase80Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $phase80Adb -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest,com.mamadrones.gcs.FieldCoverageAppTest'
```

Manual path: choose a synthetic Field outline, open Coverage → Coverage settings and set tool width, overlap and headland. Tap Compare directions and inspect both cards, swiping sideways on narrow/large-text displays and scrolling vertically as needed. Check that edits clear the old comparison and that a direction-specific failure stays visible. Comparison does not select a direction or alter the saved draft.

The real-app test uses and restores the disposable emulator draft/recovery; fixture tests use synthetic fields and in-memory actions. Commit/push only on request. Recovery requires a reviewed scoped manual reversal of Phase 80 while preserving uncommitted Phases 76–79; no separate verified snapshot or machinery-qualified release.
