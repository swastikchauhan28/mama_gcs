# Phase 130 — fresh-emulator startup comparison

Date: 2026-10-31
Starting revision: `e079eaa502c09b6929be7ea80c01290d57193729`, clean and synchronized with `origin/main`. Model/version unavailable.

## Goal and design

Compare the Phase 129 startup failures with a fresh, isolated Android emulator, and run the map-recovery and document-picker test groups only if startup is healthy. A fresh emulator uses separate data; it does not establish that the original emulator is repaired. No production code, dependency, existing emulator data, phone, or hardware was changed.

## Observation

Created task-owned `MamaPhase130` under `app/build/phase130/avd-home` using a copy of the baseline AVD configuration only, with a new identity and no copied user data. It used the same installed API 34 Google APIs x86_64 system image and launch options as Phase 129: `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-window -memory 2048 -cores 4`. Its first boot completed. After the 40-second observation, the launcher was focused and the crash buffer was empty, but Android's event buffer contained multiple service ANRs, including Google Play services and Bluetooth. The healthy-start precondition therefore failed even though no error dialog was focused. The two focused test groups were not run, and no app or test APK was installed in this emulator. The task emulator was stopped after inspection.

Evidence: `app/build/phase130/window.txt`, `anr.txt`, `crash.txt`, `emulator-out.txt`, and `emulator-err.txt`. The `app/build` directory is ignored and may be removed by a future clean. No fresh build, unit tests or lint were run in this phase.

## Interpretation and next step

The fresh-data emulator also recorded startup service ANRs under the same host, Android image and launch options; this weakens the hypothesis that only the old emulator's stored data explains all startup trouble. The observed ANRs are not identical to the Phase 129 focused System UI dialog, and the host, image, options and load were not controlled independently, so a common cause is not established. This test did not reproduce or investigate the earlier MapLibre native crash. Review host/emulator diagnostics or choose a healthy authorized test device before rerunning the focused tests; do not suppress ANRs or bypass health checks. Hardware, field accuracy, and release acceptance remain unverified.
