# Phase 8a — VESC Bluetooth discovery

Date: 2026-10-06. Scope: help identify a nearby BLE radio before choosing a VESC telemetry adapter. This is a diagnostic discovery step, not controller integration.

## Delivered

- Added Systems → VESC Bluetooth, with a user-triggered BLE scan bounded to 12 seconds and stopped when the screen leaves the foreground.
- Displays the advertised device name, signal strength and advertised service UUIDs. It does not display or persist Bluetooth addresses.
- Requests Android Nearby devices permission on Android 12 and newer; Android 11 and older use the platform-required location permission for BLE scanning. Location is not read or stored.
- Keeps platform Bluetooth access behind `VescBluetoothDiscoveryViewModel`.
- Explains that Bluetooth Classic / SPP devices will not appear and that a scan result does not prove VESC protocol compatibility.

## Explicitly not implemented

No pairing, bonding, GATT connection, characteristic reads/writes, Bluetooth Classic / SPP, VESC packet decoding, controller identity verification, live telemetry, or motor commands. Scan results are transient UI data. Android's `neverForLocation` declaration may filter some BLE beacon results; the scan is intended to identify a likely controller radio, not discover every nearby transmitter.

## Hardware-team follow-up

Run the scan with VESC Tool disconnected, then report the likely module name and service UUIDs. Provide the Bluetooth-module label/model and VESC Tool hardware/firmware identification for both channels, plus an approved read-only telemetry path and field definitions. If the module is Bluetooth Classic / SPP, confirm that with its model/label; an empty BLE scan is not evidence that no module exists.

## Validation

Debug APK assembly, debug unit tests, and Android lint passed. The Android UI test covers navigation, discovery-only safety copy and disabled scanning in a non-BLE test environment, but was not run because `adb` and an attached emulator/device were unavailable in the build environment. The live BLE scan still needs verification on the physical Android phone and the actual VESC module.

Android platform references: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [BLE device discovery](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices).
