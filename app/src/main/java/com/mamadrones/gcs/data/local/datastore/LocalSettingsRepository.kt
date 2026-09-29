package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.domain.model.AppPreferences
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.UdpEndpoint
import com.mamadrones.gcs.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

// Exactly one DataStore per file. Stores local display and explicit endpoint preferences, never credentials or commands.
private val Context.preferences by preferencesDataStore(name = "display_preferences")

@Singleton
class LocalSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme")
    private val udpRemoteHostKey = stringPreferencesKey("udp_remote_host")
    private val udpRemotePortKey = intPreferencesKey("udp_remote_port")
    private val udpLocalPortKey = intPreferencesKey("udp_local_port")
    override val preferences = context.preferences.data.map { values ->
        AppPreferences(
            theme = ThemeMode.entries.find { it.name == values[themeKey] } ?: ThemeMode.DARK,
            udpEndpoint = values[udpRemoteHostKey]?.let { host ->
                runCatching {
                    UdpEndpoint(
                        remoteHost = host,
                        remotePort = values[udpRemotePortKey] ?: UdpEndpoint.DEFAULT_PORT,
                        localPort = values[udpLocalPortKey] ?: UdpEndpoint.DEFAULT_PORT
                    )
                }.getOrNull()
            }
        )
    }
    override suspend fun setTheme(theme: ThemeMode) {
        context.preferences.edit { it[themeKey] = theme.name }
    }
    override suspend fun setUdpEndpoint(endpoint: UdpEndpoint?) {
        context.preferences.edit { values ->
            if (endpoint == null) {
                values.remove(udpRemoteHostKey)
                values.remove(udpRemotePortKey)
                values.remove(udpLocalPortKey)
            } else {
                values[udpRemoteHostKey] = endpoint.remoteHost
                values[udpRemotePortKey] = endpoint.remotePort
                values[udpLocalPortKey] = endpoint.localPort
            }
        }
    }
}
