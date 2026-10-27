# Phase 78 — Estimated unplanned coverage areas

Date: 2026-10-27. The user approved separate estimates for main-area gaps and reserved headland, plus emulator testing. HEAD `847d8912af82ef8e87d95dc21abbdb940525ff77` with uncommitted Phases 76–77 and unrelated `desktop.ini`; all retained. Model: gpt-6.1-terra. See [D-078-01](DECISIONS.md#d-078-01-estimate-unplanned-areas-with-a-bounded-local-grid).

## Flow, limits and trust

For a valid local field preview, the bounded planner constructs the same working passes as before. The background calculation samples at most 128 × 128 cell centers over the local field bounding box. Each point inside the polygon is classified as headland if its distance to a field edge is below the entered headland width, otherwise as covered by an ideal flat-ended rectangular pass or unplanned main area. Counts times cell area estimate headland and gaps separately; overlap is not double-counted. The UI reports cell width/height. All samples, counts and results are volatile; invalid input and no-pass cases keep the prior visible error. A zero headland means exactly no reserved headland; zero detected gap samples is labelled none detected, not guaranteed zero. If no sample falls inside the field, both estimates are unavailable.

This is a deterministic sampling approximation with no guaranteed area error bound. The local projection, bounded resolution and rectangular tool model can miss or exaggerate small gaps and irregular headlands; total sample-derived areas need not exactly equal the independently calculated polygon area. The estimate does not inspect the soil, measure GPS/tracking accuracy, plan turns, or establish physical coverage. A 0 m headland remains a setting, not evidence that the rover can turn. A canceled calculation is bounded but computation may continue until completion; stale results cannot publish. No new persistence, network, permissions, dependencies, routes or vehicle commands.

## Verification and recovery

Fresh JVM run: 406 tests, 65 suites, no failures, errors or skips. First emulator attempt `app/build/acceptance-20261027-151313-93103f17/` finished with 3/5 passing in 158.411 s. Two assertions failed because Compose `assertTextContains` defaults to exact matching, while the warning and numeric labels were present within longer strings. Corrected all new partial-text checks to request substring matching, retaining the warning and numeric-result assertions. No commit/push requested. Recovery requires a reviewed narrow manual reversal of Phase 78 while preserving the existing uncommitted Phase 76–77 work; no separate verified snapshot or qualified machinery release is established.

Initial production build/JVM/lint succeeded in 7m 5s (97 tasks, 28 executed, 69 up-to-date). The corrected-test build `:app:assembleDebugAndroidTest :app:lintDebug` succeeded in 1m 10s (69 tasks, 8 executed, 61 up-to-date); lint analysis and report were up-to-date rather than a fresh warning inventory. Corrected emulator retry `app/build/acceptance-20261027-151852-cf11bc7b/` passed 5/5 in 111.629 s with a healthy Android gate. Its screenshot showed readable estimates and the narrow-gap warning, but the area-error caveat needed to be more explicit. Added “Estimates can be inaccurate” to the UI and a matching assertion. The final build, JVM tests and lint succeeded in 6m 30s (97 tasks, 24 executed, 73 up-to-date); 406 tests passed again. Lint analysis ran but its report remained up-to-date, so its previous warning count was not freshly regenerated. Final emulator run `app/build/acceptance-20261027-152312-c44cf778/` passed 5/5 in 199.945 s with a healthy Android gate; the GPU launch option was unavailable.

The final fixture capture `app/build/phase78-estimates-final.png` was visually inspected: the two estimates, cell dimensions and warning are legible; the diagram is below and can be scrolled into view. Tests exercise rectangles, a triangle, a concave field, changed/invalid inputs, no-pass rejection, and a thin field with no interior samples. This is targeted emulator evidence, not a full Android suite or physical acceptance. `git diff --check` passed; a scoped high-signal credential/URL scan found no matches, not a repository-wide audit. No tests, dependencies or command boundaries were removed.

Commands (PowerShell, repository root):

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
$phase78Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $phase78Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\debug\app-debug.apk"
& $phase78Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $phase78Adb -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest,com.mamadrones.gcs.FieldCoverageAppTest'
```

Manual path: choose a field outline, open Coverage → Coverage settings, enter width, overlap and headland, and generate the preview. Read both estimates and the sampling warning; scroll to inspect pink and amber areas. Change a setting to confirm the previous result clears. No working passes, invalid values or missing field samples must never be displayed as complete coverage.
