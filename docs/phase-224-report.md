# Phase 224 — Small-screen import preview at 2× text

## Goal and scope

The user approved a real-picker GeoJSON/GPX preview check on a smaller disposable emulator, with a long synthetic route name and 2× Android text. Baseline `6c54704` on `main`; the earlier APK/demo outputs under `output/` were already untracked. Model/version unavailable. The test opened no hardware link or operator phone and issued no vehicle command. See D-224-01.

## Reproduction and fix

- Reused the genuine API 31 AVD with a reversible `wm size 720x1440` and `wm density 360` override (320 × 640 dp) and font scale `2.0`. A 30-second boot/ANR preflight passed. The existing picker test initially passed `OK (1 test)` with a healthy post-run gate, but visual review showed both previews cutting off the outline and saved-route notice behind the dialog actions. This is why the original semantic assertion did not establish visible content. Evidence: `app/build/acceptance-20261121-153354-f324a1fa/` and `app/build/phase224-small-two-x-evidence/`.
- The input is the existing bounded local import projection (`plan.pendingImport`), read only by `MissionScreen`. It shows the name, waypoint/distance estimate, local outline presence and explicit-save notice; the dialog actions update only the working draft or cancel. The text is now separate semantic nodes in a vertically scrollable dialog body. The actions and import/save state transitions are unchanged. No new data, persistence, permission, dependency, network destination or command path was added.
- Extended the real DocumentsUI test to assert the preview name, scroll to the outline and save notice, assert both actions remain visible, and capture top and scrolled screenshots for both formats. The first run of this revised test failed because the route name matched both the editor behind the dialog and the preview. Restricted that selector to the preview scroll container; no production change was needed for that test failure. Failure evidence: `app/build/acceptance-20261121-154613-d271a65b/`.

## Verification

- `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded. The 533 JVM test cases had zero failures/errors. Lint reported zero errors and 27 warnings. Gradle reported 25 executed and 72 up-to-date tasks. The corrected selector was then rebuilt with `.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` (6 executed, 54 up-to-date).
- Small 320 × 640 dp, 2× text: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation'` passed `OK (1 test)` with a healthy post-run gate. Evidence: `app/build/acceptance-20261121-154928-699ffd01/`. Visually reviewed the top and scrolled GeoJSON/GPX screenshots in `app/build/phase224-small-two-x-fixed-evidence/`; the full outline and save notice are reachable by scrolling while Cancel and Replace stay visible.
- Small 320 × 640 dp, normal text: a fresh 30-second health preflight and the same focused picker test passed `OK (1 test)` with a healthy post-run gate. Evidence: `app/build/acceptance-20261121-155134-afb11dc0/`. The previews in `app/build/phase224-small-normal-evidence/` show complete details and actions without scrolling.
- Restored the AVD to 1080 × 2220, 440 dpi and 1× text, then closed it. Test documents and local draft/recovery were restored by the test. The screenshots and raw instrumentation evidence are ignored build outputs, not source-controlled artifacts.

## Status and limits

The tested small-screen import preview is usable at 2× and normal text. Other viewport sizes, very long names, translations and assistive technologies remain unverified. A successful local import does not establish field accuracy, mission upload or rover safety. Owner diff review remains a release gate. This report was prepared before the later commit/push request.
