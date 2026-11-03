# Phase 153 — emulator stability investigation and preflight

Date: 2026-11-03. Starting revision: `f8c938d4d804f492ee65d48f93ab25367c938f32`, clean `main`. Model/version unavailable. User approved the investigation and emulator test. See D-153-01.

## Findings and change

Phase 148 recorded System UI ANRs and lost Compose hierarchy across emulator retries. Phase 152's first guarded run logged seven ANR events from Android/Google background services; its later retry had no new ANRs. The records do not demonstrate a common root cause, and a later pass does not resolve those failures. Prior boot attempts also encountered image-name and pending-shutdown issues. No change to Android services, emulator configuration, app behavior or the existing acceptance health gate is justified by these records alone.

`scripts/Wait-AndroidEmulatorHealthy.ps1` adds a read-only emulator-only preflight. It checks boot state and visible Android error windows, observes a configurable interval (30 seconds by default), then rechecks boot/windows and reads crash and ANR logs since the start. It fails on missing evidence, time moving backwards, ANRs, crashes or dialogs. It does not clear buffers, dismiss dialogs, restart the emulator or launch tests. The interval is a cautious precondition, not proof of subsequent stability or an ANR fix. Device-clock changes, log-buffer rollover and events outside the window limit detection; filtering whole seconds may conservatively include an event from just before the window. A hung ADB call is not independently bounded.

## Verification

- `./scripts/Test-EmulatorHealthPreflight.ps1`: nine synthetic fake-ADB cases passed: healthy, unbooted, dialog, invalid device clock, crash, ANR, unreadable crash buffer, unreadable ANR buffer and non-emulator serial rejection. Evidence: ignored `app/build/phase153/synthetic-checks.txt`.
- Used the existing isolated Phase 152 emulator image with `Pixel_3a_API_34_extension_level_7_x86_64`, no snapshot, software rendering and 2048 MB; no physical phone or hardware. `./scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local adb.exe> -QuietSeconds 30` passed: no new crash or ANR events and boot/window checks passed. Evidence: ignored `app/build/phase153/preflight.txt`.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.MissionPlanningTest#unsavedRouteAndOutlineRecoverAfterClosingAndReopeningEditor'` returned `OK (1 test)` in 57.273 s with Android health gate true. Evidence: ignored `app/build/phase153/mission-acceptance.txt` and `app/build/acceptance-20261103-135436-9a686077/`. The emulator was stopped without wiping. This focused test uses synthetic emulator drafts and attempts restoration; interruption can leave synthetic data.
- No fresh app build, JVM suite or full Android regression ran; production app code and dependencies were unchanged. Controlled fake checks are not a substitute for repeated emulator trials. Ignored evidence can be removed by a build clean.

## Remaining

The historical ANRs and Compose instability are not diagnosed. One healthy preflight and focused run do not establish repeatability. Hardware heartbeat, physical coverage, owner walkthrough and release acceptance remain open.
