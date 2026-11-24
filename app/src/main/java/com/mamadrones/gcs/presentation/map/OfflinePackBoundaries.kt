package com.mamadrones.gcs.presentation.map

/** Native-complete packs only; the draft outline and incomplete downloads are separate concepts. */
internal fun completePackBoundaryRect(pack: OfflineFieldPack): OfflineMapRectangle? {
    if (!pack.complete) return null
    val bounds = pack.bounds
    return OfflineMapRectangle(bounds.latitudeSouth, bounds.longitudeWest,
        bounds.latitudeNorth, bounds.longitudeEast).takeIf { it.isValid() }
}

internal fun completePackBoundaryRects(packs: List<OfflineFieldPack>): List<OfflineMapRectangle> =
    packs.mapNotNull(::completePackBoundaryRect)

internal fun selectedCompletePackBounds(packs: List<OfflineFieldPack>, id: Long): OfflineMapRectangle? =
    packs.firstOrNull { it.id == id }?.let(::completePackBoundaryRect)
