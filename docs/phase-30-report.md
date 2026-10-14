# Phase 30 — Saved online basemap choices

## Delivered

- Application settings → Maps → Map appearance offers Streets, Satellite and Satellite with labels, with descriptions, accessible radio selection, loading/saving states and save-error feedback.
- A stable enum identifier is stored in the existing local Preferences DataStore. Existing installs, absent values and unknown identifiers default to Streets. This is a display preference available without a rover connection or admin configuration permission; no arbitrary URL or API key is stored in the preference.
- Operate, Map and Plan inherit one shared reactive selection. The existing native style-reload path reinstalls vehicle/track/heading and draft layers. Failed loading retains the existing error/retry workflow rather than silently claiming map availability.
- Changing theme now preserves the other in-memory preferences instead of rebuilding a default preferences object. Tests cover preserving the endpoint/map/theme, loading and concurrent-save guards, failed saves and retry.
- Only the existing HTTPS MapTiler host is used. Style IDs are `streets-v4`, `satellite-v4` and `hybrid-v4`; the existing build-time client key is query-encoded and is not exposed in UI errors. Source references: [MapTiler Maps API](https://docs.maptiler.com/cloud/api/maps/) and [official built-in style catalog](https://docs.maptiler.com/sdk-js/api-reference/variables/Externals.MAP_STYLE_CONFIG/).

## Boundaries

All choices require internet and the existing MapTiler account/key entitlement. Satellite imagery is not live, may be outdated or offset, and cannot establish obstacle clearance or a safe route. Offline region downloads, custom providers, live video and terrain/obstacle evaluation are not implemented. Attribution remains enabled in MapLibre. No movement, arming, mission, parameter, safety-gate or hardware-record changes are included.

## Validation

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 11m 8s**. Final APK up-to-date verification also passed in 21 seconds.
- **298 JVM tests passed**, zero failures/errors/skips, including nine new settings/configuration cases. Lint: **0 errors, 30 existing warnings**.
- Final-source `:app:testDebugUnitTest :app:lintDebug` verification also completed successfully in 1m 14s with a 1 GB single-use Gradle daemon and the emulator stopped. JVM results were up to date; Android-test lint analysis reran after the frame-callback correction.
- Final targeted emulator batch: **10/10 passed in 73.642 seconds**, covering settings states, preference persistence, local style reload/error recovery and online map interactions. Evidence: `app/build/acceptance-20261014-104202-d39cd27e/instrumentation.txt`. Local fixtures and actual online rendering are separate checks; fixture success alone does not establish provider availability.
- The 13 October run was interrupted during its first case and has no pass result. On 14 October, six cases passed before the new local-style switch fixture crashed in the test-only `querySourceFeatures` call (`MapRenderer::actor`, null renderer). The helper now waits for a native render callback before querying source features; no production source-query call was involved. Corrected-test rebuild and lint passed in 1m 54s.
- An interim online run reported three passing interaction tests (`app/build/acceptance-20261014-102517-81fd8510/instrumentation.txt`), but SDK snapshot images were blank/stale. This is **not accepted as visual basemap verification**. The test now waits for subsequent render callbacks, captures Android compositor output, rejects a blank centre region, and still requires manual visual review. Original failing batch: `app/build/acceptance-20261014-102256-2372c7a2/instrumentation.txt`.
- An intermediate full batch had seven passes and three callback timeouts (`app/build/acceptance-20261014-103702-c36dea79/instrumentation.txt`). The helpers now use `OnDidFinishRenderingFrameListener`, which observes repaint frames, instead of the map-completion listener. The final test APK rebuilt successfully in 1m 18s before the successful ten-test run. These synchronization changes affect tests only.
- Final compositor captures were manually reviewed: real Satellite imagery, Hybrid imagery with road labels, Streets vector tiles, draft route overlays, attribution and light/dark map settings. Evidence: `app/build/qa-repair2/phase30-SATELLITE.png`, `phase30-HYBRID.png`, `phase30-STREETS.png`, `phase30-maps-light.png` and `phase30-maps-dark.png` in the same directory. These are landscape emulator fixtures with synthetic positions, not proof of coverage at the rover's operating site.
- Low-memory emulator startup freezes occurred before the final run. After the user freed memory, the startup System UI dialog was cleared before testing; no dialogs were dismissed during the passing test batch. The emulator was shut down afterwards.
- This was a targeted regression run, not the entire Android test suite. Physical-phone, portrait/enlarged-text and rover/Bluetooth checks remain outside this phase's validation. No vehicle commands were sent. `git diff --check` passed (only repository line-ending conversion warnings).

## Manual acceptance

1. Open Menu → Application settings → Maps. Select each map appearance and open Map; verify the chosen geographic background and attribution, not only the saved radio selection.
2. Visit Operate and Plan; confirm the same choice is used and existing route points remain visible. Do not connect or command hardware for this check.
3. Restart the app and change the theme. The selected basemap and saved UDP endpoint should remain unchanged.
4. With internet unavailable, confirm loading failures are visible and Retry map can be used when service returns. Cached tiles are not guaranteed offline coverage.
5. Check portrait/landscape, light/dark and enlarged text on the physical phone. Imagery coverage/age and provider account restrictions require separate verification for the actual operating area.
