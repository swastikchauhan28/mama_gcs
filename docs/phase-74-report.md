# Phase 74 — Verify coordinate entry in the normal app window

Date: 2026-10-27

Baseline: `9bb331e73e321f7d53c9cd3c017d704f33d7389b` plus uncommitted Phases 71–73. This phase adds an Android acceptance test and records only; it makes no production app change. Hardware work remains on hold.

## Scope and path

The test launches the real `MainActivity` on the disposable `Pixel_3a_API_34_extension_level_7_x86_64` emulator, uses the visible menu to open Mission, scrolls to Add coordinates, and opens the shared waypoint dialog. It enters invalid `91` latitude and `181` longitude, checks both range explanations and disabled Add while the IME is open, then corrects them to `-35.36` and `149.16`, presses the longitude Done action, checks the IME is hidden and Add is enabled, and cancels. Read-only snapshots from `LocalMissionDraftRepository.load()` and `loadRecovery()` taken after navigation match after cancellation. No waypoint was added or saved; screenshot files are test artifacts. This exercises the real window/navigation/IME path without connecting a telemetry link or sending a vehicle command.

## Verification

- `adb devices -l` showed only `emulator-5554`; `adb -s emulator-5554 emu avd name` identified the documented disposable Pixel AVD. Boot was complete before testing. No emulator data was cleared.
- `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL in 1m 39s**; 60 tasks, 7 executed and 53 up-to-date. The production app compile tasks were up-to-date. The test APK installed with `adb -s emulator-5554 install -r "$env:TEMP\mama-gcs-phase58-app-build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"` — Success.
- `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.MissionCoordinateEntryUiTest'`, with `$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'` — **PASS, `OK (1 test)`**. The health file records zero new crash lines, zero new ANR events, boot complete and no ANR/error dialog. Evidence: `app/build/acceptance-20261027-111414-5884c80b/instrumentation.txt` and `android-health.txt`.
- Full-display captures `app/build/phase74-main-invalid-ime.png` and `app/build/phase74-main-corrected-after-done.png` are 1080×2220 and were visually inspected. With the keyboard open, both errors and the disabled Add action are readable. After Done, the corrected values and enabled Add action are visible. The app showed a local, not uploaded, zero-point route behind the dialog; the test then cancelled.

## Review, limits and recovery

Review `MissionCoordinateEntryUiTest.kt`, D-074-01 and the two full-display captures. This one case verifies the actual MainActivity portrait path and unchanged saved draft/recovery contents after Cancel. It does not cover saving a waypoint, other IMEs, locales, physical phones, the full Android suite, or rover hardware. The acceptance runner could not identify the GPU launch option. The Phase 73 JVM/lint and eight-test Android results are historical, not rerun here. No permission, dependency, schema, destination or production behavior changed. The baseline is a source recovery point rather than a qualified release; the test and records can be reverted without data migration. Owner diff review has not been claimed. Phases 71–74 remain uncommitted and unpushed; preserve unrelated `desktop.ini`.
