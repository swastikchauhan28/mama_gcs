package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.settings.SettingsUiState

@Composable
fun SettingsScreen(state: SettingsUiState, onThemeSelected: (ThemeMode) -> Unit, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Settings", "Preferences stored on this device")
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading preferences…") }
    state.error?.let { Notice("PREFERENCES ERROR", it) }
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).selectableGroup()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Dark console, daylight, or device setting", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeMode.entries.forEach { theme ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = theme == state.preferences.theme, enabled = !state.loading && !state.saving,
                        role = Role.RadioButton, onClick = { onThemeSelected(theme) }
                    ), verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = theme == state.preferences.theme, onClick = null, enabled = !state.loading && !state.saving)
                    Text(when (theme) { ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light"; ThemeMode.SYSTEM -> "System" }, Modifier.padding(start = 12.dp))
                }
            }
            if (state.saving) Text("Saving…", style = MaterialTheme.typography.bodySmall)
        }
    }
    SubsystemCard(PanelSpec("Units", "METRIC", listOf("Speed" to "m/s", "Distance" to "m / km", "Temperature" to "°C", "Pressure" to "bar")))
    Notice("CONNECTION SETUP NOT IMPLEMENTED", "No UDP, Bluetooth or serial connection is started by this application. Connection profiles will be configured in a later phase.")
    Notice("LOCAL DISPLAY PREFERENCES", "Theme is saved offline. Passwords, credentials and machine commands are not stored in display preferences.")
}
