# Phase 37 — BLE reception-stage check

## Delivered

**Application settings → Bluetooth / BLE → BLE connection check** shows five separate evidence stages:

1. Remote GATT connection open.
2. Notify/indicate subscription completed.
3. Current notification bytes observed at both the transport and decoder.
4. A recent supported MAVLink message decoded (not necessarily accepted from the selected autopilot).
5. A fresh accepted heartbeat from the selected autopilot, with the vehicle receiver currently connected.

The panel identifies the first missing stage and gives read-only guidance for disconnected, connecting, closing, error, remote-only, no receive option, subscribing, missing decoder session, missing/stale bytes, unconfirmed MAVLink and unconfirmed autopilot states. It is visible near the top of the BLE screen instead of requiring the operator to interpret scattered counters.

## Evidence boundaries

- A successful remote-only GATT connection is explicitly distinct from telemetry. The UI explains that connection-only stability testing is possible without the absent R12/Cube and does not encourage selecting unknown notification characteristics.
- A decoder must be active, belong to BLE, and have started at or after this GATT connection. Samples must fall within that decoder session and not in the future. Historical counters, another link, closed sessions and earlier reconnect samples cannot supply live evidence.
- Both transport and decoder byte counts and receipt times are checked. Decoded-message totals alone are insufficient: a current per-message observation is required. A heartbeat additionally needs a fresh accepted HEARTBEAT observation and the current vehicle heartbeat/status.
- Receipt evidence expires after a five-second inclusive display window. The existing screen timer reevaluates it without requiring another packet. This is not a configured radio timeout, validated failsafe or stop action. It uses the existing calendar timestamps; inconsistent session boundaries are rejected conservatively.
- No measurement validity, trusted identity, exact physical radio path, range or control capability is inferred, even when all five stages are present. A fresh heartbeat is not proof that GPS/motor/sensor measurements are valid or available.
- No transport, characteristic subscription, stream-rate request, firmware/parameter setting, background reception, automatic reconnect, command admission or emergency-stop behavior changed. The panel is a pure assessment plus display; existing explicit receive actions are unchanged.

## Validation

- JVM suite: **339 tests passed**, zero failures/errors/skips. Fourteen new assessment tests cover all stage families, wrong/closed sessions, timestamp boundaries, missing/stale bytes, decoder evidence, rejected/foreign messages, accepted heartbeat requirements and reconnect isolation.
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 5m 33s**. Both APKs installed on the existing API 34 emulator with app data retained.
- Lint: **zero errors, 31 warnings**; existing Hilt Compose deprecation compiler warnings remain.
- Targeted API 34 emulator run: **18/18 passed in 64.943 seconds**: three new connection-panel tests plus the existing four stability-screen, five report-UI and six isolated history-storage tests. Evidence: `app/build/acceptance-20261015-154216-ec7d0233/instrumentation.txt`.
- Captured and visually reviewed the synthetic remote-only landscape panel. The five evidence rows and guidance are readable without overlapping; the screen scrolls for additional content. Screenshot: `app/build/acceptance-20261015-154216-ec7d0233/ble-connection-check.png`.
- `git diff --check` passed. No remote, Cube or receiver connection was initiated. No phone APK was installed. The checks use synthetic states and isolated storage, not real radio reception.
- Physical receipt/freshness transitions, portrait-device visual acceptance and remote power-loss remain unverified. Passing these tests does not certify rover operation.

## Manual acceptance

1. Open Bluetooth / BLE with no connection: the checklist must not claim a fresh heartbeat even if another transport has live telemetry.
2. Connect only to the identified T12_485. Without starting receive, expect REMOTE CONNECTED / RECEIVE NOT STARTED (or NO RECEIVE OPTION if no supported characteristic is discovered). Run the existing connection-only stability test if desired.
3. Only once the MAVLink characteristic is confirmed, explicitly start receive. Watch subscription, bytes, decoded messages and accepted heartbeat separately. Missing data with the Cube/R12 absent is not a failed remote-only stability test.
4. With a hardware-team-secured telemetry setup later, confirm the panel reaches live heartbeat evidence. Stop the data source: the panel must stop presenting live evidence once it expires. This phase does not perform that physical test.
5. Disconnect/reconnect. Old counters must not restore live checklist stages before new-session samples arrive.

Outstanding physical remote power-loss testing and real document-provider save/cancel acceptance from earlier phases remain outstanding; this panel does not complete them.
