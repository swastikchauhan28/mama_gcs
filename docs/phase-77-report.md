# Phase 77 — Reserved headland preview

Date: 2026-10-27. The user approved adding headland width to the local field preview and emulator validation. Starting Git revision `847d8912af82ef8e87d95dc21abbdb940525ff77`; Phase 76 was uncommitted, alongside unrelated `desktop.ini`. Preserve that work. Model: gpt-6.1-terra. See [D-077-01](DECISIONS.md#d-077-01-reserve-an-interior-headland-in-the-local-coverage-preview).

## Scope, ownership and limits

Coverage settings adds a volatile numeric headland width, default 0 m and bounded to 0–100 m. Width/overlap/direction/headland changes invalidate the current preview. The UI passes the current validated outline and values to the bounded background planner. Zero headland preserves the Phase 76 calculation. For a positive headland, each scanline is clipped by boundary-edge capsules with a radius of headland plus half the tool width; this conservatively reserves the rectangular footprint, including around concave vertices. At most 64 boundary segments and 500 working passes/rows are allowed. The diagram overlays the interior distance-to-boundary band in amber; green strips represent planned passes, and pink denotes remaining unplanned main area. An overlarge headland or tool width produces an explicit no-passes result.

The choice is deliberately conservative: circular clearance can leave extra unplanned areas near corners and narrow sections. The preview does not generate headland working passes, turns, obstacle avoidance, uploadable routes or actual soil-work measurements. The local projection, nominal tool width, manual headland width, and diagram raster are estimates. Real implement offset, vehicle dimensions, steering, tracking error, boundaries and physical coverage remain unverified; no claim of safe or complete ploughing is made. No new dependency, permission, persistence, external destination, or hardware command. The previous saved route/recovery data remains separate.

## Verification and review

Fresh `:app:testDebugUnitTest` executed 403 tests in 65 suites, with zero failures, errors or skips. The app and Android-test APKs assembled and lint completed; the overall build succeeded in 6m 53s (97 tasks, 28 executed, 69 up-to-date). Lint analysis ran; `lintReportDebug` was up-to-date, so the previously recorded 28 warnings are not a fresh warning inventory. Commands, in PowerShell from the repository:

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
$phase77Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $phase77Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\debug\app-debug.apk"
& $phase77Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $phase77Adb -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest,com.mamadrones.gcs.FieldCoverageAppTest'
```

The verified disposable API 34 Pixel 3a emulator completed **5/5 targeted tests in 188.254 s**, with a healthy Android gate; GPU launch option unavailable. Evidence: `app/build/acceptance-20261027-141349-605d3016/`. The new fixture tested rectangular, triangular and concave outlines, invalid width and stale-preview invalidation, without action callbacks. The existing real-app test checked the zero-headland setting and preserved the original synthetic draft/recovery through portrait/landscape. These are targeted tests, not the full Android suite or physical acceptance.

Inspected fixture images `app/build/phase77-rectangle.png`, `phase77-triangle.png` and `phase77-concave.png`: amber band, green passes and pink unplanned regions are distinct. Positive-headland real-app screen and system-wide large text, other devices, IMEs, locales and screen readers remain unverified. The geometry tests independently check clearance at sampled points for sloped and concave fields; they do not prove all geometries or physical behavior. `git diff --check` passed, and a scoped high-signal key/token/URL scan of affected source/tests found no matches; this is not a repository security audit. No removed tests or new dependencies/configuration.

Manual check: create a test field outline; Coverage → Coverage settings → enter tool width and headland → Generate preview. Inspect amber headland and pink gaps, change the field or width and confirm the previous result clears. Saved route and vehicle commands must remain unchanged. The user must verify an actual implement and turning needs before field use.
