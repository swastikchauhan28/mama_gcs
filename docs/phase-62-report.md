# Phase 62 — complete software Android regression

Date: 2026-10-26. Source baseline `6d0665d37e37cf157d5719d33b4781e2d55fcd01`, with the uncommitted Phase 60 screenshot diagnostics and Phase 61 enlarged-text UI change in the working tree. The unrelated untracked `desktop.ini` was preserved. No Phase 62 app or test code change was needed.

## Goal and boundary

Run the current Android software test inventory against the Phase 60/61 debug APKs, including the previously intermittent landscape screenshot test. The inventory contained 35 software classes and one excluded `BleRemoteStabilityAcceptanceTest` physical class. The two disjoint batches follow [the project testing guide](TESTING.md) and [D-062-01](DECISIONS.md#d-062-01-run-the-current-software-android-inventory-before-another-ui-change). All execution used the disposable `Pixel_3a_API_34_extension_level_7_x86_64` emulator with `swiftshader_indirect`; no physical phone, Cube, radio or rover was contacted. The test runner observes Android health as well as JUnit and does not clear logs.

## Fresh results

- Inventory and batch commands (the pre-run window check also rejected any ANR/error dialog):

  ```powershell
  $physical = @('BleRemoteStabilityAcceptanceTest')
  $map = @('BleProfileReportActionsTest', 'MapAcceptanceTest')
  $software = @(Get-ChildItem app/src/androidTest/java/com/mamadrones/gcs -Filter '*Test.kt' |
      Where-Object { $_.BaseName -notin $physical } | Select-Object -ExpandProperty BaseName | Sort-Object)
  $remaining = @($software | Where-Object { $_ -notin $map })
  if ($software.Count -ne 35 -or $remaining.Count -ne 33) { throw 'Inventory mismatch' }
  $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
  $mapSelection = ($map | ForEach-Object { 'com.mamadrones.gcs.' + $_ }) -join ','
  $remainingSelection = ($remaining | ForEach-Object { 'com.mamadrones.gcs.' + $_ }) -join ','
  ./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass $mapSelection
  ./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adb -TestClass $remainingSelection
  ```

- Map batch: `OK (7 tests)` in 56.194 s. ADB instrumentation exit 0; Android boot `1`, zero new crash lines and ANR events, no error window. Evidence: `app/build/acceptance-20261026-122017-241e4a4a/` (`instrumentation.txt`, `android-health.txt` and sibling event logs).
- Remaining 33 classes: `OK (127 tests)` in 704.32 s. ADB instrumentation exit 0; Android boot `1`, zero new crash lines and ANR events, no error window. Evidence: `app/build/acceptance-20261026-122156-eca1181f/`. The run includes `RoverWorkspaceUiTest#landscapeDaylightWorkspaceAndCategories`, which passed without a screenshot timeout.
- Total across disjoint batches: **134 Android software methods passed**. This is a fresh run of the installed Phase 60/61 debug and test APKs, not a new JVM or lint run. The last APK build, 389 JVM tests and lint result are documented in [Phase 61](phase-61-report.md).
- A separate post-suite check again found Android boot `1` and no ANR/error window. Visually inspected fresh emulator screenshots saved as ignored local evidence: `app/build/phase62-landscape.png` shows the wide toolbar, mode card, instruments and safety footer without overlap; `app/build/phase62-settings-portrait.png` shows a readable Application settings title, tabs, Appearance card and footer.

## Limits, review and next step

The suite ran in two batches, not one uninterrupted 134-method instrumentation session. The screenshots show a blank/neutral map surface; they do not prove live map tile availability. Software fixtures and emulator checks do not verify the Cube/R12/T12 link, RC reception, actuator behavior or physical safety. The earlier Phase 59 screenshot timeout and Phase 60 first-run boot-service ANRs remain historical evidence; a passing run does not erase them.

Review the class exclusion and both JUnit/health records, plus the still-uncommitted Phase 60/61 code diff and screenshots. No secrets, credentials, dependencies, permissions, schemas, external destinations, commands, Git history or production data were changed by Phase 62. No rollback is needed for this documentation-only validation; source rollback for Phase 60/61 remains in their reports. The combined work is uncommitted and unpushed pending a separate user request.
