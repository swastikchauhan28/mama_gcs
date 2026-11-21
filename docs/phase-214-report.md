# Phase 214 — Saved route precision round trip

## Goal and scope

The user approved a software-only check on 2026-11-21 to close the Phase 213 gap: confirm that a high-precision waypoint survives UI edit, explicit local save, activity reopen, and a second no-change edit. Baseline `17f5ac2` on `main`; the phone-demo media under `output/` were already untracked and were not changed. RC work remained paused. No production code, dependencies, permissions, schemas, transports, vehicle commands, or operator-phone data changed. See D-214-01.

## Execution path and change

`MissionMapDialogTest.preciseWaypointSurvivesSaveReopenAndUnchangedEdit` exercises the real `MainActivity` and `MissionScreen` route editor through `MissionPlanViewModel` into `LocalMissionDraftRepository`. The test seeds a disposable local draft, edits its waypoint to `-0.000000123456789, 149.16523456789`, waits for the recovery copy, explicitly presses Save draft, closes and relaunches the activity, checks both exact edit-field strings, confirms without changes, and compares the persisted `MissionDraft` by value. It checks that Save draft is disabled after the no-change edit. The previous saved draft and recovery copy are restored in `finally`.

The draft is a bounded list of up to 250 `DraftWaypoint` values. This phase changes only the Android acceptance test and project records. A passing local round trip does not establish physical survey accuracy or rover motion.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 14m 47s`; 533 JVM tests in 84 suites, zero failures/errors/skips; both debug APKs built; lint zero errors, 27 warnings.
- The API 34 disposable emulator logged a Google Play services ANR on first boot and a System UI ANR after a clean restart, before app testing. The API 31 disposable emulator also logged a Google Play services startup ANR. Evidence is retained in ignored `app/build/phase214-emulator-boot/`; these are real environment failures, not test passes. Once OneDrive was closed, the API 31 emulator passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local SDK adb> -QuietSeconds 30` with no new crash or ANR events.
- Installed both current debug APKs with `adb install -r` on the disposable API 31 emulator. `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.MissionMapDialogTest#preciseWaypointSurvivesSaveReopenAndUnchangedEdit'` — `OK (1 test)`; post-run Android health passed. Evidence: ignored `app/build/acceptance-20261121-112643-98272794/`.
- `git diff --check` passed (only Git LF/CRLF conversion warnings for `docs/DECISIONS.md`). No UI visual inspection was required because only test and documentation code changed.

## Limits and next step

The targeted emulator check does not cover all Android flows, device storage failure, physical field accuracy, mission upload, or vehicle control. The API 34 emulator did not achieve a healthy startup in this phase; the successful UI check ran on API 31. Review the scoped diff and evidence before any release. The user separately requested commit and push after completion. No rollback was executed; removing the test and report would reverse this test-only phase without changing persisted operator data.
