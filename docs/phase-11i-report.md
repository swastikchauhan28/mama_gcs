# Phase 11i: Map-based waypoint editing

Date: 2026-10-06. Scope: make locally planned route points directly editable from the Plan map without moving a marker or changing the draft implicitly.

## Delivered

- Tapping near a numbered orange waypoint selects the nearest point within a 36 dp touch radius and opens the existing coordinate editor prefilled with that waypoint.
- The operator must confirm with **Apply**; Cancel leaves the route unchanged. The existing unsaved/recovery and explicit-save behavior is preserved.
- Taps away from route points remain normal map taps, and long-press remains the separate add-waypoint gesture.
- Added pure hit-test coverage for nearest-point selection, touch-radius misses, empty candidates and invalid inputs.

## Boundaries

Selection is a screen-space convenience for the local Plan draft only. It does not move or command a vehicle, change a saved route until Save draft, alter the live map, or imply route feasibility. A waypoint hidden by another overlay may still be easier to edit from the numbered list.

## Validation

`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin` passed. The on-device map tap interaction was not executed because `adb` and an attached emulator/phone are unavailable in this environment. MapLibre's Android API defines geographic-to-screen projection and map-click listeners for this hit test; see the [MapLibre `toScreenLocation` reference](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-projection/to-screen-location.html) and [map click listener reference](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/-on-map-click-listener/index.html).
