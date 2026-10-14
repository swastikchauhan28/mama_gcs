package com.mamadrones.gcs.domain.model

/** Stable local preference identifiers, not arbitrary URLs or vehicle settings. */
enum class BasemapStyle(val label: String, val description: String, val mapTilerId: String) {
    STREETS("Streets", "Roads, places and geographic context", "streets-v4"),
    SATELLITE("Satellite", "Imagery without street labels", "satellite-v4"),
    HYBRID("Satellite with labels", "Imagery with roads and place labels", "hybrid-v4");

    companion object {
        fun fromStored(value: String?): BasemapStyle = entries.find { it.name == value } ?: STREETS
    }
}
