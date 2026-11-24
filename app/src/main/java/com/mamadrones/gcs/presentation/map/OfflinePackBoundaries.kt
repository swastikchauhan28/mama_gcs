package com.mamadrones.gcs.presentation.map

/** Native-complete packs only; the draft outline and incomplete downloads are separate concepts. */
internal fun completePackBoundaryRects(packs: List<OfflineFieldPack>): List<OfflineMapRectangle> =
    packs.asSequence().filter { it.complete }.map { pack ->
        val bounds = pack.bounds
        OfflineMapRectangle(bounds.latitudeSouth, bounds.longitudeWest,
            bounds.latitudeNorth, bounds.longitudeEast)
    }.filter { it.isValid() }.toList()
