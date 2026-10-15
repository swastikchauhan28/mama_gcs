# Phase 38 — Confirm and bind BLE receive selection

## Delivered

In **Application settings → Bluetooth / BLE**, tapping a discovered receive characteristic now opens **Confirm BLE telemetry receive** rather than immediately subscribing.

- Review the selected device label, service UUID, characteristic UUID, both instance numbers and NOTIFY/INDICATE method. Cancel or dismiss to leave GATT connected without starting reception.
- **Start receive** explicitly starts the existing receive-only workflow. The dialog explains that this enables Android notifications and writes the BLE configuration descriptor; it does not send MAVLink, motor or vehicle commands.
- Pending selection is bound to the process-local successful GATT connection number, which increments only after service discovery succeeds. It is not based on calendar time, device name or UUID alone.
- The selection must still match a discovered characteristic exactly, including service/characteristic instances and notify/indicate method. The ViewModel validates before starting a decoder, and the transport validates again before changing subscription state or writing the descriptor.
- Disconnect/reconnect, connecting/closing, another telemetry link, a changed/removed receive option or a started subscription invalidates the dialog. Leaving the foreground clears it. Pending confirmations are not persisted across activity/process recreation and are not automatic reconnect preferences.
- The existing app-navigation test now requires confirmation and checks the exact connection-bound request. Existing notification setup, parsing and exclusive-link ownership remain unchanged.

## Boundaries

This is accidental-action prevention, not protocol detection, a verified hardware profile or trusted vehicle pairing. The operator must still obtain confirmation that the chosen characteristic carries MAVLink. Names and UUIDs alone cannot establish that. For a remote-only continuity test with no Cube/R12, cancel; notification subscription is unnecessary.

No new notification characteristic was selected on real hardware during implementation or tests. No commands, stream-rate requests, parameter changes, automatic retries or background reception were added. Movement and app emergency stop remain unavailable.

## Validation

- `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`: **BUILD SUCCESSFUL** (12m 29s).
- **345 JVM tests passed**, zero failures/errors/skips, including six new selection-validity tests.
- Lint: **zero errors, 31 warnings**, unchanged warning count from Phase 37.
- API 34 emulator: **26 targeted synthetic checks passed** in 380.662 seconds: five new confirmation tests, three BLE app-navigation checks, three connection-checklist checks, four stability-screen checks, five report-action checks and six history checks. No real radio was connected and no notification descriptor was written by these fixtures.
- Landscape screenshot reviewed: full UUIDs and Cancel/Start receive buttons are visible; the longer explanation is in a scrollable dialog body. Portrait, large-font and physical-phone visual acceptance are not claimed by this run.
- Local ignored evidence: `app/build/acceptance-20261015-161903-f2af94aa/instrumentation.txt` and `ble-receive-confirmation.png` in the same directory.
- The first emulator startup showed an Android System UI ANR before testing; the runner refused to start. A cold boot without snapshot restore resolved it. No error dialog was dismissed to obtain a pass, and no app data was cleared.

## Manual acceptance

1. Connect to the identified remote and tap a receive option. Verify that no receive counters/subscription change merely from opening the dialog.
2. Cancel or press Back. GATT should remain connected and the connection-only test remain available.
3. Only for a confirmed MAVLink characteristic, review its complete UUIDs and instance numbers, then explicitly choose Start receive. Observe the Phase 37 checklist; subscription alone is not telemetry proof.
4. With the dialog open, disconnect the link or leave the app. After returning/reconnecting, the old confirmation must not reappear or start reception.
5. Review a newly discovered option again after reconnecting. The old connection's request cannot be reused, even if the device and UUIDs are identical.

Real descriptor/subscription behavior, Cube/R12 telemetry and physical remote-loss acceptance remain pending; synthetic tests do not certify rover operation.
