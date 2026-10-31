# Phase 131 — host resources and emulator startup

Date: 2026-10-31
Starting revision: `31eb34d51f21269388c11f6748995571383d33a7`, clean and synchronized with `origin/main`. Model/version unavailable.

## Goal and comparison

Inspect host resource and acceleration information, then compare one later no-wipe boot of the task-owned Phase 130 emulator with its first boot. This checks whether initial setup alone explains the observations; it does not independently control host load, elapsed time, or other variables. Run the map-recovery and document-picker groups only after a healthy startup. No production code, dependencies, phone data or hardware were changed.

## Evidence and result

- Before starting, Windows reported 16,474,168 KiB visible physical memory, 5,241,136 KiB free physical memory, and 24,328,478,720 bytes free on C:. The CPU reported 12 cores and 16 logical processors. `emulator.exe -accel-check` reported WHPX installed and usable. The Windows processor property `VirtualizationFirmwareEnabled` reported false; these differing indicators are not resolved here and no firmware change was attempted.
- Started task-owned `MamaPhase130` without wiping data using the same API 34 image and launch settings as Phase 130: `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-window -memory 2048 -cores 4`. After boot completion and a 40-second observation, Android recorded a System UI service ANR and Bluetooth process SIGABRTs. The captured abort assertion again identified a timeout starting `HciHalHidl`, as in earlier investigations. An Android Bluetooth error window was present. Host free physical memory at the observation point was 2,009,676 KiB. These readings are snapshots, not continuous profiling or proof of causality.
- Since the Android health precondition failed, the map-recovery and document-picker tests were not run. No app/test APK was installed, and no fresh build, lint or unit checks were run. The task emulator was stopped without wiping data. No crash/ANR check was disabled and no error dialog was dismissed.
- Local evidence: `app/build/phase131/window.txt`, `anr.txt`, `crash.txt`, `host-memory.txt`, `emulator-out.txt`, and `emulator-err.txt`. Ignored build artifacts can be removed by a future clean.

## Interpretation and next step

The later boot reproduced a System UI ANR and the previously seen Bluetooth HCI startup assertion; first-boot setup alone is therefore insufficient to explain all observed failures. The lower available-memory snapshot during the emulator session is not evidence that memory caused them, and a repeat with different settings would need controlled comparisons. This did not test or explain the previous MapLibre native crash. Investigate the Android/emulator startup failure with targeted profiling or run the blocked tests on a healthy authorized device. Hardware, field accuracy and release acceptance remain unverified.
