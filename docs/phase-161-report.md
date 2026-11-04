# Phase 161 — self-crossing field recovery

Date: 2026-11-04. User approved implementation and emulator testing. Baseline clean `4d4c790f70362aa219caf21411d9306972466ba9`; model/version unavailable. See D-161-01.

## Scope

Added `FieldCoverageUiTest#selfCrossingOutlineClearsPreviewAndRecoversWithoutChangingRoute`. It renders production `MissionScreen` with a synthetic in-memory draft containing two route waypoints and a four-vertex field. It generates a valid preview, replaces the in-memory outline with a self-crossing vertex order, verifies the old preview clears and the crossing explanation appears, restores the valid outline and generates a new preview. The test checks that waypoints are retained and no mission action is dispatched. The fixture changes draft state directly; it does not test the field-editor gesture path, persistence, or actual hardware. No production code was changed.

## Verification

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 88 tasks, 10 executed, 78 up-to-date. No fresh JVM suite or full regression ran.
- The initial disposable-emulator preflight detected a System UI ANR dialog; evidence retained under ignored `app/build/phase161/preflight.txt`, `preflight-window.txt` and `preflight-exit-info.txt`. The dialog was not dismissed. A no-wipe restarted emulator passed the 30-second preflight (`preflight-retry.txt`).
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#selfCrossingOutlineClearsPreviewAndRecoversWithoutChangingRoute'`: `OK (1 test)` in 21.871 seconds; Android health gate passed. The runner reported GPU option unavailable. Evidence: ignored `app/build/phase161/test.txt` and `app/build/acceptance-20261104-105608-ee7912ff/`.
- Reviewed ignored `app/build/phase161/phase161-crossing-error.png` and `phase161-corrected-preview.png`: the first displays the self-crossing explanation; the second shows the regenerated rectangular preview. The emulator was stopped without wiping.

## Remaining

The test does not cover the full field-editor interaction, other invalid shapes, every device, or physical coverage. The prior emulator ANR remains undiagnosed. No full regression or hardware acceptance was performed.
