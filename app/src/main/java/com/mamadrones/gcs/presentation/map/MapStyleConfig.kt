package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.BuildConfig
import com.mamadrones.gcs.domain.model.BasemapStyle
import androidx.compose.runtime.compositionLocalOf
import java.net.URLEncoder

val LocalBasemapStyle = compositionLocalOf { BasemapStyle.STREETS }
/** Navigation only: map failure UI must not silently change the saved provider. */
val LocalMapSettingsNavigation = compositionLocalOf<(() -> Unit)?> { null }

object MapStyleConfig {
    const val OFFLINE_FIELD_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    val isConfigured: Boolean
        get() = BuildConfig.MAPTILER_API_KEY.isNotBlank()

    val mapTilerStyleUrlOrNull: String?
        get() = styleUrlOrNull(BasemapStyle.STREETS)

    fun styleUrlOrNull(basemap: BasemapStyle, apiKey: String = BuildConfig.MAPTILER_API_KEY): String? =
        when (basemap) {
            BasemapStyle.OFFLINE_FIELD -> OFFLINE_FIELD_STYLE_URL
            else -> apiKey.takeIf { !basemap.isGoogle && it.isNotBlank() }
                ?.let { key -> "https://api.maptiler.com/maps/${basemap.mapTilerId}/style.json?key=${URLEncoder.encode(key, "UTF-8")}" }
        }
}
