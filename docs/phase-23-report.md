# Phase 23 — Passive autopilot firmware information

## Delivered

Rover configuration → Autopilot info displays metadata observed from the selected autopilot, without assuming the user's reported Cube model or firmware is what the app actually received.

- Supports the implemented AUTOPILOT_VERSION fields on both MAVLink 1 and 2 through the existing shared UDP, BLE and Classic receive pipeline.
- Firmware is displayed as major.minor.patch plus release family and the exact release-type byte. Other retained fields (middleware, OS, board version, vendor and product IDs) are raw hexadecimal, not inferred hardware names. All-zero fields are labeled ZERO / UNSPECIFIED (0), not a known firmware version or model.
- The first valid autopilot heartbeat still selects the source. Version messages before selection or from another system/component are ignored. CRC, payload-bound and signing-rejection policy remain in effect.
- A current-session observation retains its receipt age while the heartbeat remains live. No five-second sensor freshness rule is applied to version metadata. Disconnection, degraded connection, inactive session or an old/future heartbeat changes the label to LAST OBSERVATION · LINK NOT LIVE. Missing-session, previous-session and future-dated observations hide the fields.
- Starting a new receive session resets this metadata. It is not persisted. Hardware UIDs, custom version arrays and capability flags are not retained in decoded/domain state; new fields are excluded from telemetry-report export.
- No message request, stream-rate request, firmware update, parameter write, pairing, automatic compatibility decision or actuator command is added. Drive admission remains unchanged.

## Protocol reference

The [MAVLink AUTOPILOT_VERSION definition](https://mavlink.io/en/messages/common.html#AUTOPILOT_VERSION) describes firmware metadata and request-driven delivery. The [generated reference header](https://github.com/mavlink/c_library_v2/blob/master/standard/mavlink_msg_autopilot_version.h) specifies message ID 148, CRC extra 178, base length 60 and extended length 78. Used field offsets are 16/20/24/28 for uint32 versions and 32/34 for uint16 vendor/product IDs. MAVLink 2 trailing zero restoration uses the existing parser after checksum verification. Extension bytes are accepted but not retained. The [release-type enumeration](https://mavlink.io/en/messages/common.html#FIRMWARE_VERSION_TYPE) supplies release-family boundaries; the raw byte is always visible.

## How to check

1. Use Rover configuration → Autopilot info. With no received message, expect NOT RECEIVED and passive-reception guidance, not an assumed 4.7.0 value.
2. Use a synthetic fixture or an already-authorized, independently secured telemetry setup. This phase's automated tests use only synthetic messages; no radio connection is necessary.
3. If AUTOPILOT_VERSION arrives after the selected source heartbeat, inspect version, release type, raw values and age. A Cube may not send this message automatically; missing data does not prove unsupported firmware. The app intentionally does not request it.
4. Disconnect: the observation remains explicitly historical. Reconnect: the panel resets until a new accepted version message arrives.
5. Compare with the hardware team's authoritative firmware/configuration records. This display cannot authenticate a board, verify motor wiring, establish safe stopping or replace the Cube parameter export.

## Validation

- Debug APK and Android UI-test APK built successfully, including the final null-check cleanup.
- All 220 JVM tests passed with zero failures, errors or skips. New coverage includes wire-format/unsigned fields, truncation/bounds, rejected packets, omitted identifiers, source-selection/no-transmit, reconnect reset and presentation states.
- Both `AutopilotVersionScreenTest` tests passed on `emulator-5554` with synthetic in-memory state: absent metadata/passive guidance and disconnected historical metadata. APKs were installed with `adb install -r`; no app data was cleared and no radio was connected.
- Final `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1` rerun succeeded. Lint reports zero errors and 29 warnings. `git diff --check` passed.
- Physical Cube/T12/R12 delivery, full navigation/portrait/landscape visual acceptance and physical-device testing remain unverified. These tests do not certify rover operation.
