# Phase 221 — Field outline in route import preview

## Goal and scope

The user approved a software-only change on 2026-11-21: show the imported field-outline status before replacing the local working draft, then test GeoJSON and GPX previews on a disposable small-screen emulator. Baseline `46fbe3d` on `main`. The separate APK and demo artifacts under `output/` were already untracked. No hardware or operator phone was used. See D-221-01.

## Execution path and change

Android DocumentsUI returns a local file to `MissionScreen`; the bounded reader and route codecs produce a `MissionDraft` in `pendingImport`. The preview now reads `keepInFence.size` and shows `Field outline: N vertices` or `No field outline`. Confirmation still replaces only the working draft; `Save draft` persists it. No codec, storage, link, permission, dependency or command behavior changed. Imported names remain external input shown in the dialog; the existing reader and codec bounds still apply. The outline is local planning data, not an enforced geofence.

## Verification

- Fresh `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`; 533 unit tests, zero failures; lint zero errors and 27 warnings. App and Android test packages built.
- API 31 disposable emulator `MamaGcs_Phase209_API31`: 30-second preflight passed with no new crash or ANR. Focused `RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation` passed `OK (1 test)` in 77.734 seconds with healthy post-run gate. It used the real DocumentsUI picker, verified the 3-vertex GeoJSON preview and absent-outline GPX preview before confirmation, then checked explicit save behavior and restored the prior emulator draft and recovery state. Evidence: `app/build/acceptance-20261121-140251-08e313a5/`.
- Inspected synthetic small-screen screenshots at `app/build/phase221-preview-evidence/phase221-geojson-preview.png` and `phase221-gpx-preview.png`: both messages and both dialog actions are visible without clipping. These screenshots are ignored build evidence, not tracked release assets.
- `git diff --check` passed (Git reported a line-ending conversion warning for `docs/DECISIONS.md`). Scoped obvious-secret scan found no new credentials. Changed-file review found only the preview, its existing picker test and Phase 221 records.

## Status and remaining limits

Phase 221 implementation and focused emulator verification complete. Review the diff before release. This test covers two synthetic local files on one emulator size; it does not establish all document providers, translations, accessibility sizes, field accuracy, vehicle execution or physical safety. No commit or push was requested for this phase. The prior untracked `output/` artifacts are untouched.
