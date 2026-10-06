# Phase 11e: Local route structure review

Date: 2026-10-06. Scope: add a deterministic, device-local structural review of the current mission draft without claiming vehicle compatibility or safety.

## Delivered

- Plan reports whether the draft has at least two waypoints, which is the minimum to describe a route shape.
- Consecutive waypoints with exactly identical WGS84 coordinates are listed as zero-length legs using one-based leg numbers.
- The review updates immediately as the working draft is edited; it is read-only and does not modify coordinates or block local save/export.
- The screen explicitly states that terrain, obstacles, boundaries, vehicle limits and drive safety are not evaluated.

## Boundaries

This is a structural review of a local draft, not a geofence, path planner, mission preflight, vehicle compatibility check, safety approval, or permission to operate. It has no vehicle-specific tolerances or inferred hardware behavior. Upload, download, mission execution and rover commands remain unavailable.

## Validation

Pure unit tests cover empty and one-point drafts, defined route geometry, and multiple zero-length legs. The emulator mission-planning test verifies the incomplete state and transition to defined route geometry after two distinct points are added. `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest` (14 instrumentation tests), `:app:lintDebug`, and `:app:assembleDebug` all passed on 2026-10-06.
