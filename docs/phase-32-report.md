# Phase 32 — BLE remote connection stability

## Delivered

- The BLE inspection screen now shows the current GATT connection duration, successful GATT sessions, unexpected disconnect count, previous session duration, and the last disconnect reason.
- Stability counters are kept for the lifetime of the app process and survive manual disconnect/reconnect cycles. They are not persisted across an app restart.
- User disconnects and app backgrounding are recorded as intentional endings. A remote or GATT failure after service discovery is counted as an unexpected drop. Failed attempts before connection are not counted as drops.
- The existing receive-only behavior is unchanged. Service inspection does not subscribe to notification characteristics or send telemetry/control data.

## Limits

- This is diagnostic evidence for phone-to-T12 BLE GATT connection continuity. It does not measure BLE data delivery because the user has not selected a confirmed notification characteristic.
- It does not test the absent R12/Cube path, signal range certification, reconnect automation, phone-lock/background continuity, or rover operation.
- Counts reset when Mama GCS's process restarts. The screen timer updates once per second while open.

## Validation

- `:app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 2m 5s**. Three existing `hiltViewModel` deprecation warnings remain.
- No automated tests were added or run in this phase. No hardware connection was changed by this implementation build.

## Manual stability check

1. Open Bluetooth / BLE and connect to the known `T12_485` entry. Keep Mama GCS in the foreground.
2. Confirm the GATT-connected notice and watch the connection duration for ten minutes.
3. Tap Disconnect BLE, reconnect to `T12_485`, and repeat for three successful sessions. Expected result: successful count reaches three and unexpected drops remains zero.
4. If the connection drops on its own, capture the BLE link error and the stability panel. The last disconnect reason should explain the app-observed failure.

This check establishes only phone-to-remote GATT continuity. It does not validate rover telemetry or control.
