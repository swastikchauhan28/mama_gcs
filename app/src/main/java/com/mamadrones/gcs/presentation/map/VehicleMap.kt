package com.mamadrones.gcs.presentation.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mamadrones.gcs.domain.model.GeoTrackPoint
import com.mamadrones.gcs.domain.model.VehicleState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.sin

private const val VEHICLE_SOURCE_ID = "mama-vehicle-source"
private const val TRACK_SOURCE_ID = "mama-track-source"
private const val HEADING_SOURCE_ID = "mama-heading-source"

private const val VEHICLE_LAYER_ID = "mama-vehicle-layer"
private const val TRACK_LAYER_ID = "mama-track-layer"
private const val HEADING_LAYER_ID = "mama-heading-layer"

private const val DEFAULT_MAP_ZOOM = 17.0
private const val CAMERA_ANIMATION_MILLIS = 350
private const val HEADING_LINE_LENGTH_METERS = 10.0

/**
 * Native MapLibre map hosted by Compose. The supplied state must already be projected through the
 * presentation freshness policy so stale/disconnected coordinates are not displayed as live.
 */
@Composable
fun VehicleMap(
    state: VehicleState,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    val styleUrl = MapStyleConfig.mapTilerStyleUrlOrNull

    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var style by remember(mapView) { mutableStateOf<Style?>(null) }
    var cameraInitialized by remember(mapView) { mutableStateOf(false) }
    var followVehicle by remember(mapView) { mutableStateOf(true) }

    val moveListener = remember(mapView) {
        object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                followVehicle = false
            }

            override fun onMove(detector: MoveGestureDetector) = Unit

            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
        }
    }

    DisposableEffect(mapView, styleUrl, moveListener) {
        var disposed = false
        var attachedMap: MapLibreMap? = null

        if (styleUrl != null) {
            mapView.getMapAsync { loadedMap ->
                if (disposed) return@getMapAsync

                attachedMap = loadedMap
                map = loadedMap
                loadedMap.uiSettings.apply {
                    isAttributionEnabled = true
                    isLogoEnabled = true
                    isCompassEnabled = true
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                }
                loadedMap.addOnMoveListener(moveListener)
                loadedMap.setStyle(styleUrl) { loadedStyle ->
                    if (!disposed) {
                        installVehicleLayers(loadedStyle)
                        style = loadedStyle
                    }
                }
            }
        }

        onDispose {
            disposed = true
            attachedMap?.removeOnMoveListener(moveListener)
            style = null
            map = null
        }
    }

    val latitude = state.position.latitude
    val longitude = state.position.longitude
    val hasPosition = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
    val vehicleTarget = if (hasPosition) LatLng(latitude!!, longitude!!) else null

    LaunchedEffect(
        style,
        latitude,
        longitude,
        state.positionTrack,
        state.headingDegrees,
    ) {
        val loadedStyle = style ?: return@LaunchedEffect
        val target = vehicleTarget

        if (target == null) {
            clearVehicleSources(loadedStyle)
            return@LaunchedEffect
        }

        updateVehicleSources(
            style = loadedStyle,
            latitude = target.latitude,
            longitude = target.longitude,
            headingDegrees = state.headingDegrees,
            track = state.positionTrack,
        )

        val loadedMap = map ?: return@LaunchedEffect
        if (!cameraInitialized) {
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(DEFAULT_MAP_ZOOM)
                .bearing(0.0)
                .tilt(0.0)
                .build()
            cameraInitialized = true
        } else if (followVehicle) {
            loadedMap.animateCamera(
                CameraUpdateFactory.newLatLng(target),
                CAMERA_ANIMATION_MILLIS,
            )
        }
    }

    fun centerOnVehicle(enableFollow: Boolean) {
        val loadedMap = map ?: return
        val target = vehicleTarget ?: return
        followVehicle = enableFollow
        loadedMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, DEFAULT_MAP_ZOOM),
            CAMERA_ANIMATION_MILLIS,
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            styleUrl == null -> MapMessage(
                title = "MAP KEY REQUIRED",
                detail = "Add MAPTILER_API_KEY to the ignored local.properties file.",
                modifier = Modifier.align(Alignment.Center),
            )

            style == null -> MapMessage(
                title = "LOADING MAP",
                detail = "Connecting to the configured MapTiler vector style.",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FilledTonalButton(
                onClick = { centerOnVehicle(enableFollow = false) },
                enabled = vehicleTarget != null && map != null,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Center")
            }
            FilledTonalButton(
                onClick = { centerOnVehicle(enableFollow = true) },
                enabled = vehicleTarget != null && map != null,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(if (followVehicle) "Following" else "Follow")
            }
        }
    }
}

@Composable
private fun MapMessage(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var destroyed = false

        fun start() {
            if (!started && !destroyed) {
                mapView.onStart()
                started = true
            }
        }

        fun resume() {
            if (!resumed && !destroyed) {
                start()
                mapView.onResume()
                resumed = true
            }
        }

        fun pause() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stop() {
            if (started && !destroyed) {
                pause()
                mapView.onStop()
                started = false
            }
        }

        fun destroy() {
            if (!destroyed) {
                stop()
                mapView.onDestroy()
                destroyed = true
            }
        }

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            destroy()
        }
    }

    return mapView
}

private fun installVehicleLayers(style: Style) {
    if (style.getSource(TRACK_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACK_SOURCE_ID, emptyFeatureCollection()))
    }
    if (style.getSource(HEADING_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(HEADING_SOURCE_ID, emptyFeatureCollection()))
    }
    if (style.getSource(VEHICLE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(VEHICLE_SOURCE_ID, emptyFeatureCollection()))
    }

    if (style.getLayer(TRACK_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                lineColor(AndroidColor.rgb(58, 166, 255)),
                lineWidth(5f),
                lineOpacity(0.85f),
            ),
        )
    }
    if (style.getLayer(HEADING_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(HEADING_LAYER_ID, HEADING_SOURCE_ID).withProperties(
                lineColor(AndroidColor.rgb(255, 193, 7)),
                lineWidth(4f),
                lineOpacity(1f),
            ),
        )
    }
    if (style.getLayer(VEHICLE_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID).withProperties(
                circleColor(AndroidColor.rgb(48, 209, 88)),
                circleRadius(9f),
                circleStrokeColor(AndroidColor.WHITE),
                circleStrokeWidth(3f),
            ),
        )
    }
}

private fun updateVehicleSources(
    style: Style,
    latitude: Double,
    longitude: Double,
    headingDegrees: Double?,
    track: List<GeoTrackPoint>,
) {
    style.getSourceAs<GeoJsonSource>(VEHICLE_SOURCE_ID)
        ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)))
    updateTrackSource(style, track)
    updateHeadingSource(style, latitude, longitude, headingDegrees)
}

private fun updateTrackSource(style: Style, track: List<GeoTrackPoint>) {
    val source = style.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID) ?: return
    val points = track
        .filter { point ->
            point.latitude.isFinite() && point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        }
        .map { point -> Point.fromLngLat(point.longitude, point.latitude) }

    if (points.size >= 2) {
        source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
    } else {
        source.setGeoJson(emptyFeatureCollection())
    }
}

private fun updateHeadingSource(
    style: Style,
    latitude: Double,
    longitude: Double,
    headingDegrees: Double?,
) {
    val source = style.getSourceAs<GeoJsonSource>(HEADING_SOURCE_ID) ?: return
    if (headingDegrees == null || !headingDegrees.isFinite()) {
        source.setGeoJson(emptyFeatureCollection())
        return
    }

    val headingRadians = Math.toRadians(headingDegrees)
    val endLatitude = latitude +
        cos(headingRadians) * HEADING_LINE_LENGTH_METERS / 110_540.0
    val longitudeScale = 111_320.0 *
        cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
    val endLongitude = longitude +
        sin(headingRadians) * HEADING_LINE_LENGTH_METERS / longitudeScale

    source.setGeoJson(
        Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(longitude, latitude),
                    Point.fromLngLat(endLongitude, endLatitude),
                ),
            ),
        ),
    )
}

private fun clearVehicleSources(style: Style) {
    style.getSourceAs<GeoJsonSource>(VEHICLE_SOURCE_ID)?.setGeoJson(emptyFeatureCollection())
    style.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID)?.setGeoJson(emptyFeatureCollection())
    style.getSourceAs<GeoJsonSource>(HEADING_SOURCE_ID)?.setGeoJson(emptyFeatureCollection())
}

private fun emptyFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray())
