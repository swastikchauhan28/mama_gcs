# Phase 11f: Local reference geofence

Date: 2026-10-06. Scope: add an optional, device-local polygon as a visual and waypoint-position review aid for mission drafts.

## Delivered

- Mission drafts can store up to 64 WGS84 outline vertices. The Plan screen supports adding coordinate vertices, removing individual vertices, or clearing the outline.
- MapLibre draws outlines with at least three distinct coordinates as a translucent amber polygon and fits the camera to route and outline points.
- Route review reports waypoint indices outside a usable outline. Points on the outline are treated as inside. Without at least three distinct outline coordinates, containment is reported as not checked.
- Local binary draft storage was versioned to v2 and continues to read v1 drafts with no outline. GeoJSON exchange v2 includes a closed Polygon feature tagged `role: keep-in-fence`; GeoJSON v1 remains readable.
- Working draft recovery, save, library copies and route import/export carry the local outline as part of the draft.

## Boundaries

The outline is an operator-supplied planning reference only. The containment calculation is a planar check for compact local polygons and reports waypoint positions, not the path between points. Polygon self-intersection, terrain, obstacles, vehicle position, coordinate accuracy, onboard configuration and vehicle compatibility are not validated. The outline is never uploaded or enforced. It is not an autopilot geofence, safety approval, or authorization to operate machinery. Vehicle fence transfer and mission execution remain disabled.

## Validation

Pure unit tests cover inside/outside/boundary classification, insufficient/degenerate outlines, and persistence encoding/migration. Emulator tests check the editable outline and route review panel, persistence, and GeoJSON round-trip. On 2026-10-06, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest` (14 instrumentation tests), `:app:lintDebug` and `:app:assembleDebug` all passed.
