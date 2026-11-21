# Phase 225 — Landscape import preview at 2× text

## Goal and baseline

The user approved a landscape check of the GeoJSON and GPX import previews on a disposable emulator at 2× Android text, with a long synthetic route name. Baseline `c97a8e0` on `main`; separate APK/demo outputs under `output/` were already untracked. Model/version unavailable. No operator phone, hardware link, mission upload or rover command was involved. See D-225-01.

## Reproduction and correction

- The genuine API 31 AVD used a reversible 720 × 1440 pixel, 360 dpi override. Its 640 × 320 dp app viewport passed a 30-second Android health check. An initial `OK (1 test)` from the old test was **invalid landscape evidence**: DocumentsUI left the preview in portrait, as the saved screenshots showed (`app/build/acceptance-20261121-160231-e5a47e1d/`, `app/build/phase225-landscape-2x-evidence/`).
- Added a landscape-specific variant of the existing real-picker test. It exports and selects synthetic GeoJSON/GPX files through DocumentsUI in portrait, rotates the **open preview** to landscape, requires a wide screenshot, checks the route name and scrollable outline/save notice, and clicks Replace before returning to portrait for Save. The original portrait test remains. Earlier attempts to run the entire Files panel in landscape stopped before the preview on the GPX notice visibility check and then on the export picker control (`app/build/acceptance-20261121-160641-a0d601ec/`, `app/build/acceptance-20261121-160913-d203f946/`). Those failures do not establish import-preview behavior; the Files panel's short-height usability remains outside this phase.
- The first actual landscape preview screenshot showed a title and two actions but **zero visible body height**. The route name remained invisible even after a scroll request. Evidence: `app/build/acceptance-20261121-161646-180d3075/`, `app/build/phase225-landscape-preview-failure.png`. Material's narrow default dialog width forced enough wrapping to consume the short window.
- `MissionScreen` now uses a wider `AlertDialog` only when Android reports landscape orientation. Its existing scrollable detail body and Cancel/Replace actions remain, as do the local import, cancel and explicit-save semantics. No persistence, schema, permission, dependency, network destination or vehicle-command path changed. The portrait dialog retains its previous width.

## Verification

- `.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain` succeeded (6 executed, 38 up-to-date). The landscape-specific Android test package was built with `.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` after each test change; final test package build succeeded.
- At 640 × 320 dp and 2× text, `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerInLandscape'` passed `OK (1 test)` with a healthy Android post-run gate (`app/build/acceptance-20261121-162038-55bc012e/`). The top and scrolled screenshots in `app/build/phase225-landscape-2x-fixed-evidence/` are actually landscape and show the name, outline, save notice and reachable actions across the scroll positions.
- At the same landscape viewport and normal text, a fresh 30-second Android health check and the same test passed `OK (1 test)` with a healthy post-run gate (`app/build/acceptance-20261121-162224-cdb84be5/`). The GeoJSON and GPX screenshots in `app/build/phase225-landscape-normal-evidence/` show complete details and actions without scrolling.
- The portrait 2× real-picker regression passed `OK (1 test)` with a healthy post-run gate (`app/build/acceptance-20261121-162411-c2626c0d/`). Final `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` succeeded: 533 JVM tests, zero failures/errors; lint zero errors and 27 warnings (12 executed, 33 up-to-date tasks).
- The AVD's 1080 × 2220, 440 dpi, 1× text and automatic rotation settings were restored, then it was closed. The test restores the previous emulator draft/recovery and removes only its generated documents. Screenshots and instrumentation logs remain ignored build evidence.

## Status and limits

The tested import preview is usable at 2× and normal text in the short landscape window. The full Files panel in that landscape size, other screen sizes, longer localized text and assistive technologies remain unverified. Synthetic local import does not establish physical mission accuracy or rover safety. Owner diff review remains a release gate. This report was prepared before the later commit/push request.
