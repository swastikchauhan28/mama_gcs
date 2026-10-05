# Phase 11d: Local mission library

Date: 2026-10-05. Scope: keep separately named route copies in app-local storage so operators can prepare and reuse routes without a connected vehicle.

## Delivered

- Added a versioned, bounded library store separate from the active draft and its unsaved recovery copy.
- The library supports up to 25 routes. Names must be unique without regard to case; a duplicate or full-library save is rejected without replacing an existing entry.
- Plan can save a copy of the current working route, open a library route into the working editor after explicit confirmation, and delete a library entry after explicit confirmation.
- Opening a route does not update the persistent active draft; it remains a working change until the operator chooses **Save draft**. Recovery continues to protect unsaved editor changes.

## Boundaries

This library is private app data on one device. It is not cloud sync, backup, a QGroundControl WPL file, or an onboard mission. It does not add mission protocol handling, geofence checks, vehicle upload/download, arming, or mission execution. Clearing app data or uninstalling removes the library. GeoJSON remains the portable route exchange format.

## Validation

No build, lint, or test command was run for this phase; the implementation remains unverified by automated checks.
