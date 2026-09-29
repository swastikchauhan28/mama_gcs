package com.mamadrones.gcs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamadrones.gcs.presentation.dashboard.DashboardViewModel
import com.mamadrones.gcs.presentation.settings.SettingsViewModel
import com.mamadrones.gcs.presentation.settings.ConnectionViewModel
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vehicleViewModel: DashboardViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val connectionViewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vehicle = vehicleViewModel.vehicleState.collectAsStateWithLifecycle().value
            val settings = settingsViewModel.state.collectAsStateWithLifecycle().value
            val connection = connectionViewModel.state.collectAsStateWithLifecycle().value
            MamaGcsTheme(settings.preferences.theme) {
                MamaGcsApp(
                    vehicle = vehicle,
                    settings = settings,
                    onThemeSelected = settingsViewModel::selectTheme,
                    connection = connection,
                    onRemoteHostChanged = connectionViewModel::updateRemoteHost,
                    onRemotePortChanged = connectionViewModel::updateRemotePort,
                    onLocalPortChanged = connectionViewModel::updateLocalPort,
                    onSaveEndpoint = connectionViewModel::saveEndpoint,
                    onOpenSocket = connectionViewModel::openSocket,
                    onCloseSocket = connectionViewModel::closeSocket,
                    onClearEndpoint = connectionViewModel::clearEndpoint
                )
            }
        }
    }
}
