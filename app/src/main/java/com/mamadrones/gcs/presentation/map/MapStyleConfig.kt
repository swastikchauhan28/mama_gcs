package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.BuildConfig

object MapStyleConfig {

    private const val STREET_STYLE =
        "https://api.maptiler.com/maps/streets-v4/style.json"

    val isConfigured: Boolean
        get() = BuildConfig.MAPTILER_API_KEY.isNotBlank()

    val mapTilerStyleUrlOrNull: String?
        get() = BuildConfig.MAPTILER_API_KEY
            .takeIf(String::isNotBlank)
            ?.let { key -> "$STREET_STYLE?key=$key" }
}
