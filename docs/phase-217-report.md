# Phase 217 — Rejected GPX import preserves the route

## Goal and baseline

The user approved a software-only Phase 217 on 2026-11-21 to check that a malformed GPX file and one exceeding the route point limit are rejected without changing the saved or working route. Baseline `2293f6d` on `main`; earlier demo media under `output/` were already untracked and remain outside this phase. RC work remains paused. Model/version unavailable. See D-217-01.

## Change and data path

The new `RouteDocumentPickerTest.rejectedGpxImportsPreserveSavedAndWorkingDraft` creates two unique local Downloads documents in a disposable emulator: a GPX route point with an invalid latitude, and a GPX route with 251 points. It starts with a saved one-point route, makes a different unsaved latitude edit, then imports each file through Android DocumentsUI. After each rejection it checks the specific visible error, absence of the replacement preview, unchanged saved route and retained unsaved edit. It restores the emulator's previous draft and recovery copy and deletes only its own document URIs in `finally`.

The external GPX document crosses from Android's file provider into the screen's 256,000-character bounded reader, `MissionPlanViewModel.importDraft`, `MissionDraftRouteCodec` and `MissionDraftGpxCodec`. The decoder rejects invalid coordinates and more than `MissionDraft.MAX_WAYPOINTS` (250); the view model keeps the current draft and displays the error. `MissionDraft` is local persistent route state; the pending import is only a preview until explicitly accepted. No production code, format, dependency, permission, schema, external destination or vehicle command changed.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL in 3m 43s`; 10 tasks executed, 87 up-to-date. Existing JVM XML reports 533 tests in 84 suites, zero failures/errors/skips; the JVM task reused its result. Lint XML reports zero errors and 27 warnings.
- The disposable API 31 emulator's first startup opened a System UI ANR dialog. After a no-wipe restart, the first 30-second preflight caught one Google Play services ANR. Logs are retained under ignored `app/build/phase217-emulator-boot/`. A later quiet 30-second preflight passed before installation. Neither failed preflight is counted as a test pass.
- Installed the current app and Android test APKs on `emulator-5554` with `adb install -r`, without clearing emulator data.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local SDK adb> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#rejectedGpxImportsPreserveSavedAndWorkingDraft'` — fresh `OK (1 test)` in 32.094 seconds. The post-run gate reports zero new crashes or ANRs and no error dialog. Evidence: ignored `app/build/acceptance-20261121-122019-6114248d/`.
- Reviewed the changed test and all phase records; the earlier untracked demo media remain separate. `git diff --check` passed with Git line-ending warnings for the decisions and handover files. A scoped obvious-secret pattern scan of the new test and report found no matches. No assertion, validation or safety gate was weakened.

## Limits and recovery

This is one synthetic file-picker path on one emulator. It does not prove every malformed third-party GPX is safely handled, physical route precision, mission upload or rover motion. A test interruption before `finally` may leave its distinct emulator fixture; inspect the uniquely named documents and local draft before reuse. Reverting this test-only phase would not change production behavior. Owner diff review and the wider release gate remain open. Phase approval does not authorize commit or push.
