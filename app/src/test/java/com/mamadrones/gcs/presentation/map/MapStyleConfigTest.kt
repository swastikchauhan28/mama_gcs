package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.domain.model.AppPreferences
import com.mamadrones.gcs.domain.model.BasemapStyle
import org.junit.Assert.*
import org.junit.Test

class MapStyleConfigTest {
    @Test fun missingOrUnknownStoredChoiceFallsBackToStreets() {
        for (value in listOf(null, "", "retired", "https://untrusted.example/style.json")) {
            assertEquals(BasemapStyle.STREETS, BasemapStyle.fromStored(value))
        }
        assertEquals(BasemapStyle.STREETS, AppPreferences().basemap)
    }

    @Test fun allChoicesRoundTripStableNames() {
        BasemapStyle.entries.forEach { assertEquals(it, BasemapStyle.fromStored(it.name)) }
    }

    @Test fun eachChoiceUsesAllowlistedHttpsProviderStyle() {
        val ids = listOf("streets-v4", "satellite-v4", "hybrid-v4")
        BasemapStyle.entries.zip(ids).forEach { (style, id) ->
            assertEquals("https://api.maptiler.com/maps/$id/style.json?key=test-key",
                MapStyleConfig.styleUrlOrNull(style, "test-key"))
        }
    }

    @Test fun blankKeyNeverCreatesAConfiguredSource() {
        BasemapStyle.entries.forEach { style ->
            assertNull(MapStyleConfig.styleUrlOrNull(style, ""))
            assertNull(MapStyleConfig.styleUrlOrNull(style, " \t"))
        }
    }

    @Test fun keyIsEncodedAsOneQueryValue() {
        assertEquals("https://api.maptiler.com/maps/streets-v4/style.json?key=a%26b%3D%23%3F+%2B",
            MapStyleConfig.styleUrlOrNull(BasemapStyle.STREETS, "a&b=#? +"))
    }
}
