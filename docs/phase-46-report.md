# Phase 46 — Classic checklist visual acceptance attempt

## Goal and scope

Close Phase 45's unexecuted UI/visual check on a disposable Android emulator. Acceptance requires the synthetic `ClassicConnectionCheckUiTest` to pass, light/dark captures to be inspected, and no Android ANR/crash before or after the run. This phase does not operate hardware, change application code, clear operator data or authorize release.

## Baseline and test choice

- Date: 2026-10-21. Starting revision: clean `main` at `34c49d0f0436c8c863b554ed08302974581ea58d`.
- Existing `MamaPwmRegression` and `MamaRegression` AVDs under ignored build artifacts had prior test history. A populated physical phone is outside the disposable software-test scope. A new `MamaPhase46` AVD in ignored `app/build/phase46-avd-home/` was selected to avoid reusing either state.
- The installed API 34 Google APIs x86_64 system image was reused. The legacy SDK `avdmanager.bat` failed before creation because its `javax.xml.bind.annotation.XmlSchema` dependency is absent from the installed Java runtime. A new AVD configuration based on the repository's earlier disposable QA AVD was written under ignored build output; no existing AVD files were changed.
- The synthetic UI test changes only its disposable test activity. Its initial version wrote two PNG captures; the strengthened version writes four top/bottom captures under that emulator's app-specific external files. It opens no Bluetooth link and sends no rover commands.

## Actual result

The new AVD booted (`sys.boot_completed=1`) and had no Android error dialog at the pre-install check. Both already-built Phase 45 APKs installed successfully on verified `MamaPhase46` (`emulator-5554`). The focused command was:

`./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <configured-sdk>/platform-tools/adb.exe -TestClass com.mamadrones.gcs.ClassicConnectionCheckUiTest`

The first runner attempt refused to begin instrumentation at its preflight because Android displayed `Application Not Responding: com.android.systemui`. ADB briefly went offline and later recovered, but the System UI ANR dialog remained. Host free RAM was approximately 650 MB near boot completion; memory pressure is a possible contributor, not a proven sole cause. No dialog was dismissed. The verified disposable emulator was stopped with `adb -s emulator-5554 emu kill`.

With about 5.5 GB host RAM free afterward, a clean retry used the same disposable AVD with a 1536 MB/two-core runtime setting. It booted without an error dialog and the unmodified Phase 45 UI fixture passed **3 of 3 tests**. Evidence: ignored `app/build/acceptance-20261021-105708-b22ce975/instrumentation.txt` (`OK (3 tests)`). The post-run window check found no ANR/crash dialog. Synthetic light/dark top-of-panel screenshots were copied to that evidence directory as `phase46-check-light.png` and `phase46-check-dark.png` and visually inspected. The first six checklist rows, including the new RC status, were legible in both themes; the bottom safety guidance was outside those captured viewports.

To check the bottom rather than infer it, `ClassicConnectionCheckUiTest` was strengthened to scroll to the final safety line, assert it is displayed and capture top/bottom views in both themes. Production app code and assertions for RC status were not weakened. The exact fresh command was:

`./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`

It returned `BUILD SUCCESSFUL` in 2m 4s, with 9 tasks executed and 60 up-to-date. The Android test APK recompiled. The subsequent cold boot and updated test-APK install initially passed health checks, but the runner again refused to start instrumentation because `com.android.systemui` showed an ANR. At that point host free RAM was approximately 2.4 GB, so low free RAM alone is not an established explanation. The new bottom-scroll assertions **compiled but did not run**. The disposable emulator was stopped without dismissing the dialog or clearing data. No other device was used.

### Final clean-boot retry

The already-installed, strengthened test APK was retained. A final cold boot used the same verified disposable AVD and 1536 MB/two-core runtime, with **no reinstall or data clearing**. Android completed boot without an error dialog. The same focused acceptance command above passed **3 of 3 tests** in 26.983 seconds, including the new bottom-scroll assertions. Evidence: ignored `app/build/acceptance-20261021-110434-839893ce/instrumentation.txt` (`OK (3 tests)`). The post-run window check found no ANR/crash dialog.

Four synthetic screenshots were copied to that ignored evidence directory: `phase46-check-light-top.png`, `phase46-check-light-bottom.png`, `phase46-check-dark-top.png` and `phase46-check-dark-bottom.png`. Visual inspection found the numbered RC status, guidance and final safety text legible in both themes; the bottom text was reachable by scrolling. The dark top capture begins at the status line because the fixture retained scroll position when switching themes; the earlier dark capture showed the title. These captures are synthetic fixture views, not physical telemetry evidence. The verified AVD was stopped after review and its ignored files remain available locally.

## Next and limits

Targeted Phase 45 Classic checklist UI/visual acceptance is now complete on a disposable emulator. It is **not** a complete current Android UI suite, an endurance test or a physical-link acceptance. The two earlier System UI ANR preflight failures remain recorded and warrant separate host/emulator investigation before a broader release run; they were not dismissed or counted as app test results. Phase 45's 381-JVM-test/build/lint result is historical to this phase. Physical Cube/R12/T12 acceptance remains a separate, secured read-only bench task. Only the synthetic Android test source changed; no production code, dependency, safety boundary or hardware configuration changed in Phase 46.
