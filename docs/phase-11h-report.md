# Phase 11h: GPX route-file exchange

Date: 2026-10-06. Scope: add a standard route-point file option to the existing device-local mission draft import/export workflow.

## Delivered

- Plan can import GPX 1.1 through Android's document picker and preview the route before replacing the working draft.
- Plan can export the current ordered waypoint list as a GPX 1.1 route.
- Import is bounded to the existing 256,000-character file limit and 250 waypoints. It accepts one `<rte>` route with ordered `<rtept>` coordinates, validates WGS84 ranges, rejects document type declarations, and does not reinterpret standalone waypoints or track logs as an executable route.
- GeoJSON remains supported and retains the optional Mama GCS local outline. GPX does not represent or export that outline, and the UI says so next to the file actions.
- Both formats remain local file exchange: import confirmation changes only the working draft until explicitly saved. No mission upload, download, or execution is added.

## Boundaries

GPX interoperability is limited to one GPX 1.1 route and its ordered route points. Altitude, speed, vehicle-specific commands, route fences and track logs are not imported or inferred. A valid GPX file does not establish vehicle compatibility or route safety.

## Validation

`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin` passed. Instrumentation tests cover ordered coordinate/name round-trip, explicit omission of local outline data, rejection of document type declarations, and rejection of a GPX file without a route; they were compiled but not run because no `adb` device is available in this environment. Physical mapping-tool interoperability has not been tested.
