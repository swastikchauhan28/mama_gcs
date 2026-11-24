package com.mamadrones.gcs.presentation.map

import android.content.Context
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One intentionally small region, derived from local planning coordinates only. */
data class OfflineFieldArea(val bounds: LatLngBounds, val estimatedTiles: Long) {
    companion object {
        const val MIN_ZOOM = 12.0
        const val MAX_ZOOM = 18.0
        const val MAX_TILES = 2_000L // margin below OpenFreeMap's 3,000-tile limit

        fun fromDraft(draft: MissionDraft): OfflineFieldArea? {
            val points: List<DraftWaypoint> = draft.keepInFence.ifEmpty { draft.waypoints }
            if (points.isEmpty()) return null
            val south = points.minOf { it.latitude }
            val north = points.maxOf { it.latitude }
            val west = points.minOf { it.longitude }
            val east = points.maxOf { it.longitude }
            if (north > 85.0 || south < -85.0 || east - west > 180.0) return null
            val latPad = maxOf(0.0005, (north - south) * 0.1)
            val lonPad = maxOf(0.0005, (east - west) * 0.1)
            val bounds = LatLngBounds.from(
                (north + latPad).coerceAtMost(85.0), (east + lonPad).coerceAtMost(180.0),
                (south - latPad).coerceAtLeast(-85.0), (west - lonPad).coerceAtLeast(-180.0))
            return fromBounds(bounds)
        }

        fun fromBounds(bounds: LatLngBounds): OfflineFieldArea? {
            if (!bounds.latitudeNorth.isFinite() || !bounds.latitudeSouth.isFinite() ||
                !bounds.longitudeEast.isFinite() || !bounds.longitudeWest.isFinite() ||
                bounds.latitudeNorth > 85.0 || bounds.latitudeSouth < -85.0 ||
                bounds.latitudeNorth < bounds.latitudeSouth ||
                bounds.longitudeEast < bounds.longitudeWest ||
                bounds.longitudeEast - bounds.longitudeWest > 180.0) return null
            var count = 0L
            for (zoom in MIN_ZOOM.toInt()..MAX_ZOOM.toInt()) {
                val n = 1L shl zoom
                val xWest = floor((bounds.longitudeWest + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
                val xEast = floor((bounds.longitudeEast + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
                fun tileY(latitude: Double): Long = floor((1.0 - asinh(tan(Math.toRadians(latitude))) / PI) / 2.0 * n)
                    .toLong().coerceIn(0, n - 1)
                count += (xEast - xWest + 1) * (tileY(bounds.latitudeSouth) - tileY(bounds.latitudeNorth) + 1)
                if (count > MAX_TILES) return null
            }
            return OfflineFieldArea(bounds, count)
        }
    }
}

/** Response-body guard supplements the bounded tile count; it never saves map bytes itself. */
object OfflineDownloadBudget {
    private const val MAX_BYTES = 45_000_000L // below the provider's 50 MB per-download ceiling
    private const val MAX_REQUESTS = 2_800L // conservative ceiling, counting assets as well as tiles
    private val bytes = AtomicLong(0)
    private val requests = AtomicLong(0)
    private val active = AtomicBoolean(false)
    fun begin(): Boolean = active.compareAndSet(false, true).also { if (it) { bytes.set(0); requests.set(0) } }
    fun finish() { active.set(false) }
    fun shouldTrack(host: String): Boolean = active.get() &&
        (host == "openfreemap.org" || host.endsWith(".openfreemap.org"))
    fun beginRequest() {
        if (requests.incrementAndGet() > MAX_REQUESTS) throw IOException("Offline field request limit reached")
    }
    fun count(length: Long) {
        if (length > 0 && bytes.addAndGet(length) > MAX_BYTES) throw IOException("Offline field download exceeded its size limit")
    }
}

data class OfflineFieldPack(
    val id: Long, val name: String, val bounds: LatLngBounds, val complete: Boolean,
    val resources: Long, val bytes: Long, val requiredResources: Long,
    val requiredPrecise: Boolean, val active: Boolean,
) {
    val progressLabel: String get() = when {
        complete -> "Ready"
        requiredPrecise && requiredResources > 0 ->
            "${(resources * 100 / requiredResources).coerceIn(0, 99)}% · $resources/$requiredResources resources"
        else -> "Incomplete · $resources resources saved"
    }
}

data class OfflineFieldMapState(val packs: List<OfflineFieldPack> = emptyList(),
    val loading: Boolean = true, val downloading: Boolean = false, val cancelling: Boolean = false,
    val activePackId: Long? = null, val message: String? = null)

/** MapLibre owns persistent tiles; this object only tracks bounded download UI state. */
object OfflineFieldMaps {
    private const val METADATA_PREFIX = "mama-field-v1:"
    private val mutableState = MutableStateFlow(OfflineFieldMapState())
    val state = mutableState.asStateFlow()
    private val refreshIds = AtomicLong()
    private val operationIds = AtomicLong()
    @Volatile private var activeOperation = 0L
    @Volatile private var activeRegionId: Long? = null
    private var activeRegionRef: WeakReference<OfflineRegion>? = null
    @Volatile private var cancelRequested = false

    fun refresh(context: Context) {
        val refreshId = refreshIds.incrementAndGet()
        mutableState.value = mutableState.value.copy(loading = true)
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(regions: Array<OfflineRegion>?) {
                    val ours = regions.orEmpty().filter { String(it.metadata, Charsets.UTF_8).startsWith(METADATA_PREFIX) }
                    if (refreshId != refreshIds.get()) return
                    if (ours.isEmpty()) {
                        mutableState.value = mutableState.value.copy(packs = emptyList(), loading = false)
                        return
                    }
                    val pending = AtomicInteger(ours.size)
                    val collected = mutableListOf<OfflineFieldPack>()
                    val readFailed = AtomicBoolean(false)
                    fun finishOne(pack: OfflineFieldPack?) {
                        synchronized(collected) {
                            if (pack != null) collected.add(pack) else readFailed.set(true)
                            if (pending.decrementAndGet() == 0 && refreshId == refreshIds.get()) {
                                mutableState.value = mutableState.value.copy(
                                    packs = collected.sortedByDescending { it.id },
                                    loading = readFailed.get(),
                                    message = if (readFailed.get()) "Could not read every saved map. Refresh to retry." else mutableState.value.message)
                            }
                        }
                    }
                    ours.forEach { region ->
                        // A process may have ended while native state still said ACTIVE.
                        if (region.id != activeRegionId && !mutableState.value.downloading) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        }
                        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                finishOne(status?.let { packFromStatus(region, it) })
                            }
                            override fun onError(error: String?) { finishOne(null) }
                        })
                    }
                }
                override fun onError(error: String) {
                    if (refreshId == refreshIds.get()) mutableState.value = mutableState.value.copy(
                        loading = true, message = "Could not list saved field maps. Refresh to retry.")
                }
            })
    }

    fun download(context: Context, draft: MissionDraft) {
        if (mutableState.value.downloading || mutableState.value.loading) return
        val area = OfflineFieldArea.fromDraft(draft)
        if (area == null) {
            mutableState.value = mutableState.value.copy(message = "Add a small field outline or route first. Large or date-line-crossing areas cannot be downloaded.")
            return
        }
        val existing = mutableState.value.packs.firstOrNull { it.bounds.contains(area.bounds) }
        if (existing?.complete == true) {
            mutableState.value = mutableState.value.copy(message = "This plan area is already ready offline.")
            return
        }
        if (existing == null && mutableState.value.packs.size >= 5) {
            mutableState.value = mutableState.value.copy(message = "Five field maps are already saved. Manage storage before downloading another.")
            return
        }
        if (existing != null) {
            resume(context, existing.id)
            return
        }
        val operation = beginOperation("Downloading up to ${area.estimatedTiles} estimated vector tiles.") ?: return
        val definition = OfflineTilePyramidRegionDefinition(MapStyleConfig.OFFLINE_FIELD_STYLE_URL,
            area.bounds, OfflineFieldArea.MIN_ZOOM, OfflineFieldArea.MAX_ZOOM, 1f, false)
        val metadata = (METADATA_PREFIX + draft.name.take(80)).toByteArray(Charsets.UTF_8)
        OfflineManager.getInstance(context.applicationContext).createOfflineRegion(definition, metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    startRegion(context, region, operation)
                }
                override fun onError(error: String) {
                    finishOperation(context, null, operation, "Could not create an offline field map.")
                }
            })
    }

    fun resume(context: Context, id: Long) {
        if (mutableState.value.downloading || mutableState.value.loading) return
        val pack = mutableState.value.packs.firstOrNull { it.id == id && !it.complete } ?: return
        if (OfflineFieldArea.fromBounds(pack.bounds) == null) {
            mutableState.value = mutableState.value.copy(message = "This saved area exceeds the download limit.")
            return
        }
        val operation = beginOperation("Resuming ${pack.name}. Saved resources will be reused.") ?: return
        OfflineManager.getInstance(context.applicationContext).getOfflineRegion(id,
            object : OfflineManager.GetOfflineRegionCallback {
                override fun onRegion(region: OfflineRegion) {
                    val definition = region.definition as? OfflineTilePyramidRegionDefinition
                    if (!String(region.metadata, Charsets.UTF_8).startsWith(METADATA_PREFIX) ||
                        definition?.styleURL != MapStyleConfig.OFFLINE_FIELD_STYLE_URL ||
                        definition.minZoom != OfflineFieldArea.MIN_ZOOM ||
                        definition.maxZoom != OfflineFieldArea.MAX_ZOOM ||
                        definition.includeIdeographs ||
                        definition.bounds?.let { OfflineFieldArea.fromBounds(it) } == null) {
                        finishOperation(context, null, operation, "Saved pack configuration is unsupported; remove it and download again.")
                    } else startRegion(context, region, operation)
                }
                override fun onRegionNotFound() {
                    finishOperation(context, null, operation, "Saved field map was not found. Refresh maps.")
                }
                override fun onError(error: String) {
                    finishOperation(context, null, operation, "Could not resume the saved field map.")
                }
            })
    }

    fun cancel(context: Context) {
        if (!mutableState.value.downloading || mutableState.value.cancelling) return
        val operation = activeOperation
        cancelRequested = true
        mutableState.value = mutableState.value.copy(cancelling = true, message = "Cancelling field download…")
        val id = activeRegionId ?: return // a pending create/resume callback will finish cancellation
        activeRegionRef?.get()?.let {
            finishOperation(context, it, operation, "Download cancelled. The saved pack is incomplete and can be resumed.")
            return
        }
        OfflineManager.getInstance(context.applicationContext).getOfflineRegion(id,
            object : OfflineManager.GetOfflineRegionCallback {
                override fun onRegion(region: OfflineRegion) {
                    finishOperation(context, region, operation, "Download cancelled. The saved pack is incomplete and can be resumed.")
                }
                override fun onRegionNotFound() {
                    finishOperation(context, null, operation, "Download cancelled; saved map was not found.")
                }
                override fun onError(error: String) {
                    finishOperation(context, null, operation, "Could not confirm cancellation. Refresh saved maps.")
                }
            })
    }

    private fun beginOperation(message: String): Long? {
        if (!OfflineDownloadBudget.begin()) return null
        val operation = operationIds.incrementAndGet()
        activeOperation = operation
        activeRegionId = null
        activeRegionRef = null
        cancelRequested = false
        mutableState.value = mutableState.value.copy(downloading = true, cancelling = false,
            activePackId = null, message = message)
        return operation
    }

    fun delete(context: Context, id: Long) {
        if (mutableState.value.downloading) return
        OfflineManager.getInstance(context.applicationContext).getOfflineRegion(id,
            object : OfflineManager.GetOfflineRegionCallback {
                override fun onRegion(region: OfflineRegion) {
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            mutableState.value = mutableState.value.copy(
                                packs = mutableState.value.packs.filterNot { it.id == id },
                                message = "Saved field map removed from this device.")
                        }
                        override fun onError(error: String) {
                            mutableState.value = mutableState.value.copy(message = "Could not remove the saved field map.")
                        }
                    })
                }
                override fun onRegionNotFound() {
                    mutableState.value = mutableState.value.copy(message = "Saved field map was not found.")
                }
                override fun onError(error: String) {
                    mutableState.value = mutableState.value.copy(message = "Could not open the saved field map for removal.")
                }
        })
    }

    private fun startRegion(context: Context, region: OfflineRegion, operation: Long) {
        if (operation != activeOperation) return
        activeRegionId = region.id
        activeRegionRef = WeakReference(region)
        mutableState.value = mutableState.value.copy(activePackId = region.id)
        if (cancelRequested) {
            finishOperation(context, region, operation,
                "Download cancelled. The saved pack is incomplete and can be resumed.")
            return
        }
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (operation != activeOperation) return
                            updatePack(region, status)
                            if (status.completedTileCount > OfflineFieldArea.MAX_TILES ||
                                status.completedResourceSize > 45_000_000L) {
                                finishOperation(context, region, operation,
                                    "Download size limit reached. This pack is incomplete.")
                            } else if (status.isComplete) {
                                finishOperation(context, region, operation,
                                    "Field map ready offline for its saved area and zoom levels.")
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            finishOperation(context, region, operation,
                                if (cancelRequested) "Download cancelled. The saved pack is incomplete and can be resumed."
                                else "Field map download stopped. Check network and try again; the pack is incomplete.")
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            finishOperation(context, region, operation,
                                "Map tile limit reached. This pack is incomplete.")
                        }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun updatePack(region: OfflineRegion, status: OfflineRegionStatus) {
        val pack = packFromStatus(region, status) ?: return
        mutableState.value = mutableState.value.copy(packs =
            (mutableState.value.packs.filterNot { it.id == pack.id } + pack).sortedByDescending { it.id })
    }

    private fun packFromStatus(region: OfflineRegion, status: OfflineRegionStatus): OfflineFieldPack? {
        val definition = region.definition as? OfflineTilePyramidRegionDefinition ?: return null
        val bounds = definition.bounds ?: return null
        return OfflineFieldPack(region.id,
            String(region.metadata, Charsets.UTF_8).removePrefix(METADATA_PREFIX),
            bounds, status.isComplete, status.completedResourceCount,
            status.completedResourceSize, status.requiredResourceCount,
            status.isRequiredResourceCountPrecise,
            region.id == activeRegionId && mutableState.value.downloading)
    }

    private fun finishOperation(context: Context, region: OfflineRegion?, operation: Long, message: String) {
        if (operation != activeOperation) return
        region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region?.setObserver(null)
        activeRegionRef?.get()?.takeIf { it !== region }?.setObserver(null)
        activeRegionRef = null
        activeRegionId = null
        activeOperation = 0L
        cancelRequested = false
        OfflineDownloadBudget.finish()
        mutableState.value = mutableState.value.copy(downloading = false, cancelling = false,
            activePackId = null, message = message)
        refresh(context)
    }
}
