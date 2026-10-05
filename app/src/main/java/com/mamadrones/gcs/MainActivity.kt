package com.mamadrones.gcs

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mamadrones.gcs.presentation.dashboard.DashboardViewModel
import com.mamadrones.gcs.presentation.settings.SettingsViewModel
import com.mamadrones.gcs.presentation.settings.ConnectionViewModel
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.mission.MissionPlanViewModel
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vehicleViewModel: DashboardViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val connectionViewModel: ConnectionViewModel by viewModels()
    private val missionPlanViewModel: MissionPlanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enterImmersiveMode()
        setContent {
            val vehicle = vehicleViewModel.vehicleState.collectAsStateWithLifecycle().value
            val settings = settingsViewModel.state.collectAsStateWithLifecycle().value
            val connection = connectionViewModel.state.collectAsStateWithLifecycle().value
            val missionPlan = missionPlanViewModel.state.collectAsStateWithLifecycle().value
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
                    onClearEndpoint = connectionViewModel::clearEndpoint,
                    missionPlan = missionPlan,
                    onMissionAction = missionPlanViewModel::dispatch
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // Keep Android navigation available through an edge swipe, without reserving space.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
