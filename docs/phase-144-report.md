# Phase 144 — invalid route file through Android picker

Date: 2026-11-02. Starting revision: `39c575f`, clean `main` aligned with `origin/main`. Model: gpt-6.1-terra. The user approved this focused test; production behavior is unchanged. See D-144-01 in [DECISIONS](DECISIONS.md).

## Test and data boundaries

`RouteDocumentPickerTest#invalidFileThroughPickerPreservesSavedAndWorkingDraft` creates a uniquely named invalid GeoJSON in emulator Downloads through MediaStore, saves a synthetic route, makes a different unsaved edit, and selects the file through Android DocumentsUI. It asserts the visible unsupported-file error, no import preview, the original committed route and the edited working latitude. It deletes the created MediaStore URI and attempts to restore the previous emulator draft/recovery in `finally`. Interruption or cleanup failure could leave synthetic emulator data behind. No phone or vehicle was used.

## Verification

- Final `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`, 69 tasks (9 executed, 60 up-to-date); lint report has 27 warnings. No fresh JVM suite or full Android regression was run.
- On the no-wipe `Pixel_3a_API_34_extension_level_7_x86_64` emulator, `adb -s emulator-5554 shell am instrument -w -e class com.mamadrones.gcs.RouteDocumentPickerTest#invalidFileThroughPickerPreservesSavedAndWorkingDraft com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner` returned `OK (1 test)` in 36.91 s. Final output is in ignored `app/build/phase144/picker-final.log`.
- Two initial attempts failed before reaching the picker because shell redirection did not create the fixture. A MediaStore-based attempt reached the expected error and preserved edit but failed the assertion because the editor displays extra decimal places. The final run used substring matching and passed the same full flow. Earlier output remains in ignored `app/build/phase144/`.
- A bounded window check found no matching Android error/ANR dialog after the final run. The emulator was stopped without wiping it.

## Limits and next step

This is one invalid content case on one emulator, not the full app regression or all provider failure modes. Physical hardware and field acceptance remain open. Review the test diff and run broader regression when appropriate. Commit/push require a separate request.
