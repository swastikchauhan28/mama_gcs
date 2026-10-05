# Operator workspace and requirements coverage

Date: 2026-10-02. Scope: practical operator UI over the existing receive-only application, not actuator control.

## Operating model

The layout uses established ground-station patterns: map-first operation, compact instruments, persistent link access, and separate planning/setup. References: official [QGroundControl UI overview](https://docs.qgroundcontrol.com/master/en/qgc-user-guide/getting_started/ui_overview.html) and [Fly View](https://docs.qgroundcontrol.com/Stable_V5.0/en/qgc-user-guide/fly_view/hud.html). This is an original Compose layout; no QGC source, assets or flight-control assumptions are copied.

- **Operate:** bounded live map with coordinates, mode and arming overlay; speed, heading, GPS and battery instruments; direct equipment/health/telemetry access. Long telemetry lists no longer displace the map.
- **Map:** expanded geographic workspace, live heading/trail, zoom, center and follow. The MapLibre compass provides orientation reset. Attribution remains visible.
- **Drive:** commands grouped together but disabled; concise lock reason, expandable safety evidence and measured vehicle state. Operators cannot self-certify safety evidence.
- **Plan:** local draft editor with confirmed map long-press or manual coordinate entry, editing/reordering/removal, explicit local save and an orange route preview. Upload/download and execution remain unavailable; the draft is separate from unknown onboard mission state.
- **Systems:** telemetry, equipment, health, diagnostics, administration and local settings.

All destinations retain the connection shortcut and disabled emergency-stop availability indicator. This indicator is **not a physical emergency stop** and cannot stop the vehicle. Wide windows use a full-height navigation rail and instrument dock; phones use bottom navigation and a two-by-two instrument grid with horizontally scrollable equipment shortcuts. Short, narrow windows use a scrolling instrument row to preserve map space. Detailed screens scroll independently. Actions have at least 48 dp targets. Text accompanies status colors; dark, light and system appearance are retained. A received MAVLink system ID is labelled observed, not securely paired.

## Requirements matrix

### Fullscreen display

The activity uses Android edge-to-edge immersive mode: status and navigation bars are hidden, with transient system bars available through an edge swipe. Fullscreen is reapplied when the activity regains window focus. Camera-cutout safe insets protect touch targets; keyboard insets keep connection forms accessible. Portrait/landscape are not locked. The landscape navigation rail is 72 dp and the instrument dock is 224 dp, reducing space taken away from the map without reducing action targets below 48 dp. Physical device bezels and Android multi-window boundaries are outside the app's drawable area.

Implementation follows Android's [immersive-mode guidance](https://developer.android.com/develop/ui/views/layout/immersive) and [display-cutout guidance](https://developer.android.com/develop/ui/views/layout/display-cutout).

Android may show its standard first-use **Viewing full screen** explanation. Tap **Got it** to dismiss it; the app does not suppress this system guidance. The captured landscape frame includes this overlay. A physical-device check should acknowledge it before checking touch interactions and edge-swipe access to Android navigation.

Fullscreen follow-up validation: debug and test APK builds passed; 49 JVM tests passed; lint reported zero errors. Five focused emulator tests passed after correcting flat primary-navigation restoration: three real-window tests cover hidden bars after launch/recreation, Settings-to-Operate navigation and actual landscape rotation; two existing tests cover navigation, theme switching and disabled commands. The landscape screenshot is saved locally at `app/build/fullscreen-landscape.png`. Tests retry transient unavailable screenshots and scroll to theme controls before clicking. The prior full-suite run exposed the navigation restoration issue; that failure was fixed and its focused regression test passed. Camera-cutout hardware and IME behavior still require physical-device acceptance.

### Capability coverage

UI coverage does not mean the underlying capability is complete. This matrix maps the supplied agricultural Rover requirements to current screens and outstanding work. Hardware, security, timing and field-validation requirements cannot be fulfilled by visual design alone.

| Requirement group | UI location / current behavior | Remaining operational work |
| --- | --- | --- |
| Rover mode, arming, speed, heading, position, altitude | Operate summary; Telemetry details; seven-decimal coordinates | Hardware comparison and per-source freshness policy validation |
| GPS fix, satellites, HDOP, battery packs, voltage/current/temperature, attitude, SYS_STATUS | Telemetry/Health, source receive ages, missing values UNKNOWN | Profile-specific thresholds, rate checks, validated readiness and alerts |
| Map, heading and route history | Operate/Map; MapLibre, MapTiler Streets, session trail, zoom/center/follow, generic load-error retry | Home marker, planned routes/geofences, source selection, licensed offline-region downloads/storage |
| Link setup and visibility | Persistent shortcut; saved UDP endpoint and explicit open/close in Settings | Bluetooth/USB adapters, permissions, reconnect/background policy, multi-vehicle sessions |
| Forward/reverse, stop, arm/disarm, mode and speed | Drive disabled actions and safety explanation | Authenticated/authorized command boundary, deadman, mode policy, limits, ACK/result confirmation and failsafe tests |
| Emergency stop on every screen | Persistent UNAVAILABLE state, no command callback | Independent physical stop and verified vehicle-side behavior; app commands cannot substitute for it |
| Arbitrary VESC count, RPM vs ERPM, currents, voltage, temperatures, duty cycle, faults | Motors renders reported controllers individually; unknown inventory explicit | Controller models, pole counts, CAN/UART route, decoder and freshness |
| Spray pump, nozzles, flow, pressure and faults | Spray includes pump/system fault and reported nozzle rows; actions disabled | Mappings, sensors, command/feedback confirmation and spraying interlocks |
| Hydraulic enable, pump/valve, pressure, temperature and faults | Hydraulic evidence panel; actions disabled | Interface, mechanical interlocks and command/feedback confirmation |
| Mission create/edit/reorder, transfer, execution/progress | Plan edits one working draft, previews numbered orange waypoints/route, offers GeoJSON import/export and a separate local library of up to 25 named route copies; transfer/start/pause/resume disabled; onboard state unknown | Protocol/retries, geofences, progress and safe execution |
| Login, roles/users, pairing, parameters/max speed | Admin denies access; observed system separate from paired identity | Offline auth/session store, authorization, identity provisioning, signing/replay protection, parameters and audits |
| Diagnostics, messages, counters and logs | Actual RX/TX counts, packet age, bounded STATUSTEXT and telemetry evidence | Parser-error/reconnect instrumentation, durable logs/export and security audit |
| Offline behavior | Local preferences, saved mission draft/library and UDP telemetry need no cloud login; manual waypoint editing works without a basemap | Licensed tile regions and offline auth storage; map cache is not an offline guarantee |
| Phone/tablet, orientation, touch/accessibility, themes | Responsive rail/bottom bar, independent scrolling, labelled 48 dp controls, blue industrial theme | TalkBack, sunlight/glove and field acceptance; no outdoor certification claimed |
| Architecture, security, tests and data integrity | Presentation-state UI; no raw MAVLink commands; connection loss clears displayed values; no demo data | Production security, reliability, per-source freshness and hardware validation |

## Current field workflow

1. Use the top connection shortcut to configure and explicitly open the known UDP peer.
2. Return to Operate; confirm heartbeat, observed system identity and measurements. CONNECTED is protocol liveness, not authorization or readiness.
3. Inspect Telemetry/Health for GPS, battery and ages; Diagnostics for packet counts/messages.
4. Use Map for center/follow/zoom. Panning disables follow; Follow restores it. Map-source failures do not disable telemetry inspection.
5. Inspect equipment separately. Unknown is never interpreted as OFF, stopped or safe.
6. Do not attempt vehicle movement through this version: every command remains disabled. The physical safety system is required independently.

## Acceptance checks

- Disconnected/degraded sessions must not retain live measurements or markers.
- Connected telemetry alone must not enable drive, spray, hydraulic, mission or stop commands.
- Navigation, connection shortcut and stop availability remain reachable in phone/wide layouts.
- Long details scroll without moving the operating map offscreen.
- Map errors never expose provider-key URLs; retry never sends vehicle commands.
- Diagnostics shows actual transport counters rather than static placeholders.

## Validation evidence

- Debug APK and instrumentation APK built successfully; 49 JVM tests passed.
- Lint: zero errors, 25 warnings (dependency/version notices and the existing SettingsScreen modifier-parameter warning).
- Android 14 emulator: all 7 instrumentation tests passed at 393 dp phone width, including real DataStore theme persistence and fixture-only connected-state command locking.
- The three operating/navigation safety tests passed at 807 dp landscape width; both dashboard and connected-state tests also passed at 130% font scale. Emulator size/font scale were restored afterward.
- Screenshots reviewed for phone, landscape, Drive and large-text layouts. Basemap rendering was also observed in the settled running app; the early fixture captures can precede native tile rendering.
- Final opaque map-button colors and single-line navigation labels were build/unit/lint checked after the emulator runs; they change contrast/text layout only, not navigation or command behavior.
- Screenshots are local ignored build artifacts under `app/build/ui-qa-*`. Connected-state screenshots use test fixtures, not live vehicle data.

No live vehicle movement, physical safety, TalkBack/glove/sunlight acceptance or SITL reconnection test is claimed by this UI change. Map retry/error paths compile but provider-failure recovery has not been end-to-end fault-injected. Physical-vehicle acceptance remains outstanding.
