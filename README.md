# Mama GCS

Native Android ground-control-station foundation for a Mama agricultural UGV: ArduPilot Rover, VESC motors, spray equipment and hydraulics. Kotlin · Compose · Material 3 · Hilt · offline-first.

## Current delivery: Phase 3 MAVLink HEARTBEAT receive foundation

The expanded agricultural specification uses incremental acceptance. Phase 3 connects an explicit UDP session to a bounded MAVLink 2 HEARTBEAT parser and vehicle-liveness state. It does not add general telemetry, vehicle control, or automatic connection/reconnection.

- Responsive dark field-console design, light/daylight and system themes, original local icons, shared typography, spacing, cards and status treatments.
- Dashboard, Map, Control, Mission, Health, Motors, Spray, Hydraulic, Diagnostics, Admin and Settings. Phones use bottom navigation; wide windows use a side rail. Secondary screens are reached through More or dashboard shortcuts.
- Nullable/UNKNOWN subsystem models and read-only integration contracts. No invented telemetry, safe actuator state, healthy state, vehicle marker or controller/nozzle inventory.
- Disabled, explicitly unavailable emergency stop and hardware actions. Navigation and persistent local theme settings work; vehicle selection explains that pairing is unavailable.
- Basic role/session policy and app-storage/manifest protections, **not** implemented authentication or production command security.
- A saved UDP peer endpoint, explicit open/close controls, fixed-peer packet acceptance and observable packet statistics. The session listens before opening the socket, selects the first valid autopilot HEARTBEAT system/component pair, ignores competing identities, and marks vehicle state degraded after heartbeat timeout. It closes when the app moves to the background and never auto-connects.
- Incremental MAVLink 2 framing and HEARTBEAT CRC/payload validation. Unsigned HEARTBEAT packets can establish liveness; signed frames are rejected because no signing-key verifier is provisioned. CRC and fixed-peer filtering are not authentication.

Opening a configured UDP socket starts the receive session and accepts datagrams only from the configured peer. Only CRC-valid unsigned MAVLink 2 HEARTBEATs from an autopilot component establish vehicle liveness; this does not authenticate the sender. No MAVLink commands or other telemetry are decoded or sent.

**Do not use this build to operate machinery.** The emergency-stop button cannot command or confirm a physical stop. Use the independent physical safety system.

## Architecture

```text
presentation/    stateless Compose screens/components, navigation, design system
                lifecycle-aware Dashboard/Settings ViewModels
domain/          immutable models, repository contracts, use cases
core/            command outcomes/errors, pure authorization policy
data/            theme/UDP endpoint DataStore, vehicle repository,
                foreground-owned UDP transport and HEARTBEAT-only MAVLink session
di/              Hilt bindings
```

Active flow: `repository → Flow/StateFlow → use case/ViewModel → Compose`.
Theme updates use `UI → ViewModel → use case → SettingsRepository → DataStore`.
UI code does not import transport, MAVLink, sockets or storage APIs. The domain layer has no Android/Compose dependencies. Subsystem contracts remain separate from their future adapters; a VESC transport is not assumed to be MAVLink. Application vehicle identity is separate from MAVLink system/component IDs. The current repository has one active vehicle state; multi-vehicle selection/session management is deferred.

## Build and run

Keep the existing toolchain: AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0, compile/target SDK 36, min SDK 26, JVM target 17. Use Android Studio's bundled JDK or a compatible installed JDK and an installed Android SDK 36. Set your own `sdk.dir` in ignored `local.properties` if needed.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
# Requires a running Android 26+ emulator or device:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Open the project in Android Studio, sync, select a device and run `app`. No network, vehicle, login or cloud account is required to use the foundation. More → Settings selects Dark, Light or System; the choice survives process restart. Units are currently metric only.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint report: `app/build/reports/lint-results-debug.html`.

Added runtime dependency: AndroidX Preferences DataStore 1.1.1. Added test-only Compose UI testing libraries at the existing Compose 1.9.0 version. Existing dependencies were not blindly upgraded. Room, map SDKs, crypto libraries and new protocol libraries are deliberately not added for unimplemented features.

## Boundaries and next phases

There is no live map/offline tile provider, telemetry simulator, real actuator control, login/user store, audit persistence, hardware-health evaluation, mission protocol or background communication service. The neutral map grid is decorative and labeled unavailable. Raw UDP packet counts are available only while an explicitly configured socket is open; they are not vehicle telemetry or link health.

See [Phase 1 report](docs/phase-1-report.md) for exact implementation, file inventory and validation; [security boundaries](docs/security.md) and [hardware integration gates](docs/hardware-integration.md) before enabling integrations.

1. Foundation — delivered.
2. **Transport — delivered.** Explicit UDP profile, foreground socket ownership, lifecycle/error states, Bluetooth/serial extension contracts and tests.
3. **MAVLink HEARTBEAT — delivered.** MAVLink 2 framing/CRC, explicit session lifecycle, autopilot identity pinning, heartbeat timeout, and conservative unsigned/signing policy. General telemetry and commands remain out of scope.
4. Telemetry — available measurements, units, source freshness and invalid-value handling.
5. Health — hardware-defined thresholds and readiness assessment.
6. Map — offline-capable map, position, heading and tracks.
7. Drive — authorization, deadman/failsafes, safe commands and confirmed outcomes.
8. VESC — only after controller models and physical telemetry path are confirmed.
9. Spray — verified hardware mapping and interlocks.
10. Hydraulic — verified hardware mapping and interlocks.
11. Missions — planning, transfer and controlled execution.
12. Admin — trusted local authentication, users, sessions, configuration and auditing.
13. Production hardening — reliability/security/device and field validation.

Dependencies between phases matter: secure identity, authorization and required safety gates must exist **before** enabling any control, even if full admin UX is scheduled later.
