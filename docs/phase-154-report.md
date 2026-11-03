# Phase 154 — local mission-planning walkthrough

Date: 2026-11-03. Starting revision `fece9c7d20cf37f51e35cccde17dc52a189acd3b`, clean `main`. Model/version unavailable. User approved the walkthrough and disposable-emulator test. See D-154-01.

## Scope

Added [mission-planning-walkthrough](mission-planning-walkthrough.md) and a README link. The guide explains draft preservation, example WGS84 waypoints and field outline, a 2 m tool with 10% overlap, coverage interpretation, save and GeoJSON export/import. It warns that GPX does not preserve the local outline, imported edits require explicit save, the preview is not a route, turns are not planned, and rover upload/execution and physical field coverage remain unverified. UI labels and data behavior were checked against current source and the existing automated journey. No app, phone data, hardware, vehicle command, dependency or build configuration changed.

## Emulator verification

- Used the existing isolated Phase 152 image, `Pixel_3a_API_34_extension_level_7_x86_64`, emulator-5554, no snapshot, 2048 MB and `swiftshader_indirect`. The test creates synthetic drafts and a uniquely named GeoJSON file, attempts to restore the previous emulator drafts and removes only its own file in `finally`; interruption could leave synthetic data. It may use the configured map provider. No physical phone or hardware was used.
- `./scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <local adb.exe> -QuietSeconds 30`: the first and restarted-emulator observations each detected one Google Play services ANR and blocked their attempts. Evidence: ignored `app/build/phase154/preflight.txt`, `preflight-anr-events.txt`, `preflight-retry.txt` and `preflight-retry-anr-events.txt`. After inspecting the second failure and confirming the launcher was visible, a later independent observation passed; evidence: `app/build/phase154/preflight-settled.txt`. These preflight failures remain unresolved.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb <local adb.exe> -TestClass 'com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute'`: `OK (1 test)` in 70.466 s, Android health gate true. The automated journey verified route and outline coordinates, coverage diagram, saved draft, GeoJSON export and import, and explicit post-import save. Evidence: ignored `app/build/phase154/walkthrough-test.txt` and `app/build/acceptance-20261103-143749-bd584830/`.
- Visually inspected the synthetic `app/build/phase154/coverage.png`: the diagram, modeled unplanned area/range and warning that estimates are not actual soil or positioning accuracy are visible. The emulator was stopped without wiping.
- This is an automated real-app journey, not a human-operated walkthrough or a fresh test of GPX, every guide branch or a full regression. No fresh app build or JVM suite ran because this phase changed documentation only.

## Remaining

The owner should review and use the guide on a suitable disposable installation; no owner understanding is assumed. Earlier emulator ANRs remain unexplained. Hardware heartbeat, actual field coverage and release acceptance remain open.
