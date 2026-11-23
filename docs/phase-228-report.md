# Phase 228 — Portrait waypoint jump at 2× text

## Goal and scope

The user approved a focused check of numbered waypoint jumps on a disposable Android 31 emulator. Baseline `5429f398e24ec532664373bf58d951e5132aec99` on `main`. The route is a synthetic three-point local draft; the test saves and restores the emulator's previous draft and recovery copy. No operator phone, Cube, telemetry, mission upload or rover command was involved. See D-228-01. Model/version unavailable.

## Finding and change

The first 2× and normal-text tests passed, but visual inspection found that the 2× target card was only partly visible above the fixed safety banner (`app/build/acceptance-20261123-111153-c7ae78e1/`). A stronger full-card assertion exposed the same clipping in a screenshot from an Android-health-rejected run; that run also recorded a System UI startup crash and is not acceptance evidence (`acceptance-20261123-111507-276ff68e/`). The fixed large-text header had reduced the portrait editor viewport. At 2× text in a short portrait window, `MissionScreen` now puts only that header inside the scrollable editor. Normal-text and existing short-landscape layouts retain their earlier structure. The jump target still uses its stable waypoint item index.

The new full-app test enters invalid targets 0 and 4, checks the disabled Go action, then enters 2 and verifies the enabled action. It uses keyboard Done at 2× text and taps Go at normal text. It requires Waypoint 2 to be highlighted and wholly inside the editor bounds, captures a screenshot, and checks that the saved draft did not change. The test restores the previous local draft and recovery copy in `finally`.

## Verification

- `:app:assembleDebug :app:assembleDebugAndroidTest` succeeded after the app change; `:app:assembleDebugAndroidTest` succeeded again after the Files test correction.
- The 2× portrait jump passed `OK (1 test)` with a healthy Android gate (`app/build/acceptance-20261123-112101-583795ab/`). Visually reviewed `app/build/phase228-waypoint-portrait-2x-final.png`: the full Waypoint 2 card, highlight and actions are above the safety banner.
- The first 2× portrait Files run timed out after import (`acceptance-20261123-112152-e3fd6fdb/`); the test had not scrolled back to Save draft after the header moved. The corrected test scrolls to the header and keeps the save/persistence assertions. It passed with a healthy gate (`acceptance-20261123-112541-43d2a5df/`).
- The 2× outline jump and short-landscape real DocumentsUI Files regression passed with healthy gates (`acceptance-20261123-112657-da4e54b2/`, `acceptance-20261123-112750-049b8c0a/`).
- The normal-text portrait jump passed with a healthy gate (`acceptance-20261123-112842-351f2bbe/`). Visually reviewed `app/build/phase228-waypoint-portrait-normal-final.png` with the full Waypoint 2 card shown.
- `:app:testDebugUnitTest :app:lintDebug` succeeded. The JVM XML report has 533 tests, zero failures/errors/skips across 84 suites. Lint XML reports zero errors and 27 warnings. These are targeted emulator runs and code checks, not a full app regression.

## Status and limits

The tested English API 31 portrait viewport was 720 × 1440 pixels at 360 dpi. Other devices, screen sizes, languages, accessibility services and physical mission behavior remain unverified. The baseline is a recovery reference, not a qualified release. Owner diff review remains a release gate. The user requested a separate Phase 228 commit and push on 2026-11-23; Git history records the result. The earlier untracked APK/demo outputs under `output/` were left untouched. The emulator was used only with synthetic data.
