# Phase 129 — emulator stability investigation

Date: 2026-10-31
Starting revision: `7e6e21d2c4664354980a214f25f68225482685e7`, clean and synchronized with `origin/main`. Model/version unavailable.

## Goal and evidence

Investigate the recurring Android startup failures and the prior MapLibre native crash, then run focused map-recovery and document-picker checks only if Android is healthy. No application or configuration changes were made: the evidence does not identify a supported code-level remedy. Phase 126 and Phase 128 Bluetooth logs show an Android Bluetooth process abort while starting `HciHalHidl`, which identifies the failing service but not its underlying cause. The earlier native MapLibre `GeoJSONSource::querySourceFeatures` crash remains unproven.

## Attempts and verification

- Started the existing task-owned `MamaPhase116` API 34 emulator without wiping data, using `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-window -memory 2048 -cores 4`. Boot completion was not observed in the initial bounded window, but was observed in an additional window. After 40 seconds, an Android System UI ANR dialog was focused. Events also recorded phone and Google service ANRs. Crash buffer was empty. Evidence: `app/build/phase129/window.txt`, `anr.txt`, `crash.txt`, `emulator-out.txt` and `emulator-err.txt`.
- Stopped only that emulator and performed one no-wipe restart with identical options. Boot completed; the 40-second observation included a System UI Keyguard service ANR, and a follow-up window check showed the System UI ANR dialog. The crash buffer was empty. Evidence: `app/build/phase129/retry-window.txt`, `retry-anr.txt`, `retry-crash.txt`, `retry-out.txt`, `retry-err.txt`. The initially recorded focus was null; the dialog was confirmed on the subsequent check. The emulator was stopped after inspection.
- Focused map-recovery and document-picker tests were **not run** because both starts failed the Android health precondition. No fresh build, lint or unit checks were run, and Phase 128's successful full regression remains historical evidence rather than a fresh result for this phase. No Android error dialog was dismissed, no app or emulator data was wiped, and no hardware was used.

## Interpretation and next step

System UI ANRs recurred on two attempts, including before test execution; they establish an emulator startup failure, not the cause. The second attempt does not reproduce the earlier Bluetooth abort, and neither run tests the MapLibre crash. No controlled comparison establishes memory pressure or graphics settings as the cause. Investigate startup timing and emulator/host diagnostics with bounded, privacy-aware evidence, or run the focused tests on a separately authorized healthy device. Keep crash and ANR gates enabled. Hardware, field accuracy and release acceptance remain separate and unverified. Ignored `app/build` evidence may be removed by a later clean.
