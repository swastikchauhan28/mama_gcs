# Phase 222 — Enlarged-text import preview check

## Goal and scope

The user approved checking the GeoJSON and GPX route import previews with larger Android text on a disposable emulator. Baseline `0bd278f` on `main`. Separate APK and demo artifacts under `output/` were already untracked. No operator phone, live hardware, mission upload or production code was used. See D-222-01.

## Path and verification

- Reused the existing `RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation` on `MamaGcs_Phase209_API31` (1080 × 2220 pixels, 440 dpi) with Android font scale set to 1.5. The preflight found no new crash or ANR in a 30-second quiet window.
- Installed the previously built Phase 221 debug app and Android test package without clearing emulator app data. The test passed `OK (1 test)` in 94.121 seconds, with a healthy post-run gate. It opened both files through real Android DocumentsUI, asserted the outline message in each preview, used both Cancel and Replace working draft, checked that saving remained explicit, restored prior emulator draft/recovery state, and removed only test-created files. Instrumentation evidence: `app/build/acceptance-20261121-141125-c76c83ae/`.
- Inspected `app/build/phase222-large-text-evidence/geojson-1.5x.png` and `gpx-1.5x.png`. Both show the complete imported name, route summary, field-outline status, save explanation and both dialog actions. The actions wrap to separate rows at 1.5× but remain readable and reachable. Screenshots are synthetic, ignored build evidence.
- Restored the emulator font setting to 1.0 and closed it. No app code or assertions changed. Phase 221 already passed a normal-text picker run and fresh build/unit/lint checks; those results are historical for Phase 222, not new runs.

## Status and limits

Phase 222 passed at the tested emulator size and 1.5× text; no layout fix was needed. Other display sizes, 2× text, translations and assistive technologies remain unverified. This UI result does not establish vehicle behavior or physical safety. Owner diff review remains a release gate. No commit or push was requested for Phase 222.
