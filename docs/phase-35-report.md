# Phase 35 — Recover interrupted BLE stability tests

Follow-up: [physical-phone validation and fixes, 2026-10-15](ble-stability-validation-2026-10-15.md).

## Delivered

- Starting a ten-minute BLE stability run now writes its start time to local DataStore.
- If the app process ends before recording a result, the next launch recovers the unfinished run as **INTERRUPTED**, with elapsed time and a clear reason. The test cannot be started until that recovery check completes.
- Finishing a run saves its result and clears the active marker in one DataStore transaction, preventing duplicate or lost results across a process restart.
- Existing Phase 34 history stored with the earlier record format is migrated when the next run is saved or an interrupted run is recovered.

## Limits

- This recovery covers app process termination. If the phone clock changes while Mama GCS is closed, the recovered elapsed time may be approximate; negative durations are clamped to zero.
- Results remain local to the phone. The feature still measures only foreground phone-to-T12 GATT connection continuity.

## Validation

- `:app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 1m 39s**. Three existing `hiltViewModel` deprecation warnings remain.
- No automated tests were added or run in this phase. No physical BLE or rover connection was initiated by the build.

## Manual check

1. Connect to `T12_485`, start the ten-minute test, then force-stop Mama GCS before it finishes.
2. Reopen the app and check **Saved stability tests**. The unfinished run should appear once as **INTERRUPTED**.
3. Start and complete another run; confirm it is saved as **PASSED** and the interrupted record remains in history.
