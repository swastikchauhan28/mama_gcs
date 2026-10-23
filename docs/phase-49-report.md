# Phase 49 — Classic diagnosis visual acceptance

## Goal and scope

The user approved Phase 49 on 22 October 2026 to visually accept the Phase 48 Classic telemetry diagnosis card. This phase is software acceptance only: use a disposable emulator, inspect light/dark layouts, and check Android health before and after. No physical phone, Cube, R12, T12, parameter, wiring or command path is used.

## Verification attempt — 22 October 2026

- Starting revision: `07d181ca2b06fee22033668633e586124b726d1a`; worktree was clean.
- Existing debug APKs were present: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- ADB at `C:\Users\swastik\AppData\Local\Android\Sdk\platform-tools\adb.exe` could not start its server (`could not read ok from ADB Server` / `failed to start daemon`). No emulator or device was listed. Installation, instrumentation, screenshot capture and post-run health checks therefore did not run.
- This is an environment block, not a UI pass or app failure. The physical phone remains excluded from synthetic acceptance.

## Result and next safe step

Status: **blocked — disposable Android test environment unavailable**.

Retry on a healthy disposable emulator after the ADB server starts. Install both existing APKs, run `ClassicConnectionCheckUiTest`, inspect the Phase 48 diagnosis card in light/dark themes, and run the documented pre/post Android health checks. Do not dismiss ANR dialogs or use a populated phone as a fixture.

Rollback: none; this phase made no runtime or hardware changes.

## Verification retry — 23 October 2026

- Starting revision `61ea57331e2199c47ebe7b42df6c6f7791858ad8`; worktree clean. ADB started outside the filesystem sandbox and listed no connected physical device. Started the existing `Pixel_3a_API_34_extension_level_7_x86_64` emulator headless with `-read-only -no-snapshot-load -no-snapshot-save`; serial `emulator-5554` booted (`sys.boot_completed=1`). Pre- and post-test `adb -s emulator-5554 shell dumpsys window` showed the launcher and no `Application Not Responding:` or `Application Error:` dialog.
- Installed existing debug and Android-test APKs on that read-only emulator. First `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <installed adb.exe> -TestClass com.mamadrones.gcs.ClassicConnectionCheckUiTest` run: **2 passed, 1 failed**. The `stagesAndGuidanceRemainReadableInBothThemes` failure was an assertion on the card container's text semantics; the expected diagnostic text is rendered in its child `Text` node. Evidence: `app/build/acceptance-20261023-102526-af6e5ae4/instrumentation.txt`.
- Changed only the synthetic Android UI test: assert that the child diagnostic text belongs to the tagged card, scroll it into view, and capture the Phase 48 diagnosis card in light/dark. No production UI copy, decoder, transport or safety gate changed. `./gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` succeeded after each test edit (final run: 60 tasks, 6 executed/54 up-to-date). Reinstalled the rebuilt test APK.
- Intermediate rerun: **OK (3 tests)** at `app/build/acceptance-20261023-102920-b5786f11/instrumentation.txt`. Its four older captures showed the waiting/live states but not the diagnosis card, so the visual acceptance was not yet complete. Final focused rerun: **OK (3 tests)** at `app/build/acceptance-20261023-103151-b52bf6af/instrumentation.txt`.
- Visually inspected `phase49-diagnosis-light.png` and `phase49-diagnosis-dark.png` in that final ignored build-evidence directory. Both show the complete diagnosis title and limitation sentence in readable contrast; the card does not claim a physical root cause. The test confirms the text is displayed in both themes. This was not a full Android suite, fresh JVM/lint run, manual installed-app navigation, or physical T12/R12/Cube acceptance.

Current Phase 49 status: **focused synthetic UI and visual acceptance passed** on the read-only emulator. The earlier ADB block and first assertion failure remain recorded above; neither is recast as a passing run. No hardware link was opened, and no app data was cleared. The next independent gate is the secured, read-only Cube-origin heartbeat trace described in `hardware-integration.md`.
