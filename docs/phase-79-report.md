# Phase 79 — Modeled coverage uncertainty ranges

Date: 2026-10-27. User approved range display and emulator tests. Baseline HEAD `847d8912af82ef8e87d95dc21abbdb940525ff77`; Phases 76–78 are uncommitted and `desktop.ini` is unrelated. Preserve them. Model: gpt-6.1-terra. See [D-079-01](DECISIONS.md#d-079-01-show-conservative-modeled-area-ranges).

## Design and limitations

The existing background 128 × 128 sampler now counts complete grid cells definitely or possibly belonging to modeled headland or unplanned main area. The closest-boundary distance is 1-Lipschitz; adding/subtracting a cell's half-diagonal plus a numerical margin allows clear inside/outside and headland/main classifications. Field-boundary cells remain uncertain. A cell is definitely unplanned only if no ideal flat-ended rectangular tool strip intersects it; it is definitely covered if one strip contains the entire cell. Union-covered cells not individually contained remain uncertain. Lower and upper counts multiply by cell area; display rounds the lower down and the upper up. Possible-cell counts for the two categories may overlap, so upper bounds are not additive. The prior center-sample estimates remain separate. No sample inside the field keeps the prior unavailable result.

These intervals are intended as conservative ranges for the projected mathematical field and strips, subject to floating-point behavior and the specified margin; they are not statistical confidence intervals or independently certified geometry. Field survey error, GPS/RTK, rover/tool offset and slip, obstacles and actual worked soil are not included. Ranges may be wide and their bounds cannot establish safe turning, physical coverage or zero missed ground. No saved route, permission, dependency, network, mission upload or vehicle commands change. Calculation remains bounded by 128² cells, 64 edges and 500 passes; canceled work may finish but cannot publish stale results.

## Verification and recovery

The fresh JVM task completed **409 tests in 66 suites, with zero failures, errors or skips**. Analytical checks enclose known rectangle, triangle and concave areas; a synthetic strip pattern with every sample center covered still bounds the known 8,192 m² of missed gaps. Display formatting tests verify outward rounding. Targeted emulator acceptance passed **5/5 in 218.69 s** with a healthy Android gate (`app/build/acceptance-20261027-154135-eda4e124/`); GPU option unavailable. The actual app test checked the default headland in portrait/landscape and preserved the disposable draft/recovery; positive headland and shape checks were fixture-based. It was not a full-app suite or physical acceptance. The `app/build/phase79-ranges.png` fixture screenshot was visually inspected; separate ranges and model-only caveat are legible and the diagram can be reached by scrolling. Build and lint succeeded in 7m 44s (97 tasks, 28 executed, 69 up-to-date); lint analysis ran but `lintReportDebug` was up-to-date, so the prior warning inventory was not regenerated.

Commands used (PowerShell from repository root):

```powershell
.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
$phase79Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $phase79Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\debug\app-debug.apk"
& $phase79Adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $phase79Adb -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest,com.mamadrones.gcs.FieldCoverageAppTest'
```

Manual path: open Coverage for a synthetic field, set tool width, overlap and headland, and generate. Compare the two estimates with their modeled ranges and the uncertainty warning; scroll to inspect the diagram. Change a setting and verify old values clear. A zero sample count must not be interpreted as proven complete coverage. `git diff --check` passed; a scoped high-signal secret/URL scan found no matches, not a full audit. No existing tests, permissions, dependencies or command boundaries were removed. Commit/push only on explicit request. Recovery requires reviewed manual reversal of Phase 79 while preserving uncommitted Phases 76–78; no separate verified snapshot or machinery-qualified release exists.
