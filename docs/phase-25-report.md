# Phase 25 — Hardware-team review report

## Delivered

Open **Rover configuration → Parameter file**, import a local parameter export, select **Hardware summary**, then **Prepare hardware report**. Review the preview and choose **Save text file** to select a destination. Sharing with the hardware team is a separate manual action.

- A frozen UTF-8 report uses the same bounded fields and reference notes as Phase 24. It preserves original value text, source lines, missing-value markers and the current file SHA-256. It includes total imported entry count but not the full file.
- Reference-file contents, search/category filters, original filename/path, comments, arbitrary parameters, accounts and live telemetry are not included. The report does contain selected configuration values and a fingerprint, and is not described as anonymized.
- Explicit questions cover installed firmware, export freshness, output/motor mapping and calibration, receiver/Cube telemetry wiring, phone/remote route, neutral brake/coast behavior, physical stop, link-loss/manual takeover, speed enforcement and VESC feedback.
- No file timestamp or installed firmware is inferred. Export is not an exhaustive configuration backup, a parameter file for uploading, or a safety assessment. It does not establish wiring or resolve contradictory hardware descriptions.
- The preview survives picker cancellation and write failure for retry. Duplicate save attempts are ignored. Import/replacement, clear and recapture are blocked while choosing a destination or writing; the selected preview is the content saved. Subsequent imports or clearing discard earlier previews. Import failures preserve the earlier file review, but discard an earlier report preview.
- Writes run off the main thread, close streams, and have a 128 KiB limit. Provider errors are not exposed. Failed writes warn about possible partial files; the app does not automatically delete them.
- Source files and previews remain memory-only. Activity recreation retains the ViewModel; process recreation cannot restore the snapshot and will not write to a returned destination without it. Leaving the workspace while a provider write is in progress can interrupt it; check for a partial document before sharing.

## Safety boundary

No transport, command sender, vehicle repository, firmware installation, parameter upload, safety approval or drive gate was changed. Hardware-dependent commands and app emergency stop remain unavailable. Opening Android's document picker may close foreground telemetry; reconnect explicitly if needed. The user's parameter export is not bundled in the repository.

## Validation

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 11m 24s**. Debug and instrumentation APKs built.
- **240 JVM tests, zero failures/errors/skips**, including 12 new report-format, bounded-write and export-state tests. Existing parameter-import/comparison tests remain passing.
- Lint passed with **0 errors and 30 existing warnings**, unchanged from Phase 24.
- Targeted API 34 emulator suite: **10/10 passed in 173.216 seconds** (three new report UI tests plus all seven parameter-review regressions). Evidence: `app/build/acceptance-20261012-165921-230ff751/instrumentation.txt`, ending `OK (10 tests)`. Tests use synthetic fixtures; no vehicle link is opened. The full Android suite was not rerun for this phase.
- The existing isolated `MamaRepair` emulator (`emulator-5556`) required the observed Android System UI startup **Wait** action before APK installation. The initial UI hierarchy dump returned a null root; the screenshot `app/build/qa-repair2/phase25-startup.png` confirmed the startup dialog. This is an environment recovery, not an app test pass. The acceptance runner subsequently passed its ANR/crash preflight. No dialogs were dismissed and no other UI automation client was attached during instrumentation. The emulator was kept awake for tests, then shut down.
- `git diff --check` passed. No physical phone/rover tests, real document-provider save/cancel interactions, firmware writes or parameter uploads were performed.

## Manual acceptance

1. Import a current parameter file, prepare the report, and verify its fingerprint and source-line values against Hardware summary.
2. Add a different reference file and apply a raw-review search filter. Prepare again; the report must still show only the current file's bounded summary.
3. Close the preview: no document picker or write should occur. Prepare again, choose Save, then cancel the picker: the same preview should return.
4. Save to a local text document; inspect the document and compare it with the frozen preview. Verify UTF-8 text and the limitations/questions section.
5. Replace or clear the current file, then prepare again: earlier evidence must not remain. During destination selection/write, import and clear must be disabled.
6. If the provider fails, the preview remains and the UI warns that a partial file may exist. Retry to a valid destination; do not share a partial file.

Full physical-phone document-provider interactions, hardware connections and rover operation require separate acceptance; unit tests do not certify them.
