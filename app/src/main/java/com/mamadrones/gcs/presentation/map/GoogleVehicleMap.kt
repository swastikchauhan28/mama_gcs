package com.mamadrones.gcs.presentation.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.mamadrones.gcs.BuildConfig
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.CoverageMapLine
import com.mamadrones.gcs.domain.model.CoverageMapPoint
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.HomePositionEvidence
import com.mamadrones.gcs.domain.model.MapPositionEvidence
import com.mamadrones.gcs.domain.model.MapPositionStatus
import com.mamadrones.gcs.domain.model.VehicleState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** All geometry is a display projection. This map never sends a vehicle command. */
@Composable
internal fun GoogleVehicleMap(
    state: VehicleState,
    modifier: Modifier,
    draftWaypoints: List<DraftWaypoint>,
    draftFence: List<DraftWaypoint>,
    planningMode: Boolean,
    onWaypointRequested: ((Double, Double) -> Unit)?,
    onWaypointSelected: ((String) -> Unit)?,
    coverageLines: List<CoverageMapLine>,
    compactPlanningControls: Boolean,
    coverageGapSamples: List<CoverageMapPoint>?,
    basemap: BasemapStyle,
    initialFitDraft: Boolean,
    readOnlyPlan: Boolean,
) {
    val context = LocalContext.current
    val playAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    val openMapSettings = LocalMapSettingsNavigation.current
    if (!BuildConfig.GOOGLE_MAPS_CONFIGURED || !playAvailable) {
        GoogleMapRecoveryNotice(
            if (!BuildConfig.GOOGLE_MAPS_CONFIGURED) GoogleMapFailure.MISSING_KEY
            else GoogleMapFailure.MISSING_PLAY_SERVICES,
            modifier, openMapSettings)
        return
    }

    var retry by remember { mutableIntStateOf(0) }
    val mapView = rememberGoogleMapViewWithLifecycle(retry)
    var map by remember(mapView) { mutableStateOf<GoogleMap?>(null) }
    var tilesLoaded by remember(mapView) { mutableStateOf(false) }
    var loadFailed by remember(mapView) { mutableStateOf(false) }
    var cameraInitialized by remember(mapView) { mutableStateOf(false) }
    var initialDraftFitted by remember(mapView) { mutableStateOf(false) }
    var mapSize by remember(mapView) { mutableStateOf(IntSize.Zero) }
    var followVehicle by remember(mapView) { mutableStateOf(!planningMode) }
    val collapseForLargeText = planningMode && LocalDensity.current.fontScale >= 1.8f
    var controlsExpanded by remember(compactPlanningControls, collapseForLargeText) {
        mutableStateOf(!compactPlanningControls && !collapseForLargeText)
    }
    val requested by rememberUpdatedState(onWaypointRequested)
    val selected by rememberUpdatedState(onWaypointSelected)
    val currentPlanning by rememberUpdatedState(planningMode)
    val vehicleOverlays = remember(mapView) { GoogleOverlayBag() }
    val homeOverlays = remember(mapView) { GoogleOverlayBag() }
    val draftOverlays = remember(mapView) { GoogleOverlayBag() }
    val coverageOverlays = remember(mapView) { GoogleOverlayBag() }

    DisposableEffect(mapView, basemap) {
        var disposed = false
        mapView.getMapAsync { ready ->
            if (disposed) return@getMapAsync
            ready.mapType = when (basemap) {
                BasemapStyle.GOOGLE_SATELLITE -> GoogleMap.MAP_TYPE_SATELLITE
                BasemapStyle.GOOGLE_HYBRID -> GoogleMap.MAP_TYPE_HYBRID
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
            ready.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = true
                isMapToolbarEnabled = false
            }
            ready.setOnMapLoadedCallback { if (!disposed) { tilesLoaded = true; loadFailed = false } }
            ready.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) followVehicle = false
            }
            ready.setOnMapLongClickListener { point ->
                if (tilesLoaded && currentPlanning) requested?.invoke(point.latitude, point.longitude)
            }
            ready.setOnMarkerClickListener { marker ->
                val id = marker.tag as? String
                if (tilesLoaded && currentPlanning && id != null && selected != null) {
                    selected?.invoke(id)
                    true
                } else false
            }
            map = ready
        }
        onDispose {
            disposed = true
            map?.setOnMapLongClickListener(null)
            map?.setOnMarkerClickListener(null)
            map?.setOnCameraMoveStartedListener(null)
            vehicleOverlays.clear()
            homeOverlays.clear()
            draftOverlays.clear()
            coverageOverlays.clear()
            map = null
        }
    }
    LaunchedEffect(mapView, tilesLoaded) {
        if (!tilesLoaded) {
            delay(20_000)
            if (!tilesLoaded) loadFailed = true
        }
    }

    val clockTick by produceState(android.os.SystemClock.elapsedRealtime()) {
        while (true) { delay(1_000); value = android.os.SystemClock.elapsedRealtime() }
    }
    val visiblePosition = if (MapPositionEvidence.status(state, clockTick) == MapPositionStatus.FRESH) {
        validPoint(state.position.latitude, state.position.longitude)
    } else null
    val visibleHome = HomePositionEvidence.visibleOnMap(state, System.currentTimeMillis())
    LaunchedEffect(map, visibleHome) {
        homeOverlays.clear()
        val ready = map ?: return@LaunchedEffect
        validPoint(visibleHome?.latitude, visibleHome?.longitude)?.let { point ->
            ready.addMarker(MarkerOptions().position(point).title("Received home position")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))
                ?.let(homeOverlays.markers::add)
        }
    }
    LaunchedEffect(map, visiblePosition, state.positionTrack, state.headingDegrees) {
        vehicleOverlays.clear()
        val ready = map ?: return@LaunchedEffect
        val point = visiblePosition ?: return@LaunchedEffect
        ready.addMarker(MarkerOptions().position(point).title("Received vehicle position")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
            ?.let(vehicleOverlays.markers::add)
        val track = state.positionTrack.mapNotNull { validPoint(it.latitude, it.longitude) }
        if (track.size >= 2) vehicleOverlays.polylines += ready.addPolyline(
            PolylineOptions().addAll(track).color(Color.rgb(58, 166, 255)).width(5f))
        val heading = state.headingDegrees
        if (heading != null && heading.isFinite()) {
            val radians = Math.toRadians(heading)
            val end = validPoint(point.latitude + cos(radians) * 10.0 / 110_540.0,
                point.longitude + sin(radians) * 10.0 /
                    (111_320.0 * cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)))
            if (end != null) vehicleOverlays.polylines += ready.addPolyline(
                PolylineOptions().add(point, end).color(Color.rgb(255, 193, 7)).width(4f))
        }
        if (planningMode && draftWaypoints.isNotEmpty() && !followVehicle) return@LaunchedEffect
        if (!cameraInitialized) {
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 17f))
            cameraInitialized = true
        } else if (followVehicle) ready.animateCamera(CameraUpdateFactory.newLatLng(point), 350, null)
    }
    LaunchedEffect(map, draftWaypoints, draftFence, planningMode) {
        draftOverlays.clear()
        val ready = map ?: return@LaunchedEffect
        if (!planningMode) return@LaunchedEffect
        val route = draftWaypoints.map { LatLng(it.latitude, it.longitude) }
        if (route.size >= 2) draftOverlays.polylines += ready.addPolyline(
            PolylineOptions().addAll(route).color(Color.rgb(255, 159, 67)).width(3f))
        if (draftFence.size >= 3) draftOverlays.polygons += ready.addPolygon(
            PolygonOptions().addAll(draftFence.map { LatLng(it.latitude, it.longitude) })
                .fillColor(Color.argb(40, 255, 181, 71)).strokeColor(Color.rgb(255, 181, 71)).strokeWidth(3f))
        draftWaypoints.forEachIndexed { index, waypoint ->
            ready.addMarker(MarkerOptions().position(route[index]).title("Waypoint ${index + 1}")
                .icon(BitmapDescriptorFactory.fromBitmap(numberedPin(index + 1))))?.also {
                it.tag = waypoint.id
                draftOverlays.markers += it
            }
        }
        if (!cameraInitialized) (route.firstOrNull() ?: draftFence.firstOrNull()?.let { LatLng(it.latitude, it.longitude) })?.let {
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
            cameraInitialized = true
        }
    }
    LaunchedEffect(map, coverageLines, coverageGapSamples, planningMode) {
        coverageOverlays.clear()
        val ready = map ?: return@LaunchedEffect
        if (!planningMode) return@LaunchedEffect
        coverageLines.forEach { line ->
            val start = validPoint(line.start.latitude, line.start.longitude)
            val end = validPoint(line.end.latitude, line.end.longitude)
            if (start != null && end != null) coverageOverlays.polylines += ready.addPolyline(
                PolylineOptions().add(start, end).color(Color.rgb(0, 157, 170)).width(4f))
        }
        // The estimator can produce 16,384 cells. One bounded image avoids thousands of SDK markers.
        val gaps = coverageGapSamples.orEmpty().mapNotNull { validPoint(it.latitude, it.longitude) }
        if (gaps.isNotEmpty()) {
            val minLat = (gaps.minOf { it.latitude } - 0.000001).coerceAtLeast(-90.0)
            val maxLat = (gaps.maxOf { it.latitude } + 0.000001).coerceAtMost(90.0)
            val minLon = (gaps.minOf { it.longitude } - 0.000001).coerceAtLeast(-180.0)
            val maxLon = (gaps.maxOf { it.longitude } + 0.000001).coerceAtMost(180.0)
            val bitmap = createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 35, 140) }
            gaps.forEach { point ->
                val x = ((point.longitude - minLon) / (maxLon - minLon) * 511).toFloat()
                val y = ((maxLat - point.latitude) / (maxLat - minLat) * 511).toFloat()
                canvas.drawCircle(x, y, 2.5f, paint)
            }
            coverageOverlays.bitmaps += bitmap
            ready.addGroundOverlay(GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                .positionFromBounds(LatLngBounds(LatLng(minLat, minLon), LatLng(maxLat, maxLon)))
                .zIndex(2f))?.let(coverageOverlays.groundOverlays::add)
        }
    }

    fun fitDraft() {
        val ready = map ?: return
        val points = (draftWaypoints + draftFence).map { LatLng(it.latitude, it.longitude) }.distinct()
        if (points.isEmpty()) return
        followVehicle = false
        if (collapseForLargeText) controlsExpanded = false
        if (points.size == 1) ready.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 17f))
        else if (mapView.width > 0 && mapView.height > 0) {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            val padding = (24 * mapView.resources.displayMetrics.density).toInt()
            ready.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }
    LaunchedEffect(map, tilesLoaded, mapSize, draftWaypoints, draftFence, initialFitDraft, initialDraftFitted) {
        if (initialFitDraft && !initialDraftFitted && tilesLoaded && map != null &&
            mapSize.width > 0 && mapSize.height > 0 && (draftWaypoints.isNotEmpty() || draftFence.isNotEmpty())) {
            fitDraft()
            initialDraftFitted = true
        }
    }
    val legend = when {
        coverageGapSamples == null -> "Preview only · cyan: passes. Gap estimate unavailable. Gaps can be missed."
        coverageGapSamples.isEmpty() -> "Preview only · cyan: passes. No sampled gaps detected in main area. Gaps can be missed."
        else -> "Preview only · cyan: passes; magenta: sampled gap centers (main area only). Gaps can be missed."
    }
    val buttonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary)
    Column(modifier) {
        if (compactPlanningControls && planningMode && coverageLines.isNotEmpty() && tilesLoaded) {
            Text(legend, Modifier.padding(8.dp).testTag("coverage-map-legend"), style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()
                .onSizeChanged { mapSize = it }.testTag("coverage-map-canvas"))
            if (!compactPlanningControls && planningMode && coverageLines.isNotEmpty() && tilesLoaded) {
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp).testTag("coverage-map-legend"),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    Text(legend, Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!tilesLoaded) {
                if (loadFailed) GoogleMapRecoveryNotice(GoogleMapFailure.TILES_TIMED_OUT,
                    Modifier.align(Alignment.Center), openMapSettings, onRetry = { retry++ })
                else GoogleMapNotice("LOADING GOOGLE MAP", "Waiting for Google map tiles.",
                    Modifier.align(Alignment.Center))
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(start = 8.dp, end = 8.dp, bottom = 32.dp)
                .testTag("map-control-overlay"), verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End) {
                if (controlsExpanded) {
                    if (planningMode) FilledTonalButton(onClick = ::fitDraft,
                        enabled = tilesLoaded && (draftWaypoints.isNotEmpty() || draftFence.size >= 3),
                        colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp).testTag("mission-fit")) {
                        Text(if (readOnlyPlan) "Fit area" else "Fit draft")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                            enabled = tilesLoaded, colors = buttonColors,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Zoom in" }) { Text("+") }
                        FilledTonalButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                            enabled = tilesLoaded, colors = buttonColors,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Zoom out" }) { Text("−") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = {
                            visiblePosition?.let { followVehicle = false; map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f)) }
                        }, enabled = tilesLoaded && visiblePosition != null, colors = buttonColors,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("map-center"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("Center") }
                        FilledTonalButton(onClick = {
                            visiblePosition?.let { followVehicle = true; map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f)) }
                        }, enabled = tilesLoaded && visiblePosition != null, colors = buttonColors,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("map-follow"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(if (followVehicle && visiblePosition != null && tilesLoaded) "Following" else "Follow")
                        }
                    }
                }
            }
        }
        if (compactPlanningControls || collapseForLargeText) FilledTonalButton(
            onClick = { controlsExpanded = !controlsExpanded }, colors = buttonColors,
            modifier = Modifier.heightIn(min = 48.dp).testTag("coverage-map-controls")) {
            Text(if (controlsExpanded) "Hide controls" else "Map controls")
        }
    }
}

private fun validPoint(latitude: Double?, longitude: Double?): LatLng? =
    if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0) LatLng(latitude, longitude) else null

private fun numberedPin(number: Int): Bitmap = createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
    val canvas = Canvas(this)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 159, 67) }
    canvas.drawCircle(32f, 32f, 29f, paint)
    paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 28f
    paint.isFakeBoldText = true
    canvas.drawText(number.toString(), 32f, 42f, paint)
}

private class GoogleOverlayBag {
    val markers = mutableListOf<Marker>()
    val polylines = mutableListOf<Polyline>()
    val polygons = mutableListOf<Polygon>()
    val groundOverlays = mutableListOf<GroundOverlay>()
    val bitmaps = mutableListOf<Bitmap>()
    fun clear() {
        markers.forEach { it.remove() }; markers.clear()
        polylines.forEach { it.remove() }; polylines.clear()
        polygons.forEach { it.remove() }; polygons.clear()
        groundOverlays.forEach { it.remove() }; groundOverlays.clear()
        bitmaps.forEach { it.recycle() }; bitmaps.clear()
    }
}

enum class GoogleMapFailure(val title: String, val detail: String) {
    MISSING_KEY("GOOGLE MAPS KEY MISSING", "This build has no Google Maps key. Choose a MapLibre map, or add a restricted key and rebuild."),
    MISSING_PLAY_SERVICES("GOOGLE PLAY SERVICES UNAVAILABLE", "This device cannot load Google Maps. Choose a MapLibre map in Maps settings."),
    TILES_TIMED_OUT("GOOGLE MAP TIMED OUT", "Map tiles did not finish loading. Check network, billing and Android key restrictions, or choose MapLibre."),
}

@Composable
fun GoogleMapRecoveryNotice(failure: GoogleMapFailure, modifier: Modifier = Modifier,
                            onChooseProvider: (() -> Unit)?, onRetry: (() -> Unit)? = null) {
    GoogleMapNotice(failure.title, failure.detail, modifier, onRetry, onChooseProvider)
}

@Composable
private fun GoogleMapNotice(title: String, detail: String, modifier: Modifier,
                            retry: (() -> Unit)? = null, onChooseProvider: (() -> Unit)? = null) {
    Surface(modifier.padding(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            retry?.let { TextButton(onClick = it, modifier = Modifier.testTag("google-map-retry")) { Text("Retry map") } }
            onChooseProvider?.let {
                TextButton(onClick = it, modifier = Modifier.testTag("google-map-choose-provider")) {
                    Text("Choose map provider")
                }
            }
        }
    }
}

@Composable
private fun rememberGoogleMapViewWithLifecycle(retry: Int): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(context, retry) { MapView(context).apply { onCreate(null) } }
    DisposableEffect(lifecycle, view) {
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { if (!resumed) { start(); view.onResume(); resumed = true } }
        fun pause() { if (resumed) { view.onPause(); resumed = false } }
        fun stop() { if (started) { pause(); view.onStop(); started = false } }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop(); view.onDestroy() }
    }
    return view
}
