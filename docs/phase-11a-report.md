# Phase 11a: Local mission planning

Date: 2026-10-05. This hardware-independent part of the roadmap advances while VESC, spray and hydraulic integration details are pending. It does not mark those phases complete.

## Delivered

- One named, device-local route draft with up to 250 WGS84 coordinate waypoints. This is a local resource bound, not an assumed autopilot waypoint limit.
- Add manually or long-press a loaded map and confirm the coordinate dialog. Edit coordinates, reorder and remove points; invalid or non-finite coordinates cannot enter the draft.
- Orange numbered markers and an ordered line over the existing MapLibre map; Fit draft frames the route. Live vehicle telemetry and its blue track retain their separate sources.
- A spherical surface-distance estimate for straight segments, including date-line crossings. No terrain, obstacle, geofence or kinematic validation is implied.
- Explicit Save draft using a dedicated Preferences DataStore with a versioned, bounded codec. Load/save failures are visible; failed reads do not replace stored data. Unknown format versions and malformed data are rejected.
- Saved coordinates, stable waypoint IDs, order and name survive reopening the app. Activity-level state retains unsaved edits across navigation and rotation, but process death can lose unsaved work; the screen says so.
- New draft requests confirmation before clearing the editor. The previous saved draft remains on storage until the replacement is explicitly saved.
- Wide layouts show the map beside the editor; narrow layouts provide a scrollable editor and map. Coordinate entry and local storage work without an internet or vehicle connection.

## Boundaries

This is one draft, not a mission library or onboard mission. It has no vehicle address, altitude/speed/actuator command, transfer service or execution path. Upload, download, start, pause and resume remain disabled. Maps still require the configured MapTiler source and internet; offline-region downloads remain deferred. Uninstalling/clearing app data removes the local draft, and there is no export/backup workflow yet.

## Implementation references

The map interaction uses the official [MapLibre long-click API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/add-on-map-long-click-listener.html) and [camera bounds API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.camera/-camera-update-factory/new-lat-lng-bounds.html). Existing dependencies and toolchain are retained.

## Validation

- Debug app and instrumentation APK builds passed. All 59 JVM tests passed, including coordinate/inventory bounds, editing/order preservation, date-line distance, exact storage round-trip and corrupt-format rejection.
- Android lint completed with zero errors and 23 warnings.
- Three focused Android 14 emulator tests passed: mission editing/persistence plus two existing command-lock/navigation regressions. The mission test rejects invalid coordinates, adds two points, reorders/edits them, saves, rotates, closes/relaunches the Activity with a new ViewModel, verifies the stored route, deletes a point and saves again. The test restores its previous draft afterward.
- The first mission test run failed because its test helper queried a lazily rendered off-screen waypoint before scrolling; the helper was corrected and the subsequent run passed.
- Portrait/landscape screenshots were captured, but an emulator System UI ANR dialog obscured them. Full visual acceptance remains pending an unobstructed emulator or physical-device run. The automated assertions do not prove native map long-press, Fit draft, or live-follow behavior; those interactions still need a device check. No vehicle transfer or field movement test is claimed.
