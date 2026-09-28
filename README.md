# Mama GCS

Mama GCS is a native Android Ground Control Station foundation for Mama UGV vehicles running ArduPilot Rover. Its core operation is designed to remain local and offline-first; it does not require cloud services or Internet access for vehicle communication.

## Phase 1

This initial delivery establishes a Kotlin, Jetpack Compose, Material 3, and Hilt application under `com.mamadrones.gcs`. It provides field-oriented navigation and intentionally telemetry-safe Dashboard, Map, Mission, Health, Control, Admin, and Settings screens. Without a verified MAVLink source, the UI shows **NOT CONNECTED** and **UNKNOWN** rather than fabricated data.

## Phase 2

Phase 2 adds a MAVLink-agnostic `VehicleTransport` contract, transport-level connection state and packet statistics, and a lifecycle-safe UDP implementation. UDP is configured with a remote host/port and a local bind port; its default port is 14550. The app declares only the `INTERNET` permission required for this local UDP capability. Bluetooth Classic and serial are deliberately non-operational extension points: no Bluetooth permissions, pairing, or sockets are introduced yet.

## Architecture

The source tree has clean `presentation`, `domain`, and `data/transport` boundaries. Future phases will add `data/mavlink`, repositories, use cases, and local storage. Compose UI does not access transport or MAVLink APIs.

Planned communication path:

`Vehicle transport (UDP/Bluetooth) → MAVLink parser → repository → StateFlow → ViewModel → Compose UI`

## Build and run

1. Open this folder in Android Studio.
2. Allow Gradle to sync and select an Android 26+ emulator or device.
3. Run the `app` configuration.

From a terminal: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:testDebugUnitTest`.

## Current limitations

The application intentionally contains no MAVLink parser, heartbeat health logic, Bluetooth socket implementation, map provider, telemetry simulation, control commands, Room/DataStore, or background service. Hilt is configured as the dependency-injection foundation, but has no feature bindings yet. No controls can command a vehicle.

## Roadmap

Next is Phase 3: MAVLink 2 framing, heartbeat support, system/component identity, armed state, vehicle mode, and heartbeat timeout. ArduPilot Rover SITL is the intended integration target once MAVLink is introduced.
