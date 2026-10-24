# Verification and release checklist

Run commands from the repository root in PowerShell. This is a procedure, not a claim that its checks ran. Record revision, dirty state, exact commands, environment, timestamps, results and evidence in the current phase report. See [instructions](../AGENTS.md), [handover](HANDOVER.md) and [security boundaries](security.md).

## Evidence baseline - historical, not Phase 44 reruns

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| [Phase 43](phase-43-report.md): 380 JVM tests, 62 suites, no failures/errors/skips; debug builds passed; lint 0 errors/32 warnings | Recorded software checks; final test-only follow-up reused up-to-date JVM results | Physical behavior or a new test run in Phase 44 |
| Phase 43 final `OK (14 tests)` | Targeted RC/PWM, RC activity, workspace and visual regressions passed | Entire current Android inventory or clean endurance: a system ANR appeared afterward |
| [October 19 regression](test-report-2026-10-19.md): 118 software tests, then 35 targeted visual-related checks | Earlier complete run and subsequent focused repair checks | A complete rerun after Phase 43 additions |
| [October 15 phone validation](ble-stability-validation-2026-10-15.md) | Recorded T12_485 GATT continuity/background/recovery checks | Cube/R12 telemetry, motor control or verified remote power-loss detection |

Raw logs/screenshots under ignored `app/build/` are local and may disappear after cleaning. Retain sanitized copies outside build output when a release depends on them; do not commit sensitive captures. If raw evidence is unavailable, cite the report as historical rather than claiming to have inspected the output.

## 1. Preflight and documentation-only changes

```powershell
git status --short --branch
git rev-parse HEAD
git diff --stat
git diff --check
git diff --cached --check
```

Expected: understood starting changes, verified revision and no whitespace errors. Review untracked files separately: ordinary `git diff` does not include them. For documentation-only work, inspect all new/changed content, verify relative file links and any heading fragments, and syntax-check command examples without executing installs/rollback. Confirm app/source/test/build files are unchanged. A documentation-only phase does not require an emulator or hardware connection. Record any limitations of automated link/secret checks.

## 2. Build, JVM tests and lint after code changes

Use the toolchain in [README](../README.md) and the [version catalog](../gradle/libs.versions.toml). Do not change dependencies as a test-repair shortcut. The following can download configured build dependencies; use the existing trusted configuration, never print keys or introduce new destinations casually.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain
```

Expected: exit 0 / `BUILD SUCCESSFUL`, no test failures/errors, understood skips and no lint errors. Inspect XML under `app/build/test-results/testDebugUnitTest/` and lint output under `app/build/reports/`; do not rely on the console heading alone. The 32-warning historical baseline is not blanket acceptance of new warnings. If fresh execution of the JVM test task is required:

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun --no-daemon --max-workers=1 --console=plain
```

Label `UP-TO-DATE`/cache reuse honestly. Record changes to the expected test inventory; no deleted/ignored tests simply to improve the result.

## 3. Disposable-emulator UI acceptance

Use only a disposable emulator without operator data. Review test side effects first: fixtures change test app state, document-picker tests create/remove their uniquely named temporary files, and online map tests use the configured provider/network. Do not select a physical phone for the software suite. Never run competing UI automation during instrumentation.

Build both APKs first. Set the serial from `adb devices -l`, not from old reports. The example intentionally refuses to continue until a verified serial is supplied:

```powershell
$ErrorActionPreference = 'Stop'
$testAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $testAdb devices -l
$testEmulatorSerial = '' # Fill from the current device listing after identifying the disposable emulator.
if ($testEmulatorSerial -notmatch '^emulator-\d+$') { throw 'Select a verified disposable emulator serial.' }
$testBootState = & $testAdb -s $testEmulatorSerial shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or ($testBootState -join '').Trim() -ne '1') { throw 'Emulator is not booted.' }
$testWindows = & $testAdb -s $testEmulatorSerial shell dumpsys window
if ($LASTEXITCODE -ne 0 -or $testWindows -match 'Application Not Responding:|Application Error:') {
    throw 'Emulator has an error or cannot be inspected; stop before installation.'
}
& $testAdb -s $testEmulatorSerial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'App installation failed; do not uninstall or clear data as a workaround.' }
& $testAdb -s $testEmulatorSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
```

Confirm boot returned `1` and inspect the launcher/window for crashes before proceeding. Map tests need [map configuration](maplibre-maptiler.md); do not expose its key. The [acceptance runner](../scripts/Test-AndroidAcceptance.ps1) requires a booted emulator, rejects pre-existing ANR/crash dialogs, checks JUnit output rather than just the ADB exit code, and refuses a green result if post-run Android boot/window or new crash/ANR-log checks fail. It records the matched AVD's `-gpu` launch option and the health result in ignored `app/build/acceptance-*/android-health.txt`, with new crash/ANR lines in sibling evidence files. It does not install APKs or clear Android logs. An unavailable GPU option is recorded honestly; log-buffer rollover and same-second pre-existing events are limitations, so preserve any concurrent logs when diagnosing an unstable AVD.

On this machine, a no-wipe Pixel 3a API 34 emulator run with `-gpu host` produced a System UI `HardwareRenderer` finalizer crash and aborted a monolithic software suite. A separately restarted disposable AVD with the installed emulator's `-gpu swiftshader_indirect` option completed the same 130 software methods once; see [Phase 55's dated follow-up](phase-55-report.md#2026-10-24-follow-up--phase-56-emulator-graphics-comparison). For repeatable local acceptance, launch only the verified disposable AVD with that software-renderer option and the established no-snapshot/no-wipe options, verify its actual process command line and boot/error-window state, then run the unmodified suite. Preserve both failed and passing logs. This is a test-environment workaround, not an app fix or proof of renderer stability, real-phone compatibility or physical rover safety. Never dismiss an ANR or restart an operator's device to force a pass.

Focused regression (not the full suite):

```powershell
./scripts/Test-AndroidAcceptance.ps1 -Serial $testEmulatorSerial -Adb $testAdb -TestClass 'com.mamadrones.gcs.RcPwmScreenTest,com.mamadrones.gcs.RcInputActivityUiTest,com.mamadrones.gcs.RoverWorkspaceUiTest,com.mamadrones.gcs.VisualLayoutRegressionTest'
```

For complete software coverage, inspect the current `app/src/androidTest/java/com/mamadrones/gcs/*Test.kt` inventory first. The following forms the two previously used disjoint batches, excluding the separately authorized physical class. Review newly added tests before using the list; new physical tests must not silently enter software acceptance.

```powershell
$ErrorActionPreference = 'Stop'
$mapBatch = @('BleProfileReportActionsTest', 'MapAcceptanceTest')
$physicalClasses = @('BleRemoteStabilityAcceptanceTest')
$softwareClasses = @(Get-ChildItem app/src/androidTest/java/com/mamadrones/gcs -Filter '*Test.kt' |
    Where-Object { $_.BaseName -notin $physicalClasses } | Select-Object -ExpandProperty BaseName | Sort-Object)
$remainingClasses = @($softwareClasses | Where-Object { $_ -notin $mapBatch })
$softwareClasses # Review inventory and expected method counts before running either batch.
if (@($mapBatch | Where-Object { $_ -notin $softwareClasses }).Count -ne 0 -or $remainingClasses.Count -eq 0) {
    throw 'Test inventory changed; review the batches.'
}
$mapClassNames = ($mapBatch | ForEach-Object { 'com.mamadrones.gcs.' + $_ }) -join ','
$remainingClassNames = ($remainingClasses | ForEach-Object { 'com.mamadrones.gcs.' + $_ }) -join ','
./scripts/Test-AndroidAcceptance.ps1 -Serial $testEmulatorSerial -Adb $testAdb -TestClass $mapClassNames
$testWindows = & $testAdb -s $testEmulatorSerial shell dumpsys window
if ($LASTEXITCODE -ne 0 -or $testWindows -match 'Application Not Responding:|Application Error:') {
    throw 'Post-run Android health check failed; preserve the first result and do not start batch two.'
}
./scripts/Test-AndroidAcceptance.ps1 -Serial $testEmulatorSerial -Adb $testAdb -TestClass $remainingClassNames
$testWindows = & $testAdb -s $testEmulatorSerial shell dumpsys window
if ($LASTEXITCODE -ne 0 -or $testWindows -match 'Application Not Responding:|Application Error:') {
    throw 'Post-run Android health check failed; preserve JUnit output and record this separately.'
}
```

Expected per batch: actual JUnit `OK (N tests)`, zero failures/crashes, and counts reconciled with the reviewed inventory; distinguish excluded physical tests and any assumptions/skips. Do not count aborted attempts again. After each run inspect `adb -s <verified-serial> shell dumpsys window`, check for ANR/crash dialogs and visually inspect saved screenshots. Record a later system failure separately even when assertions passed. Do not close the user's other apps yourself or dismiss dialogs to force acceptance.

## 4. Manual user paths and hardware boundary

- Software-only path, on a disposable test installation: open disconnected, confirm missing telemetry is explicit and controls cannot operate the rover; create/edit/save a small local route, reopen it, preview/export through a local document provider, and import it back after confirmation. Verify expected order/coordinates, navigation, light/dark readability and no claim of a vehicle upload. Label the outcome manual and record what was actually tried.
- RC activity path: with controlled fixtures, confirm missing evidence blocks Start; fresh samples permit observation; stop/clear, stale data and session/source changes behave as specified in [Phase 43](phase-43-report.md). Fixture values are not motor calibration.
- Physical acceptance is a separate authorized task. Use the identified phone/remote and a hardware-team-secured setup with motor/ESC outputs isolated. Follow the [hardware record](hardware-integration.md), establish current validated MAVLink/selected heartbeat and fresh RC samples before the activity test. Record failures without guessing a baud or wiring fix.
- Do not reuse T12_485 physical-test opt-ins for another remote name without reviewing the test and confirming identity. Power-loss, process-death and movement tests are not implicit in a request for connection checks. No physical acceptance was performed in Phase 44.

## 5. Customer-release gate - not yet satisfied

Do not pre-check these boxes from historical fixture tests or an AI summary. Record evidence/owner/date or a blocking gap for each release candidate:

- [ ] Owner can explain the system, its key structures, alternatives, abuse cases and first failure points; current defense brief reviewed in the owner's own words and teach-back completed.
- [ ] Owner reviewed the actual full diff, including dependencies, network behavior, permissions, test changes and protected boundaries.
- [ ] Required fresh build/JVM/UI results and visual checks recorded for the candidate; environmental failures and skips resolved or explicitly scoped, not concealed.
- [ ] Realistic manual user path and any required physical acceptance completed for the intended use; no monitoring result presented as control authorization.
- [ ] Dependency/version/source rationale and credential handling reviewed; available secret checks run with exact scope/results. No comprehensive scanner or clean-history guarantee is supplied by Phase 44.
- [ ] Recovery candidate, data compatibility and [rollback checks](ROLLBACK.md) verified; signing, distribution and device/security qualification complete for intended deployment.
- [ ] Assumptions resolved or explicitly accepted by the responsible owner; safety-critical controls remain disabled while their prerequisites are unverified.
- [ ] Handover, decisions, relevant flow/data/threat/failure records and feature/bug evidence are current. Phase 44 is the baseline, not completion of all these deeper records.

Committing source is not a release approval. No CI enforcement or release-signing configuration is added by this checklist.
