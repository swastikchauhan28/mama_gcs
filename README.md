# Mama GCS

Native Android ground-control-station foundation for a Mama agricultural UGV: ArduPilot Rover, VESC motors, spray equipment and hydraulics. Kotlin · Compose · Material 3 · Hilt · offline-first.

## Current delivery: Phase 6 live MapLibre vehicle map

The expanded agricultural specification uses incremental acceptance. Phase 6 now combines the live coordinate, heading, and recent track with a MapLibre Native geographic map using the MapTiler Streets vector style. Track samples stay in memory for the current session. Licensed offline regions remain deferred, and vehicle control remains unavailable.

- Responsive dark field-console design, light/daylight and system themes, original local icons, shared typography, spacing, cards and status treatments.
- Dashboard, Map, Control, Mission, Health, Motors, Spray, Hydraulic, Diagnostics, Admin and Settings. Phones use bottom navigation; wide windows use a side rail. Secondary screens are reached through More or dashboard shortcuts.
- Nullable/UNKNOWN subsystem models and read-only integration contracts. No invented telemetry, safe actuator state, health verdict, or controller/nozzle inventory.
- Disabled, explicitly unavailable emergency stop and hardware actions. Navigation and persistent local theme settings work; vehicle selection explains that pairing is unavailable.
- Basic role/session policy and app-storage/manifest protections, **not** implemented authentication or production command security.
- A saved UDP peer endpoint, explicit open/close controls, fixed-peer packet acceptance and observable packet statistics. The session listens before opening the socket, selects the first valid autopilot HEARTBEAT system/component pair, ignores competing identities, and marks vehicle state degraded after heartbeat timeout. It closes when the app moves to the background and never auto-connects.
- Incremental MAVLink 2 framing and CRC/payload validation for HEARTBEAT, GPS_RAW_INT, GLOBAL_POSITION_INT, ATTITUDE, SYS_STATUS, BATTERY_STATUS, and STATUSTEXT. Unsigned packets update source-specific state and receive times; signed frames are rejected because no signing-key verifier is provisioned. CRC and fixed-peer filtering are not authentication.
- Read-only health evidence labels MAVLink link, GPS, battery, and autopilot sensor data as not received, reported, or protocol-reported issue. Operational readiness remains **NOT ASSESSED** until a vehicle-specific health profile, hardware limits, expected telemetry rates, and validated subsystem routes are provisioned.
- Map and dashboard draw the current GLOBAL_POSITION_INT coordinate, heading marker, and a bounded track over a MapTiler vector basemap after at least 0.5 m of reported movement. Center and follow controls are functional; a user pan disables following. Track data is cleared at a new telemetry session and is not persisted.

Opening a configured UDP socket starts the receive session and accepts datagrams only from the configured peer. A CRC-valid unsigned HEARTBEAT from an autopilot component establishes liveness; the pinned system/component source gates telemetry updates. Receive timestamps are displayed with measurements; protocol liveness and CRC do not authenticate the sender. No MAVLink commands are sent.

**Do not use this build to operate machinery.** The emergency-stop button cannot command or confirm a physical stop. Use the independent physical safety system.

## Architecture

```text
presentation/    stateless Compose screens/components, navigation, design system
                lifecycle-aware Dashboard/Settings ViewModels
domain/          immutable models, repository contracts, use cases
core/            command outcomes/errors, pure authorization policy
data/            theme/UDP endpoint DataStore, vehicle repository,
                foreground-owned UDP transport and allowlisted MAVLink telemetry session
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

Open the project in Android Studio, sync, select a device and run `app`. The online map requires a MapTiler key in ignored `local.properties`; see [MapLibre and MapTiler setup](docs/maplibre-maptiler.md). The remaining foundation can open without a vehicle or login. More → Settings selects Dark, Light or System; the choice survives process restart. Units are currently metric only.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint report: `app/build/reports/lint-results-debug.html`.

Runtime dependencies include AndroidX Preferences DataStore 1.1.1, MapLibre Native 13.6.1, and OkHttp 4.12.0 for the app-specific map User-Agent. Test-only Compose UI testing libraries remain aligned with Compose 1.9.0. Room, crypto libraries and new protocol libraries are deliberately not added for unimplemented features.

## Boundaries and next phases

There is no licensed offline-region implementation, bundled telemetry simulator, real actuator control, login/user store, audit persistence, configured hardware-health evaluation, mission protocol or background communication service. The online map requires MapTiler network access and an operator-provided client key. Raw UDP packet counts are available only while an explicitly configured socket is open; they are not vehicle telemetry or link health.

See [Phase 1 report](docs/phase-1-report.md), [Phase 2 report](docs/phase-2-report.md), [Phase 3 report](docs/phase-3-report.md), [Phase 4 report](docs/phase-4-report.md), [Phase 5 report](docs/phase-5-report.md), and [Phase 6 report](docs/phase-6-report.md) for implementation scope and validation. For a repeatable physical-phone test with ArduPilot Rover SITL under WSL2, use the [SITL phone telemetry guide](docs/sitl-phone-telemetry.md). Review [security boundaries](docs/security.md) and [hardware integration gates](docs/hardware-integration.md) before enabling integrations.

1. Foundation — delivered.
2. **Transport — delivered.** Explicit UDP profile, foreground socket ownership, lifecycle/error states, Bluetooth/serial extension contracts and tests.
3. **MAVLink HEARTBEAT — delivered.** MAVLink 2 framing/CRC, explicit session lifecycle, autopilot identity pinning, heartbeat timeout, and conservative unsigned/signing policy.
4. **Telemetry — delivered.** GPS raw fix, global position/kinematics, attitude, system status, per-pack and aggregate battery readings, and bounded STATUSTEXT display with source receive ages and invalid-value handling. No health verdicts or controls.
5. **Health evidence and readiness gate — delivered.** Protocol-reported evidence is visible, while readiness is explicitly not assessed without a signed-off vehicle profile and hardware integration data.
6. **Map telemetry and online basemap — delivered.** MapLibre Native, MapTiler Streets vector tiles, live coordinate/heading, bounded current-session track, and center/follow controls. Licensed offline regions remain deferred.
7. Drive — authorization, deadman/failsafes, safe commands and confirmed outcomes.
8. VESC — only after controller models and physical telemetry path are confirmed.
9. Spray — verified hardware mapping and interlocks.
10. Hydraulic — verified hardware mapping and interlocks.
11. Missions — planning, transfer and controlled execution.
12. Admin — trusted local authentication, users, sessions, configuration and auditing.
13. Production hardening — reliability/security/device and field validation.

Dependencies between phases matter: secure identity, authorization and required safety gates must exist **before** enabling any control, even if full admin UX is scheduled later.
