package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.domain.model.AppPreferences
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

// Exactly one DataStore per file. Stores display preferences only, never credentials or commands.
private val Context.preferences by preferencesDataStore(name = "display_preferences")

@Singleton
class LocalSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme")
    override val preferences = context.preferences.data.map { values ->
        AppPreferences(ThemeMode.entries.find { it.name == values[themeKey] } ?: ThemeMode.DARK)
    }
    override suspend fun setTheme(theme: ThemeMode) {
        context.preferences.edit { it[themeKey] = theme.name }
    }
}
