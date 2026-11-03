# Phase 156 — use a compared tool setting

Date: 2026-11-03. Starting revision `38a0e58a96286df54c5092381ab526882bf8e398`, clean `main`. Model/version unavailable. User approved explicitly applying either compared option to the local coverage preview. See D-156-01.

## Behavior and boundaries

Each successfully calculated comparison card has **Use option A** or **Use option B**. A tap rechecks that its stored field, original width/overlap, direction and headland still match the active coverage state. It rejects stale, invalid, unavailable or busy choices and validates the bounded input strings before copying them. A successful tap copies only the option's exact width and overlap into volatile normal-preview inputs, clears the previous single preview, direction comparison and map, clears the comparison cards even when inputs are unchanged, and displays **Tool settings updated · generate a new preview**. The field, direction, headland and other inputs stay unchanged; no preview is automatically generated.

This affects no saved draft, persistence schema, external destination, dependency, device permission or vehicle command. Applying an option is a planning convenience, not a recommendation, physical safety check or guarantee of soil coverage. The existing [practice guide](mission-planning-walkthrough.md) and [security notes](security.md) describe the action and limits.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests 'com.mamadrones.gcs.presentation.mission.ApplyComparedCoverageSettingsTest' --tests 'com.mamadrones.gcs.presentation.mission.CoverageSettingsComparisonStateTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`; 97 tasks, 28 executed and 69 up-to-date. Five targeted unit tests passed, zero failures/skips. They cover both choices, exact width/overlap, preserved headland/direction, clearing prior comparison/map, same-value selection, invalid/stale fields/settings and canceled work. Lint: 0 errors, 27 warnings. No full JVM suite ran.
- Isolated `Pixel_3a_API_34_extension_level_7_x86_64` emulator, no snapshot, `swiftshader_indirect`, 2048 MB. The first health preflight detected an ANR; System UI was unresponsive when inspected. The next start presented a Bluetooth system-process error. Evidence: ignored `app/build/phase156/{preflight.txt,preflight-anr.txt,preflight-retry.txt,retry-error-window.txt,retry-crash.txt}`. Both attempts were stopped without dismissing errors. The user then reported closing unused apps; a later 30-second preflight passed (`memory-retry-preflight.txt`). A causal relationship to memory is not established.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#useComparedToolSettingsUpdatesPreviewInputsAndClearsOldPreview'`: `OK (1 test)` in 35.677 s, Android health gate true. It exercised B then A on a synthetic field, checked exact values, preserved headland/direction, cleared the old preview, and checked that no mission action was dispatched. Evidence: ignored `app/build/phase156/selection-test.txt` and `app/build/acceptance-20261103-155452-9073fcbe/`.
- Existing `FieldCoverageUiTest#compareToolSettingsShowsBothOptionsAndInvalidatesOnEdit`: `OK (1 test)` in 23.326 s, Android health gate true. Evidence: ignored `app/build/phase156/comparison-regression.txt` and `app/build/acceptance-20261103-155604-b6d7f750/`.
- Visually inspected ignored `app/build/phase156/choices.png` and `applied.png`: both buttons and the post-selection values/cleared-preview message are legible. Emulator stopped without wiping. No physical phone or hardware was used.

## Remaining

These are targeted synthetic checks, not a full regression, owner walkthrough or real field test. Earlier emulator/Compose errors, physical Cube heartbeat, actual coverage and release acceptance remain unresolved. Review the scoped diff and test evidence before committing.
