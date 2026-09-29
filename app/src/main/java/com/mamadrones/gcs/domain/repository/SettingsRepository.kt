package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.AppPreferences
import com.mamadrones.gcs.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setTheme(theme: ThemeMode)
}
