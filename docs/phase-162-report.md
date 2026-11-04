# Phase 162 — invalid-field recovery through the editor (in progress)

Date: 2026-11-04. User approved Phase 162 and emulator testing. Clean starting revision `40c441414450a1ca91c3b089b3de95073cd28806`; model/version unavailable. See D-162-01.

## Scope

Added `FieldCoverageAppTest#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute` for a disposable emulator. The intended journey uses the real activity and field editor: generate a valid preview, remove and re-add a vertex in crossing order, check the old preview clears and the crossing error appears, rebuild the field through UI controls, and regenerate. It checks both route coordinates through the waypoint dialogs and that unsaved edits did not overwrite the saved draft. The test temporarily installs a synthetic local draft and restores the previous draft and recovery copy in `finally`; interruption can prevent restoration. No production code was changed.

## Verification and limitations

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 88 tasks, 10 executed and 78 up-to-date. No fresh JVM suite or full regression ran.
- The established no-wipe disposable emulator failed three preflights before the test: two System UI ANR dialogs and a new ANR event after the user closed unused Chrome windows. Evidence is retained in ignored `app/build/phase162/` (`preflight.txt`, `preflight-retry.txt`, `preflight-user-retry.txt`, and saved window/exit-info files). No dialogs were dismissed.
- A separate new disposable userdata image at ignored `app/build/phase162/fresh-userdata.img` was started without wiping the existing image. It did not finish booting within the wait; host memory was critically low. Boot/window evidence was preserved (`fresh-boot.txt`, `fresh-window.txt`) and the disposable emulator stopped. This does not establish the cause of earlier ANRs.
- The new UI test compiled but has not run. No assertions, screenshots, or restoration behavior have been verified in Phase 162. The previous emulator data was not altered by the test.

## Next

When a healthy disposable emulator is available, install the built APKs and run `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute'` after the documented health preflight. Preserve failures; review the synthetic screenshots and verify draft restoration. Do not claim physical coverage or hardware readiness from this test.


## 2026-11-04 follow-up — real-editor test passed

The first retry's 30-second health preflight found two new ANR events. Captured exit information identifies `com.google.android.gms.persistent` during SIM-state broadcasts; see ignored `app/build/phase162/preflight-next-retry.txt` and `next-retry-exit-info.txt`. A subsequent no-wipe restart passed the same preflight (`preflight-next-retry2.txt`). Installed the existing debug and test APKs via data-preserving updates.

`./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute'` returned `OK (1 test)` in 73.034 seconds with Android healthy; runner GPU option unavailable. Evidence: ignored `app/build/phase162/next-retry-test.txt` and `app/build/acceptance-20261104-112953-662a1be0/`. The test used actual editor actions, checked both route coordinates in the waypoint dialogs and verified the originally saved test draft remained unchanged. Its finally restoration completed without error; no separate post-restoration equality assertion was made.

Reviewed ignored `app/build/phase162/phase162-app-crossing.png` and `phase162-app-corrected.png`; they show the crossing explanation and regenerated modeled preview. The emulator was stopped without wiping. Earlier System UI and Google Play services ANRs remain undiagnosed. The single synthetic test is not a full regression, physical coverage test, or release acceptance.
