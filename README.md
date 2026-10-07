# Mama GCS

Native Android ground-control-station foundation for a Mama agricultural UGV: ArduPilot Rover, VESC motors, spray equipment and hydraulics. Kotlin · Compose · Material 3 · Hilt · offline-first.

## Current delivery: BLE MAVLink receive link (Phase 13)

The expanded agricultural specification uses incremental acceptance. The current hardware-independent delivery is local mission planning with recovery of unsaved edits, GeoJSON and GPX route-file exchange, a separate on-device route library, structural review, map-based waypoint editing, and an optional local outline that checks waypoint positions and straight draft segments. GPX contains route waypoints only; use GeoJSON to retain Mama GCS's local outline. Phase 8 VESC telemetry groundwork remains pending verification of the Bluetooth module and telemetry path; Phase 8a adds BLE advertisement discovery only to help identify that module. Spray and hydraulic adapters also await hardware details. Mission transfer/execution and vehicle commands remain disabled.

Phase 12 adds read-only ArduPilot Rover `VFR_HUD` instruments (ground speed, heading, throttle, altitude, and climb rate); see the [Phase 12 report](docs/phase-12-report.md). Phase 13 adds runtime BLE GATT inspection and a selectable notify/indicate MAVLink byte receiver; see the [Phase 13 report](docs/phase-13-report.md). Commands remain disabled, and VESC telemetry still needs its own validated route and decoder.

- Responsive dark field-console design, light/daylight and system themes, original local icons, shared typography, spacing, cards and status treatments.
- Map-first Operate, Map, Drive, Plan and Systems workspaces. Phones use bottom navigation; wide windows use a side rail and instrument dock. Connection access and emergency-stop unavailability remain visible on every screen. Detailed telemetry and equipment are reached through Systems or operating shortcuts. See the [operator design and requirements matrix](docs/operator-ui-design.md) for coverage and remaining capabilities.
- Nullable/UNKNOWN subsystem models and read-only integration contracts. No invented telemetry, safe actuator state, health verdict, or controller/nozzle inventory.
- Disabled, explicitly unavailable emergency stop and hardware actions. Navigation and persistent local theme settings work; vehicle selection explains that pairing is unavailable.
- Basic role/session policy and app-storage/manifest protections, **not** implemented authentication or production command security.
- A saved UDP peer endpoint and a user-selected BLE GATT notification receiver, both with explicit open/close controls and no background auto-connect. MAVLink sessions select the first valid autopilot HEARTBEAT system/component pair, ignore competing identities, and mark vehicle state degraded after heartbeat timeout. UDP filters its configured peer; BLE notifications are parsed by the same MAVLink decoder.
- Incremental MAVLink 2 framing and CRC/payload validation for HEARTBEAT, GPS_RAW_INT, GLOBAL_POSITION_INT, ATTITUDE, VFR_HUD, SYS_STATUS, BATTERY_STATUS, and STATUSTEXT. Unsigned packets update source-specific state and receive times; signed frames are rejected because no signing-key verifier is provisioned. CRC and fixed-peer filtering are not authentication.
- Read-only health evidence labels MAVLink link, GPS, battery, and autopilot sensor data as not received, reported, or protocol-reported issue. Operational readiness remains **NOT ASSESSED** until a vehicle-specific health profile, hardware limits, expected telemetry rates, and validated subsystem routes are provisioned.
- Map and dashboard draw the current GLOBAL_POSITION_INT coordinate, heading marker, and a bounded track over a MapTiler vector basemap after at least 0.5 m of reported movement. Center and follow controls are functional; a user pan disables following. Track data is cleared at a new telemetry session and is not persisted.
- Drive admission now has a pure fail-closed checklist for operator authentication/permission, provisioned and authenticated vehicle identity, fresh heartbeat/telemetry, approved vehicle safety profile, validated command path, physical emergency stop, tested link-loss failsafe, allowed mode/arming conditions, and active deadman lease. No UI can mark those conditions verified, and no command sender exists yet.
- A pure VESC telemetry reducer accepts decoded measurements only for a provisioned controller inventory, keeps mechanical RPM distinct from electrical RPM, rejects non-finite/out-of-order readings, and removes stale measurements after a profile-defined timeout. It is not connected to a socket, CAN bus, UART, MAVLink stream, or the live vehicle state yet.
- Systems → BLE MAVLink scans advertisements, lets the operator inspect GATT notification characteristics and start a receive-only MAVLink session from a selected characteristic. It does not send vehicle commands or parse VESC telemetry; Bluetooth Classic / SPP is not supported. See the [Phase 13 report](docs/phase-13-report.md).
- Plan supports a named local draft of up to 250 coordinate waypoints, map long-press with confirmation, tap-to-edit numbered route points, manual coordinate entry, list-based edit/reorder/remove, route preview, fit-to-draft, explicit save and bounded GeoJSON/GPX import/export. Imported routes are previewed before replacing the working draft. A separate recovery copy is saved after edits and restored after process recreation; Save draft commits the route and clears recovery atomically. Orange draft markers/line are separate from live vehicle telemetry; the displayed distance is a surface straight-line estimate, not route feasibility or terrain validation. Draft editing and storage work without a vehicle or internet; the basemap still requires network access.

Opening a configured UDP socket starts the receive session and accepts datagrams only from the configured peer. A CRC-valid unsigned HEARTBEAT from an autopilot component establishes liveness; the pinned system/component source gates telemetry updates. Receive timestamps are displayed with measurements; protocol liveness and CRC do not authenticate the sender. No MAVLink commands are sent.

**Do not use this build to operate machinery.** The emergency-stop button cannot command or confirm a physical stop. Use the independent physical safety system.

## Architecture

```text
presentation/    stateless Compose screens/components, navigation, design system
                lifecycle-aware Dashboard/Settings ViewModels
domain/          immutable models, repository contracts, use cases
core/            command outcomes/errors, pure authorization policy
data/            theme/UDP endpoint DataStore, vehicle repository,
                local mission draft DataStore, foreground-owned UDP transport,
                allowlisted MAVLink telemetry session
di/              Hilt bindings
```

Active flow: `repository → Flow/StateFlow → use case/ViewModel → Compose`.
Theme updates use `UI → ViewModel → use case → SettingsRepository → DataStore`.
UI code accesses platform transport only through presentation ViewModels; the domain layer has no Android/Compose dependencies. Subsystem contracts remain separate from their future adapters; a VESC transport is not assumed to be MAVLink. Application vehicle identity is separate from MAVLink system/component IDs. The current repository has one active vehicle state; multi-vehicle selection/session management is deferred.

## Build and run

Keep the existing toolchain: AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0, compile/target SDK 36, min SDK 26, JVM target 17. Use Android Studio's bundled JDK or a compatible installed JDK and an installed Android SDK 36. Set your own `sdk.dir` in ignored `local.properties` if needed.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
# Requires a running Android 26+ emulator or device:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Open the project in Android Studio, sync, select a device and run `app`. The online map requires a MapTiler key in ignored `local.properties`; see [MapLibre and MapTiler setup](docs/maplibre-maptiler.md). The remaining foundation can open without a vehicle or login. Systems → Settings selects Dark, Light or System; the choice survives process restart. The top connection shortcut opens the same settings screen. Units are currently metric only.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint report: `app/build/reports/lint-results-debug.html`.

Runtime dependencies include AndroidX Preferences DataStore 1.1.1, MapLibre Native 13.6.1, and OkHttp 4.12.0 for the app-specific map User-Agent. Test-only Compose UI testing libraries remain aligned with Compose 1.9.0. Room, crypto libraries and new protocol libraries are deliberately not added for unimplemented features.

## Boundaries and next phases

There is no licensed offline-region implementation, bundled telemetry simulator, real actuator control, login/user store, audit persistence, configured hardware-health evaluation, mission protocol or background communication service. The VESC reducer is not a live integration; the Drive safety evaluator is groundwork only, its evidence is not connected to trusted providers, and all drive commands remain disabled. The online map requires MapTiler network access and an operator-provided client key. Raw UDP packet counts are available only while an explicitly configured socket is open; they are not vehicle telemetry or link health.

See [Phase 1 report](docs/phase-1-report.md), [Phase 2 report](docs/phase-2-report.md), [Phase 3 report](docs/phase-3-report.md), [Phase 4 report](docs/phase-4-report.md), [Phase 5 report](docs/phase-5-report.md), [Phase 6 report](docs/phase-6-report.md), [Phase 7 report](docs/phase-7-report.md), [Phase 8 groundwork report](docs/phase-8-report.md), and [Phase 8a BLE discovery report](docs/phase-8a-report.md) for implementation scope and validation. For a repeatable physical-phone test with ArduPilot Rover SITL under WSL2, use the [SITL phone telemetry guide](docs/sitl-phone-telemetry.md). Review [security boundaries](docs/security.md) and [hardware integration gates](docs/hardware-integration.md) before enabling integrations.

The [local mission planning report](docs/phase-11a-report.md), [recovery report](docs/phase-11b-report.md), [GeoJSON exchange report](docs/phase-11c-report.md), [local mission library report](docs/phase-11d-report.md), [route-review report](docs/phase-11e-report.md), [reference-geofence report](docs/phase-11f-report.md), [route-boundary report](docs/phase-11g-report.md), [GPX exchange report](docs/phase-11h-report.md) and [map waypoint editing report](docs/phase-11i-report.md) describe the hardware-independent work advanced while Phases 8-10 await integration facts. In Plan, use **Add coordinates** or long-press a loaded map, confirm each waypoint, tap a numbered orange point to edit it, reorder as needed, and press **Save draft**. Unsaved edits are recovered after app process recreation; they remain marked unsaved until explicitly saved. **Import route file** previews a bounded GeoJSON or GPX route before replacing the working draft. GeoJSON preserves the local outline; GPX is a standard route-point interchange and contains no Mama GCS boundary metadata. **Export GeoJSON** or **Export GPX** writes the current working route through Android's document picker. The device-local reference polygon checks waypoint positions and straight draft segments; it does not model the rover's actual trajectory. **Save to library** stores a separately named copy (up to 25) on this device. Opening a library route asks for confirmation and loads it into the working editor; deleting asks for confirmation. Neither action uploads to a vehicle. **New draft** asks before clearing the editor; it replaces the stored draft only after Save.

1. Foundation — delivered.
2. **Transport — delivered.** Explicit UDP profile, foreground socket ownership, lifecycle/error states, Bluetooth/serial extension contracts and tests.
3. **MAVLink HEARTBEAT — delivered.** MAVLink 2 framing/CRC, explicit session lifecycle, autopilot identity pinning, heartbeat timeout, and conservative unsigned/signing policy.
4. **Telemetry — delivered.** GPS raw fix, global position/kinematics, attitude, Rover VFR_HUD instruments, system status, per-pack and aggregate battery readings, and bounded STATUSTEXT display with source receive ages and invalid-value handling. No health verdicts or controls.
5. **Health evidence and readiness gate — delivered.** Protocol-reported evidence is visible, while readiness is explicitly not assessed without a signed-off vehicle profile and hardware integration data.
6. **Map telemetry and online basemap — delivered.** MapLibre Native, MapTiler Streets vector tiles, live coordinate/heading, bounded current-session track, and center/follow controls. Licensed offline regions remain deferred.
7. **Drive safety gate foundation — delivered.** Deny-by-default admission checklist and visible Control preflight. Trusted identity, signed vehicle identity, approved safety profile, command transport, deadman implementation, command outcomes, and all vehicle movement commands remain unimplemented.
8. **VESC — groundwork and BLE discovery.** Read-only telemetry admission, identity and stale-data policy are implemented. Phase 8a can identify BLE advertisements, but actual controller protocol/transport integration remains blocked until the installed module and physical telemetry path are confirmed.
9. Spray — verified hardware mapping and interlocks.
10. Hydraulic — verified hardware mapping and interlocks.
11. **Missions — local planning, recovery, GeoJSON/GPX exchange, route library, structural and local boundary review, and map waypoint editing delivered (11a-11i).** One persistent working draft plus up to 25 separately stored, uniquely named device-local route copies; coordinate editing, map tap-to-edit, reordering, map preview, recovery for unsaved edits, local route file import/export, warnings for incomplete geometry/duplicate legs, and an optional local polygon that checks waypoint positions and straight coordinate segments. GPX exchanges ordered route points only; GeoJSON carries the optional local outline. This is not a geofence safety service, does not model vehicle trajectory, and is not safety preflight. Vehicle fence transfer and controlled execution remain deferred.
12. Admin — trusted local authentication, users, sessions, configuration and auditing.
13. Production hardening — reliability/security/device and field validation.

Dependencies between phases matter: secure identity, authorization and required safety gates must exist **before** enabling any control, even if full admin UX is scheduled later.
