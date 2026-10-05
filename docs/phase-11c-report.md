# Phase 11c: Local GeoJSON route exchange

Date: 2026-10-05. Scope: import and export device-local coordinate route drafts without connecting to or changing the vehicle.

## Delivered

- Export the current working draft as a GeoJSON `FeatureCollection` with an ordered list of waypoint `Point` features. Coordinates use the GeoJSON order `[longitude, latitude]`.
- Import a GeoJSON file through Android's system document picker. Input is capped at 256,000 characters and 250 waypoints; unsupported collection and geometry types, invalid coordinates, and unknown Mama GCS format versions are rejected.
- Imported route names, waypoint count and straight-line distance are previewed before replacing the working draft. The imported route is still unsaved; the saved route changes only after the operator taps **Save draft**.
- Files are selected through Android's Storage Access Framework. No broad storage permission is required.

## Boundaries

This exchange is a local draft format, not the QGroundControl WPL mission format or a MAVLink mission protocol. It does not imply altitude, speed, terrain clearance, geofence compliance, or vehicle compatibility. Import replaces the current working copy only after confirmation; export serializes that working copy, including unsaved edits.

## Validation

No tests or builds were run for this change.
