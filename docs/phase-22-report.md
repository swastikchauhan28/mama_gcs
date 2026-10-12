# Phase 22 — Read-only Cube parameter-file comparison

## Delivered

Rover configuration → Parameter file now accepts a separate reference export for comparison with the current imported file. This helps the hardware team review configuration differences without connecting to or modifying a rover.

- Import .param sets the current file; Compare with reference file sets the baseline. Each retains a separate SHA-256 fingerprint. Neither label implies live, correct, trusted or newest configuration.
- Comparison classifies each parameter as VALUE TEXT CHANGED, ONLY IN CURRENT FILE, ONLY IN REFERENCE FILE or SAME VALUE TEXT. Both raw values and original line numbers are shown; names are sorted.
- Comparisons use exact parsed value strings: `1`, `1.0`, `1e0` and `0x1` are different text. Comments, whitespace, delimiter and row-order changes alone do not change a parameter's value text. This is not numeric equivalence or firmware-aware interpretation.
- Missing entries are NOT IN FILE, never defaulted, treated as disabled or diagnosed as unsafe. Partial files may be compared, but cannot establish completeness.
- Unchanged entries are hidden by default and can be included. Existing name-search and category filters apply; summary totals cover the complete comparison, not just visible rows.
- Both files use the existing atomic importer: at most 512 KiB, 10,000 entries, UTF-8, two-column format, valid names/finite numeric text and no duplicate names. The largest comparison contains 20,000 distinct names. Import and comparison execute off the main thread; display uses lazy rows.
- Failed current or reference replacements retain both previous snapshots and their comparison with a visible error. Latest import wins; clear-all cancels pending work and prevents late publication. Reference removal is disabled during import, retains the current file, and returns to the single-file review. Picker cancellation leaves the review untouched; picker launch failure is reported without discarding it.
- Both snapshots and comparison remain in navigation ViewModel memory, not persistent storage. Rotation can retain them; process death or leaving the workspace may discard them. A reference-picker result without its current snapshot is rejected with an instruction to import the current file first. Original documents are never modified, URI permissions are not persisted, and no reference contents are exported or uploaded by the app.

## Boundaries

No automatic merge, apply-to-Cube operation, parameter download/upload, calibration, hardware mapping or drive-gate evidence is added. A file fingerprint is not authentication. File-provider access can involve that provider's cloud storage, and opening the picker can background/close foreground-only telemetry; reconnect manually if needed.

The actual Cube export, telemetry wiring, output mapping, ESC calibration and independently verified stopping/takeover behavior are still required before movement integration. File differences do not verify any of these.

## How to use

1. Obtain two existing exports through the hardware team's normal read-only backup procedure. Do not change live settings merely to create differences. Synthetic text fixtures are sufficient for software testing.
2. Open Rover configuration → Parameter file. Use Import .param for the file you want to review as current.
3. Choose Compare with reference file and select the baseline export.
4. Check both fingerprints and review counts, before/after raw values and line numbers. Use search/category filters and Include unchanged as needed.
5. Remove reference returns to current-file review without deleting either document. Clear review clears both in-memory snapshots only.
6. Test a malformed replacement: the error must leave the existing comparison intact. Cancel a picker: nothing should change.

## Validation

- Debug APK and Android UI-test APK built successfully.
- All 208 JVM tests passed (zero failures, errors or skipped tests), including the comparator and reference/current import lifecycle tests.
- All five `ParameterReviewScreenTest` fixtures passed on `emulator-5554` using AndroidJUnitRunner: comparison direction/search, explicit reference removal, disabled reference actions during import, single-file search and failed-import retention. Debug and test APKs were installed with `adb install -r`, without clearing app data.
- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1` completed successfully. Lint reports zero errors and 29 warnings. `git diff --check` passed.
- Real document-picker/provider interaction, process-recreation picker delivery, full portrait/landscape visual acceptance and physical hardware validation remain pending. The executed Compose fixtures use synthetic in-memory files; no rover connection or command was involved.
