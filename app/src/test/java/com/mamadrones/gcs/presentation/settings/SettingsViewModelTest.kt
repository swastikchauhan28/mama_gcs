package com.mamadrones.gcs.presentation.settings

import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.domain.usecase.UpdateThemeUseCase
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Repository : SettingsRepository {
        override val preferences = MutableStateFlow(AppPreferences(theme = ThemeMode.DARK,
            udpEndpoint = UdpEndpoint("192.168.1.14", 14551, 14550), basemap = BasemapStyle.HYBRID))
        var fail = false
        var mapWrites = 0
        override suspend fun setTheme(theme: ThemeMode) { preferences.value = preferences.value.copy(theme = theme) }
        override suspend fun setUdpEndpoint(endpoint: UdpEndpoint?) { preferences.value = preferences.value.copy(udpEndpoint = endpoint) }
        override suspend fun setBasemap(basemap: BasemapStyle) {
            mapWrites++
            if (fail) throw IOException("test storage failure")
            preferences.value = preferences.value.copy(basemap = basemap)
        }
    }

    @Test fun mapWritePreservesThemeAndEndpoint() = runTest(dispatcher) {
        val repository = Repository()
        val model = SettingsViewModel(repository, UpdateThemeUseCase(repository))
        runCurrent()
        val initial = model.state.value.preferences
        model.selectBasemap(BasemapStyle.SATELLITE)
        assertTrue(model.state.value.saving)
        runCurrent()
        assertEquals(initial.copy(basemap = BasemapStyle.SATELLITE), model.state.value.preferences)
        assertFalse(model.state.value.saving)
    }

    @Test fun themeWriteDoesNotResetMapOrEndpoint() = runTest(dispatcher) {
        val repository = Repository()
        val model = SettingsViewModel(repository, UpdateThemeUseCase(repository))
        runCurrent()
        val initial = model.state.value.preferences
        model.selectTheme(ThemeMode.LIGHT)
        runCurrent()
        assertEquals(initial.copy(theme = ThemeMode.LIGHT), model.state.value.preferences)
    }

    @Test fun failedMapWriteKeepsPreviousChoiceAndCanRetry() = runTest(dispatcher) {
        val repository = Repository()
        val model = SettingsViewModel(repository, UpdateThemeUseCase(repository))
        runCurrent()
        repository.fail = true
        model.selectBasemap(BasemapStyle.SATELLITE)
        runCurrent()
        assertEquals(BasemapStyle.HYBRID, model.state.value.preferences.basemap)
        assertEquals("Map choice could not be saved. Try again.", model.state.value.error)
        assertFalse(model.state.value.saving)
        repository.fail = false
        model.selectBasemap(BasemapStyle.SATELLITE)
        runCurrent()
        assertEquals(BasemapStyle.SATELLITE, model.state.value.preferences.basemap)
        assertNull(model.state.value.error)
    }

    @Test fun loadingAndConcurrentSavesCannotOverwritePendingChoice() = runTest(dispatcher) {
        val repository = Repository()
        val model = SettingsViewModel(repository, UpdateThemeUseCase(repository))
        model.selectBasemap(BasemapStyle.STREETS)
        runCurrent()
        assertEquals(0, repository.mapWrites)
        model.selectBasemap(BasemapStyle.SATELLITE)
        model.selectBasemap(BasemapStyle.STREETS)
        model.selectTheme(ThemeMode.LIGHT)
        runCurrent()
        assertEquals(1, repository.mapWrites)
        assertEquals(BasemapStyle.SATELLITE, model.state.value.preferences.basemap)
        assertEquals(ThemeMode.DARK, model.state.value.preferences.theme)
    }
}
