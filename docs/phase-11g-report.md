# Phase 11g: Route boundary review

Date: 2026-10-06. Scope: extend local reference-outline review to the straight coordinate segments between consecutive draft waypoints.

## Delivered

- Outline validation now reports too few/repeated vertices, zero-area geometry, and self-intersection; invalid outlines are not used for containment results.
- The local planar geometry check unwraps longitude around the outline and evaluates waypoint positions plus every straight draft segment. Segment checks split at outline intersections, so a leg leaving a concave polygon is reported even when both endpoints are inside.
- Plan separately lists waypoint indices and leg indices outside the local outline. The outline itself remains device-local and editable.

## Boundaries

The calculation assumes a compact, simple local polygon and uses a planar projection; it is not a geodesic route or a vehicle trajectory. It does not model steering radius, navigation curves, obstacles, terrain, GNSS error, vehicle/fence margins, or rover-specific fence behavior. A result of no intersections is not a safe-to-drive finding or mission approval. The outline is not uploaded to or enforced by the vehicle; all mission and actuator commands remain disabled.

## Validation

Pure tests cover boundary inclusion, outside points, concave-boundary exits with inside endpoints, malformed/self-crossing outlines, and prior route/draft behavior. Emulator tests cover editing and persistence of the local outline and route review. On 2026-10-06, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest` (14 instrumentation tests), `:app:lintDebug` and `:app:assembleDebug` all passed. Physical vehicle and trajectory validation were not performed.
