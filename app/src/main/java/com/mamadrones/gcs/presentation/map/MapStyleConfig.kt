package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.BuildConfig
import com.mamadrones.gcs.domain.model.BasemapStyle
import androidx.compose.runtime.compositionLocalOf
import java.net.URLEncoder

val LocalBasemapStyle = compositionLocalOf { BasemapStyle.STREETS }

object MapStyleConfig {

    val isConfigured: Boolean
        get() = BuildConfig.MAPTILER_API_KEY.isNotBlank()

    val mapTilerStyleUrlOrNull: String?
        get() = styleUrlOrNull(BasemapStyle.STREETS)

    fun styleUrlOrNull(basemap: BasemapStyle, apiKey: String = BuildConfig.MAPTILER_API_KEY): String? =
        apiKey
            .takeIf(String::isNotBlank)
            ?.let { key -> "https://api.maptiler.com/maps/${basemap.mapTilerId}/style.json?key=${URLEncoder.encode(key, "UTF-8")}" }
}
