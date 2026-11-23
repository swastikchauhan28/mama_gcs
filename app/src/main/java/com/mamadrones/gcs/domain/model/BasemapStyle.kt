package com.mamadrones.gcs.domain.model

/** Stable local preference identifiers, not arbitrary URLs or vehicle settings. */
enum class BasemapStyle(val label: String, val description: String, val mapTilerId: String, val isGoogle: Boolean = false) {
    STREETS("Streets", "Roads, places and geographic context", "streets-v4"),
    SATELLITE("Satellite", "Imagery without street labels", "satellite-v4"),
    HYBRID("Satellite with labels", "Imagery with roads and place labels", "hybrid-v4"),
    GOOGLE_STREETS("Google streets", "Google road map · internet required", "", true),
    GOOGLE_SATELLITE("Google satellite", "Google imagery · internet required", "", true),
    GOOGLE_HYBRID("Google satellite with labels", "Google imagery and roads · internet required", "", true);

    companion object {
        fun fromStored(value: String?): BasemapStyle = entries.find { it.name == value } ?: STREETS
    }
}
