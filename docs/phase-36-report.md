# Phase 36 — Export saved BLE stability evidence

## Delivered

Open **Application settings → Bluetooth / BLE**, stop scanning and disconnect BLE, then choose **Prepare stability report** below saved test history. Review the frozen preview and choose **Save text file**. Android asks where to save `mama-ble-stability-report.txt`; sharing afterward is a separate manual action.

- Includes the latest 20 saved results, newest saved first: UTC start and finish/recovery times, recorded duration, outcome and a fixed reason category. It does not include active/unsaved trials or infer which remote was tested.
- Device names/addresses, UUIDs, arbitrary error strings, notifications, raw data, coordinates and accounts are excluded. Unknown outcomes are labelled UNKNOWN; unrecognized reasons are OTHER with details omitted.
- Start/export guards require recovered, nonempty history with no storage error or pending save, and no BLE scan, connection attempt, connection, subscription, close or running trial. The guard is checked again before launching the picker. Export never disconnects or connects BLE itself.
- Preview is immutable while choosing/writing. Cancel and provider failure retain it for retry; dismissing changes no saved history. Duplicate saves are ignored. Writes run off the main thread, close streams and enforce a 16 KiB UTF-8 byte bound.
- A lost process snapshot cannot write to a returned destination. Previews are memory-only; completed history remains in the existing local store. Failed or interrupted provider writes can leave partial documents; review before sharing. No files are automatically deleted.

## Boundaries

This is connection-continuity evidence, not telemetry delivery, vehicle identity, command permission, range or safety certification. An INTERRUPTED duration includes time while the app was closed and may be affected by calendar-clock changes; it is not connected time. The bounded local history is not tamper-evident.

No Bluetooth transport, subscription, Cube parameters, vehicle commands, emergency stop or automatic reconnection changed. The R12/Cube are not required for report export. The previously deferred remote power-off test remains unverified. Other foreground telemetry sessions may close when Android's document picker opens; the preview warns about this.

## Validation

- JVM suite: **325 tests passed**, zero failures/errors/skips, including 13 new report-format, byte-bound, stream-cleanup and export-state tests.
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 11m 10s**. Both APKs built and installed on the existing API 34 emulator with app data retained.
- Lint: **zero errors, 31 warnings**. Existing deprecated Hilt Compose imports also produce compiler warnings; this phase uses the same project convention.
- Targeted API 34 emulator run: **15/15 passed in 54.135 seconds**: five new report UI checks, four existing stability-screen tests and six isolated history-storage tests. Evidence: `app/build/acceptance-20261015-152639-1bcb4d4c/instrumentation.txt`.
- Captured and visually reviewed the synthetic landscape report preview: title, scrollable body, Close and Save controls are readable and do not overlap. Screenshot: `app/build/acceptance-20261015-152639-1bcb4d4c/ble-report-preview.png`. The 1970 timestamp is deliberately synthetic test input.
- `git diff --check` passed. No hardware tests are part of this phase; the previously connected phone was not available over ADB. No phone APK was installed and no Bluetooth connection was opened.
- The real Android document-provider save/cancel interaction and phone portrait layout remain manual acceptance items. Unit tests exercise exact UTF-8 writing, cancellation state, null/broken streams and retry; synthetic UI tests do not replace a real file-picker check.

## Manual acceptance

1. With saved results, disconnect BLE and stop scanning. Prepare the report; compare its UTC times, durations and outcomes with history.
2. Close the preview without saving. History must be unchanged. Prepare again, open Save, then cancel: the same preview returns.
3. Save to a new text file and compare it with the preview. Check the interpretation/privacy section before sharing.
4. Confirm export is disabled during a scan, active connection, running trial or history save. No link should be closed automatically to enable export.
5. If a document provider fails, verify the preview remains and check the destination for a partial document before retrying or sharing.
