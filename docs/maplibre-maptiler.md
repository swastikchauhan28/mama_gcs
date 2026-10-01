# MapLibre Native and MapTiler setup

The Android app renders the online geographic basemap with MapLibre Native 13.6.1 and the MapTiler Streets vector style. MAVLink `GLOBAL_POSITION_INT` data is rendered separately as runtime GeoJSON sources for the vehicle marker, heading line, and bounded current-session track.

## Create and restrict the MapTiler key

Create a dedicated MapTiler key with these settings:

- Name: `MAMA GCS Android`
- Description: `MapTiler vector basemap for the MAMA GCS Android ground-control application using MapLibre Native.`
- Allowed User-Agent header: `MAMA-GCS-Android`
- Allowed HTTP Origins: leave empty

`MamaGcsApplication` configures MapLibre's shared OkHttp client to send `MAMA-GCS-Android/<version>` for every map-resource request. The restriction is case-sensitive. HTTP-origin restrictions are not used because this is a native Android client rather than a browser.

## Configure a development machine

Add the following entry to the root `local.properties` file:

```properties
MAPTILER_API_KEY=replace_with_your_key
```

`local.properties` is ignored by Git. Do not put the key in Kotlin, XML, screenshots, issues, or committed Gradle properties. The build reads `MAPTILER_API_KEY` from the process environment first and falls back to `local.properties`, so CI should provide a protected environment secret.

The key is still present in the compiled Android application and must be treated as a client-side credential. Keep the User-Agent restriction enabled, use a key dedicated to this app/platform, monitor usage, and rotate it if it is exposed.

## Build and validate

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --offline
.\gradlew.bat :app:assembleDebug --offline
```

On a physical phone with internet access:

1. Open the Map page and confirm the MapTiler Streets basemap appears with attribution visible.
2. Open the UDP receive session and start Rover SITL telemetry.
3. Confirm the map centers on the first fresh vehicle coordinate at zoom 17.
4. Move the rover and confirm that the blue track, green marker, and yellow heading line update.
5. Drag the map and confirm that automatic following stops; tap **Follow** to resume or **Center** for a one-time recenter.
6. Stop the telemetry session and confirm the marker, heading, and track are no longer presented as live.

If the app remains on `LOADING MAP`, verify internet access, the API key, the exact `MAMA-GCS-Android` User-Agent restriction, and MapTiler account usage limits. A missing local key is reported in the map UI instead of crashing the app.

## Offline boundary

Offline-region downloads are intentionally not enabled. MapLibre supports offline regions, but the selected tile/style license must explicitly permit the required download and field-use behavior. Do not bulk-download MapTiler Cloud or public OpenStreetMap tiles unless the applicable agreement permits it. Use an offline-enabled MapTiler agreement or a licensed self-hosted source before implementing that control.
