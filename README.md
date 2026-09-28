# Mama GCS

Mama GCS is a native Android Ground Control Station foundation for Mama UGV vehicles running ArduPilot Rover. Its core operation is designed to remain local and offline-first; it does not require cloud services or Internet access for vehicle communication.

## Phase 1

This initial delivery establishes a Kotlin, Jetpack Compose, Material 3, and Hilt application under `com.mamadrones.gcs`. It provides field-oriented navigation and intentionally telemetry-safe Dashboard, Map, Mission, Health, Control, Admin, and Settings screens. Without a verified MAVLink source, the UI shows **NOT CONNECTED** and **UNKNOWN** rather than fabricated data.

## Architecture

The source tree starts with a clean boundary between `presentation` and `domain`. Future phases will add `data/transport`, `data/mavlink`, repositories, use cases, local storage, and dependency injection. Compose UI does not access transport or MAVLink APIs.

Planned communication path:

`Vehicle transport (UDP/Bluetooth) → MAVLink parser → repository → StateFlow → ViewModel → Compose UI`

## Build and run

1. Open this folder in Android Studio.
2. Allow Gradle to sync and select an Android 26+ emulator or device.
3. Run the `app` configuration.

From a terminal with Gradle 8.13 available: `gradle :app:assembleDebug` and `gradle :app:testDebugUnitTest`.

## Current limitations

Phase 1 intentionally contains no UDP, Bluetooth, MAVLink parser, map provider, telemetry simulation, control commands, permissions, Room/DataStore, or background service. Hilt is configured as the dependency-injection foundation, but has no feature bindings yet. No controls can command a vehicle.

## Roadmap

Next is Phase 2: the transport abstraction (`VehicleTransport`) and initial UDP/Bluetooth connection boundaries. MAVLink heartbeat support follows in Phase 3; real telemetry follows in Phase 4. ArduPilot Rover SITL is the intended integration target once MAVLink is introduced.
