package com.mamadrones.gcs.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.domain.model.AppPreferences
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.domain.usecase.UpdateThemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val updateTheme: UpdateThemeUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                repository.preferences.collect { preferences ->
                    mutableState.update { it.copy(preferences = preferences, loading = false) }
                }
            } catch (_: IOException) {
                mutableState.update { it.copy(loading = false, error = "Could not read display preferences. Restart the app to retry.") }
            }
        }
    }
    fun selectTheme(theme: ThemeMode) {
        if (mutableState.value.saving || mutableState.value.loading) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                updateTheme(theme)
                // Also reflect a confirmed write if the initial read collector failed.
                mutableState.update { it.copy(preferences = AppPreferences(theme)) }
            } catch (_: IOException) {
                mutableState.update { it.copy(error = "Theme could not be saved. Try again.") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }
}
