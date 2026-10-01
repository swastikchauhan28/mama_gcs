# Phase 6 live position and track view

Date: 2026-10-01. Scope: display supported live position, heading, and a short in-memory track from `GLOBAL_POSITION_INT`. No command or mission messages are sent.

## Implemented

- The Map screen and dashboard map panel render the MapTiler Streets vector style through MapLibre Native using valid geographic coordinates from the existing MAVLink telemetry state.
- A green vehicle marker and yellow direction line use the current coordinate and reported heading; the screen also shows latitude, longitude, heading, and the number of retained track points.
- Track points are recorded only when a valid coordinate moves at least 0.5 m from the previous retained point. The in-memory track is bounded to 2,000 points and resets when a new receive session starts.
- The vehicle marker is hidden when the current position is unavailable. A previously received track can remain visible while the current session is connected, but the normal disconnected display projection clears it.
- Center and follow controls are functional. Panning disables follow mode, while Follow resumes recentering on telemetry updates.
- A dedicated, case-sensitive `MAMA-GCS-Android` HTTP User-Agent supports restriction of the client-side MapTiler key. Attribution remains enabled.

## Boundaries and next inputs

This delivers telemetry position, heading, track display, and an online geographic map. It does not deliver offline regions. Offline packaging requires explicit provider/source permission, an update strategy, storage limits, and field validation before the control is enabled.

Tracks are in-memory session data. App restart or opening a new receive session clears the route. No location export, persistent history, map matching, route planning, or offline map download is included.

## Validation

`:app:compileDebugKotlin` passed after the MapLibre integration. Physical-device validation is still required for MapTiler network loading, touch gestures, rendering, and the established Rover SITL-to-phone telemetry path. Moving the SITL vehicle is needed to see multiple track points.
