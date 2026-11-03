# Phase 151 — unsaved editor recovery check

Date: 2026-11-03. Starting revision: `13e5b88`, clean `main` aligned with locally recorded `origin/main`. Model: gpt-6.1-terra. The user approved a disposable-emulator recovery check.

## Journey and boundaries

`MissionPlanningTest#unsavedRouteAndOutlineRecoverAfterClosingAndReopeningEditor` saves a synthetic baseline, enters two waypoints and a three-vertex outline through the real app UI, and waits for the separate recovery copy to persist. It verifies the committed baseline is unchanged, closes the activity, opens a new activity and ViewModel, checks the recovered-unsaved status and editable coordinates/outline, then explicitly saves and checks the coordinates and cleared recovery copy. See D-151-01 in [DECISIONS](DECISIONS.md).

The test uses the existing isolated emulator image, temporarily replaces its draft and attempts to restore its previous draft/recovery in `finally`. Interruption or cleanup failure can leave synthetic emulator data. This is activity close/relaunch with a fresh ViewModel; it does not terminate the Android process or demonstrate OS process-death recovery. No physical phone, hardware or vehicle command was used; no production code or persistence format changed.

## Verification

- `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`, 69 tasks (10 executed, 59 up-to-date); lint report has 27 warnings. No fresh JVM suite or full Android regression ran in this phase.
- The first isolated emulator invocation returned `OK (1 test)` in 125.989 s, but the acceptance health gate failed because Google Play services recorded an ANR. Evidence: ignored `app/build/acceptance-20261103-125709-a73896fc/` and `app/build/phase151/recovery.log`.
- The unchanged focused test passed on retry: `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.MissionPlanningTest#unsavedRouteAndOutlineRecoverAfterClosingAndReopeningEditor` returned `OK (1 test)` in 82.463 s with Android health gate true. Evidence: ignored `app/build/acceptance-20261103-130008-9b421e37/` and `app/build/phase151/recovery-retry.log`.
- The synthetic screenshot `app/build/phase151/recovered.png` was visually reviewed: the recovered-unsaved message and enabled Save draft button are visible. The map in that capture was blank, so map rendering is not established by this screenshot. The emulator was stopped without wiping data.

## Limits and next step

The passing retry is not a fix for the earlier ANR or existing emulator/Compose instability. OS process-death recovery, a human-operated walkthrough, hardware and release acceptance remain open. Review the scoped test diff and plan process-death validation separately if needed. Commit/push require a separate request.
