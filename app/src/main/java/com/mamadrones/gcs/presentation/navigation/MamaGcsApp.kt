package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.presentation.screens.AdminScreen
import com.mamadrones.gcs.presentation.screens.ControlScreen
import com.mamadrones.gcs.presentation.screens.DashboardScreen
import com.mamadrones.gcs.presentation.screens.HealthScreen
import com.mamadrones.gcs.presentation.screens.MapScreen
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.screens.SettingsScreen

enum class AppDestination(val label: String, val symbol: String) {
    DASHBOARD("Home", "⌂"), MAP("Map", "⌖"), MISSION("Mission", "◎"), MORE("More", "⋯")
}

@Composable
fun MamaGcsApp() {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.name) }
    var morePage by rememberSaveable { mutableStateOf("more") }
    val destination = AppDestination.valueOf(destinationName)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destinationName = item.name; if (item != AppDestination.MORE) morePage = "more" },
                        icon = { Text(item.symbol) }, label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        MamaGcsDestination(destination, morePage, { morePage = it }, padding)
    }
}

@Composable
private fun MamaGcsDestination(
    destination: AppDestination,
    morePage: String,
    onMorePageChange: (String) -> Unit,
    padding: PaddingValues
) {
    val modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
    when (destination) {
        AppDestination.DASHBOARD -> DashboardScreen(modifier)
        AppDestination.MAP -> MapScreen(modifier)
        AppDestination.MISSION -> MissionScreen(modifier)
        AppDestination.MORE -> when (morePage) {
            "health" -> HealthScreen(modifier)
            "control" -> ControlScreen(modifier)
            "admin" -> AdminScreen(modifier)
            "settings" -> SettingsScreen(modifier)
            else -> MoreScreen(modifier, onMorePageChange)
        }
    }
}
