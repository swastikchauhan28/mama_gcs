# Phase 227 — Route jumps with Files open in short landscape

## Goal and scope

The user approved a disposable-emulator check of waypoint and field-outline vertex jumps with Files open at 2× text in short landscape. Baseline `00ed2d8` on `main`. The existing APK/demo outputs under `output/` were already untracked. No phone, telemetry, Cube, radio, mission upload, or rover command was involved. See D-227-01. Model/version unavailable.

## Change and observed failures

- The first healthy test found that the Route jump field was trapped in an oversized introduction item (`app/build/acceptance-20261121-174231-fd2d76e7/`). The short-landscape jump heading, input, and action now have separate scrollable rows. The same change applies to outline vertex jumps. Jump target indices include those rows.
- After the waypoint jump became usable, the Field outline tab fell beneath the fixed safety banner and a tap did not select it (`app/build/acceptance-20261121-175435-d97cf6d5/`). The short-landscape tab strip now has its own row; jump and Files test indices include it. Ordinary layouts keep their prior arrangement.
- The first revised test failed because it demanded that the Files button be visible while scrolled to the tab row (`app/build/acceptance-20261121-180005-5dd9ede1/`). It now scrolls to the button and keeps the visibility assertion. An automated review rejected weakening that assertion to mere existence.
- The older ordinary-layout outline test timed out before finding its target when run at the emulator's 2× portrait setting (`app/build/acceptance-20261121-180358-663b3b6f/`). It passed at normal text (`app/build/acceptance-20261121-180509-464bf083/`). The 2× portrait timeout remains an open coverage limit for that older flow.

## Verification

- `:app:assembleDebug :app:assembleDebugAndroidTest` succeeded. The synthetic waypoint/outline test passed at 2× text in a 640 × 320 dp landscape viewport with Files open (`app/build/acceptance-20261121-180151-1c1544cf/`). It verified Waypoint 3 and Outline vertex 4 were visible and highlighted and that the saved draft was unchanged.
- The fresh `:app:testDebugUnitTest` report contains 533 JVM tests, zero failures, and zero skips. The first lint output stream disconnected before reporting completion; a fresh `:app:lintDebug` completed successfully with zero errors and 27 warnings.
- The existing short-landscape real DocumentsUI Files flow passed at 2× text (`app/build/acceptance-20261121-180223-ba4cf814/`). The older ordinary-layout outline regression passed at normal text (`app/build/acceptance-20261121-180509-464bf083/`). All three passing runs had healthy Android gates. These are targeted runs, not a full app regression.
- Visually reviewed `app/build/phase227-waypoint-jump.png` and `app/build/phase227-outline-jump.png`: both targets are readable above the fixed safety banner.

## Status and limits

The tested jump and Files paths work in this English, API 31 emulator viewport. Other window sizes, languages, assistive technologies, live mission upload, and physical rover behavior remain unverified. The test restores the disposable emulator's prior draft and recovery. Owner diff review remains a release gate. No Phase 227 commit or push was authorized.

The emulator stopped before the first display cleanup. After a full reboot, its temporary 720 × 1440 pixel and 360 dpi overrides were verified, cleared to 1080 × 2220 and 440 dpi, text was confirmed at 1.0, rotation was released, and the emulator was stopped. This cleanup was separate from acceptance testing.

## 2026-11-23 follow-up: 2× portrait outline test

The user asked to finish the remaining 2× portrait coverage gap. At baseline `00ed2d8`, the Phase 227 implementation remained uncommitted; earlier `output/` APK/demo files were still separate untracked work. No app behavior, network path, persistence, or hardware setup changed in this follow-up.

- Replaced the older test's wait for an uncomposed lazy-list `mission-add` child with a scroll to that control and visible/enabled assertions. The next run reached it, but the Review issue action was absent (`app/build/acceptance-20261123-103337-c1b98ad7/`). A selected-state assertion showed the Review chip had been tapped while horizontally outside the portrait viewport (`acceptance-20261123-103612-145b4f5c/`). The test now scrolls the chip into view and requires it to be visible and selected.
- The subsequent test was manually stopped after several minutes without a result (`acceptance-20261123-103813-1829be8a/`). Its post-stop screenshot (`app/build/phase227-portrait-stall.png`) showed the numeric keyboard covering most of the editor. The test retains disabled/enabled Go-button checks and uses the existing keyboard Done action to perform the vertex jump. The stopped attempt is not acceptance evidence.
- The complete `MissionPlanningTest#outlineWarningAndVertexJumpLeaveSavedDraftUntouched` flow then passed `OK (1 test)` at 2× portrait text, with a healthy Android gate (`app/build/acceptance-20261123-104510-8470eff8/`). It also passed its landscape portion after rotation. `app/build/phase227-followup-outline-portrait.png` and `phase227-followup-outline-landscape.png` were visually reviewed: highlighted vertices 4 and 2 are readable above the safety banner. Saved-draft equality passed.
- The test APK build succeeded. This is a focused emulator check; it does not establish universal accessibility or physical mission behavior. Commit and push remain separate actions.

Final review preserved the original Go-button click at ordinary text while using Done only at enlarged text. With the final test APK, the complete outline flow passed at 2× text (`app/build/acceptance-20261123-105448-c0d5a7d4/`) and at normal text (`acceptance-20261123-105523-c16577bd/`); both Android health gates passed. The short 2× landscape jump and real Files picker tests also passed again (`acceptance-20261123-105559-ef7e4348/`, `acceptance-20261123-105654-5b3007f6/`). `:app:assembleDebugAndroidTest :app:lintDebug` succeeded; JVM tests were up to date with the previous 533-test, zero-failure result. These are four focused emulator runs, not a full app regression.

After testing, the disposable emulator display was reset and verified at its physical 1080 × 2220 pixels, 440 dpi and 1.0 text scale, then stopped. The final scoped diff had no whitespace errors or added-line secret-keyword hits; lint reported zero errors and 27 warnings. The existing untracked APK/demo outputs under `output/` were left untouched. Owner diff review and physical acceptance remain separate.

On 2026-11-23 the user separately requested a Phase 227 commit and push. Check Git history for the resulting revision and remote status; this request does not change the release or physical acceptance gates.
