# Phase 158 — enlarged-text tool comparison

Date: 2026-11-03. Starting revision `7cb8525a38fee0e242867b10ebaa123b95a55317`, clean `main`. Model/version unavailable. The user requested the next phase after the enlarged-text proposal. See D-158-01.

## Scope

Added `FieldCoverageUiTest#enlargedTextToolComparisonShowsErrorsAndAppliesOption`. On the existing synthetic 320 dp / 2× Compose-font fixture it enters two options, checks an 81% overlap produces an explanation and disables Compare, corrects the value, reaches both use buttons, applies option B, verifies its exact settings and cleared preview, and checks no mission action or draft change occurred. The test uses in-memory synthetic geometry; no production app, saved phone data, hardware or vehicle command changed.

Because dialogs can use a separate Android window, a second run also temporarily set the isolated emulator's **system** `font_scale` to `2.0`; the original `1.0` value was restored in `finally` and independently re-read as `1.0`. This checks the actual dialog window under the enlarged system setting as well as the fixture's in-app font. It does not cover every viewport, orientation, keyboard or accessibility service.

## Verification

- `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`; 69 tasks, 10 executed and 59 up-to-date. Lint report: 0 errors, 27 warnings. No fresh JVM suite or full app regression ran.
- Isolated `Pixel_3a_API_34_extension_level_7_x86_64` emulator, no snapshot, `swiftshader_indirect`, 2048 MB. First 30-second preflight detected one Google Play services ANR; a later quiet interval passed. Evidence: ignored `app/build/phase158/{preflight.txt,preflight-anr.txt,preflight-settled.txt}`.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#enlargedTextToolComparisonShowsErrorsAndAppliesOption'`: `OK (1 test)` in 18.111 s, Android health gate true. Evidence: ignored `app/build/phase158/large-tool-test.txt` and `app/build/acceptance-20261103-170621-7df5b400/`.
- On a later restart, preflight found a Bluetooth system-process error dialog; the emulator was stopped without dismissing it. Evidence: `system-font-preflight.txt`, `system-font-error-window.txt`, `system-font-crash.txt` in ignored `app/build/phase158/`. A subsequent restarted emulator passed preflight (`system-font-preflight-retry.txt`).
- The same acceptance command passed again with Android system font scale `2.0`: `OK (1 test)` in 19.315 s, Android health gate true. Evidence: ignored `app/build/phase158/system-font-test.txt` and `app/build/acceptance-20261103-171119-2ce6c943/`. The prior font scale was restored to `1.0` and verified (`system-font-restore.txt`); the emulator was stopped without wiping.
- Visually reviewed ignored `app/build/phase158/system-font-{invalid,options,applied}.png`: the validation message and disabled Compare button were readable, both use buttons were reachable by scrolling, and the selected settings were asserted by the test. The earlier fixture-only captures are `invalid.png`, `options.png` and `applied.png`.

## Remaining

This is one targeted synthetic screen test on one narrow viewport and font setting, not universal accessibility certification, a full regression or physical coverage verification. The startup ANR and Bluetooth-system error are not diagnosed. Owner walkthrough, hardware heartbeat, real field coverage and release acceptance remain open.


## 2026-11-03 follow-up — both options and regenerated preview

Extended the same synthetic test to apply B (1.25 m width, 25% overlap), regenerate the preview and assert a displayed maximum spacing of 0.94 m, then apply A (2 m, 10% overlap) and assert the earlier preview clears. Existing checks retain validation, reachability and unchanged draft/mission action assertions. This is an automated synthetic scenario; it does not measure physical coverage.

- `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 69 tasks, 9 executed and 60 up-to-date; lint has 27 warnings. No fresh full regression or JVM suite ran.
- An initial isolated-emulator preflight found a System UI ANR; evidence is in ignored `app/build/phase158/complete-{preflight,error-window,crash,anr}.txt`. A clean restart passed a 30-second preflight (`complete-retry-preflight.txt`). The cause of the ANR remains unknown.
- The acceptance command above then passed at Android system font scale 2.0: `OK (1 test)` in 25.124 seconds, Android health gate true. Evidence: ignored `app/build/phase158/complete-test.txt` and `app/build/acceptance-20261103-172207-bb78c986/`. The previous system scale 1.0 was restored and independently verified (`complete-font-restore.txt`).
- Reviewed `complete-regenerated.png` and `complete-applied-a.png` in ignored `app/build/phase158/`: the former shows 0.94 m spacing and the latter shows the cleared-preview instruction. At 2× text, scrolled content is partly obscured behind the fixed header in these captures; this visual limitation warrants separate investigation. The test verifies settings through semantics rather than these two screenshots.
