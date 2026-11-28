package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft

/** One-based indices of displayed geometry outside the selected pack; never persisted. */
internal data class SavedPlanOutsidePoints(
    val routePointNumbers: List<Int>,
    val outlineVertexNumbers: List<Int>,
) {
    val total: Int get() = routePointNumbers.size + outlineVertexNumbers.size
}

internal fun findPlanOutsideSavedArea(draft: MissionDraft, area: OfflineMapRectangle): SavedPlanOutsidePoints {
    require(area.isValid())
    fun DraftWaypoint.outside(): Boolean = latitude < area.south || latitude > area.north ||
        longitude < area.west || longitude > area.east
    return SavedPlanOutsidePoints(
        routePointNumbers = draft.waypoints.mapIndexedNotNull { index, point ->
            (index + 1).takeIf { point.outside() }
        },
        outlineVertexNumbers = draft.keepInFence.takeIf { it.size >= 3 }
            ?.mapIndexedNotNull { index, vertex -> (index + 1).takeIf { vertex.outside() } }
            .orEmpty(),
    )
}
