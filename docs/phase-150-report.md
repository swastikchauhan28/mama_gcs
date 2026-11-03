# Phase 150 — local planning and exchange journey

Date: 2026-11-03. Starting revision: `aecdbd6`, clean `main` aligned with locally recorded `origin/main`. Model: gpt-6.1-terra. The user approved a software-only end-to-end check and a README heading correction.

## Journey and boundaries

`RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute` operates the real app UI on a disposable emulator. It creates a uniquely named synthetic draft with two waypoints and a three-vertex field outline; enters a 2 m tool width and 10% overlap; generates and displays the coverage diagram; saves the draft; exports GeoJSON through Android DocumentsUI; starts a new working draft; and imports the file through the picker. It checks waypoint order and coordinates, outline coordinates, and that import replaces the working draft only until explicitly saved. Coverage is a transient model, not a route upload or proof of actual field work. See D-150-01 in [DECISIONS](DECISIONS.md).

The test saves the emulator's previous local draft/recovery, temporarily replaces them with synthetic data, then attempts restoration in `finally`. It creates and deletes only a uniquely named test file under emulator Downloads; interruption or cleanup failure could leave synthetic data behind. The map may contact the configured provider. No physical phone, vehicle, parameter or command was used. The README's Phase 52–53 heading now says “Previous change” instead of incorrectly saying uncommitted.

## Verification

- Final `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`, 69 tasks (9 executed, 60 up-to-date); lint report has 27 warnings. No fresh JVM suite or full Android regression was run in this phase.
- On the no-wipe isolated `Pixel_3a_API_34_extension_level_7_x86_64` emulator, `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass com.mamadrones.gcs.RouteDocumentPickerTest#createFieldPreviewSaveExportAndReimportLocalRoute` returned `OK (1 test)` in 87.603 s and Android health gate true. Evidence is in ignored `app/build/acceptance-20261103-124415-018ee9ee/` and `app/build/phase150/journey-retry.log`.
- The first run timed out waiting for the offscreen lazy-list coverage diagram before the test scrolled to it; Android health was true. The test was corrected to wait for calculation completion, scroll to the diagram and retain its display assertion; the full retry passed. First-run evidence remains in ignored `app/build/acceptance-20261103-123725-2b1f22cf/` and `app/build/phase150/journey.log`.
- The synthetic screenshot `app/build/phase150/coverage.png` was visually reviewed: the field diagram, modeled unplanned area and limitations are visible. The emulator was stopped without wiping data.

## Limits and next step

This is an automated user journey, not a human-operated walkthrough, full regression, mission upload, physical coverage measurement or release approval. GPX was not exercised by this journey; its separate Phase 142 tests remain historical evidence. Hardware, field, owner review/teach-back and release gates remain open. Review the test diff and perform a human walkthrough on a suitable disposable installation before release. Commit/push require a separate request.
