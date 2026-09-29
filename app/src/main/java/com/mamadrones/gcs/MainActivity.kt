package com.mamadrones.gcs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamadrones.gcs.presentation.dashboard.DashboardViewModel
import com.mamadrones.gcs.presentation.settings.SettingsViewModel
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vehicleViewModel: DashboardViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vehicle = vehicleViewModel.vehicleState.collectAsStateWithLifecycle().value
            val settings = settingsViewModel.state.collectAsStateWithLifecycle().value
            MamaGcsTheme(settings.preferences.theme) {
                MamaGcsApp(vehicle, settings, settingsViewModel::selectTheme)
            }
        }
    }
}
