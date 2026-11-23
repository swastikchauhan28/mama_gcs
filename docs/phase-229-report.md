# Phase 229 — optional Google Maps provider

## Goal and scope

Add Google streets, satellite and hybrid choices to the existing local Maps setting while preserving MapLibre choices. This changes only map display. It does not alter telemetry, stored missions, mission upload or vehicle commands. The user supplied a Google Maps Android key in ignored `local.properties`; this report does not contain the key.

## Implementation and data flow

`MapSettingsScreen` writes a stable `BasemapStyle` enum name through the existing settings repository. `VehicleMap` selects `GoogleVehicleMap` for Google choices and the existing MapLibre renderer for the other choices. Google Maps SDK for Android 20.1.0 obtains tiles using the manifest key from local build configuration. Vehicle and home positions pass existing freshness checks; route, fence, track and coverage previews are local display overlays. A bounded 512 × 512 ground image renders sampled gap centers rather than creating thousands of markers. These display structures remain volatile. Map settings persist through the existing DataStore preference. The Google map performs no mission or rover write.

The app shows a missing-key or missing-Play-services message, and a 20-second load timeout offers retry. MapLibre stays selectable. Google tiles need network, enabled billing, a restricted Android key and compatible Play services. Imagery is geographic context and cannot establish field coverage or safe rover operation. The release signing certificate restriction remains to be configured and checked by the project owner.

## Verification

- `:app:assembleDebugAndroidTest` passed after correcting the new test's screen-coordinate type.
- Initial emulator acceptance failed because the old Play services Maps renderer could not resolve `org.apache.http.ProtocolVersion` (`app/build/acceptance-20261123-140147-f4db149a/`). Added Google's documented optional `org.apache.http.legacy` manifest library. The next run rendered the map but the test read `GoogleMap.mapType` off the main thread (`acceptance-20261123-140355-5ba0d0fa/`); the test now reads it on the main thread.
- Synthetic Google map acceptance passed, `OK (1 test)` with a healthy Android gate: `app/build/acceptance-20261123-140621-6da99ccd/`. It checked a loaded map, Fit draft, selection of waypoint 2 and Streets-to-Satellite switching. After a bitmap-helper lint correction, the same test passed against the final APK with a healthy Android gate at `app/build/acceptance-20261123-143033-154eda2e/`. `app/build/phase229-google-map-final.png` was visually inspected: Google tiles, two numbered route markers, field outline, cyan pass, legend and controls were present. The single gap sample was not independently established visually at this screenshot scale.
- Existing basemap settings and a saved Google basemap across Activity recreation passed, `OK (3 tests)` with a healthy Android gate: `app/build/acceptance-20261123-141037-16f4f08a/`. The persistence test restores the previous preference.

- Final `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed. The JVM report contains 533 tests and zero failures; lint has zero errors and 27 existing warnings. The final bitmap calls use the existing AndroidX KTX helper, removing the two warnings introduced by the first draft.

No physical phone, Cube or field test was run. Google Cloud key restrictions and release signing remain owner checks. No commit or push is authorized by this phase approval.

## Review and recovery

Review the provider switch, key placeholder, new network dependency, Google overlay bounds, preference compatibility, and the unchanged command path. Previous revision `ee67b2dd67fb6b7cccb962abe416effd69bbd966` is a recovery reference, not a certified release. Reverting this scoped change restores the prior MapLibre-only choices; previously saved Google enum names fall back to Streets in the old build. Do not replace or clear operator data to test recovery.
