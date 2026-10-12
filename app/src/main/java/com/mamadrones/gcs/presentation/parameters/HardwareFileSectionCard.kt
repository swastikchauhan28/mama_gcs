package com.mamadrones.gcs.presentation.parameters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HardwareFileSectionCard(section: HardwareFileSection) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("hardware-file-${section.id}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(section.guidance, style = MaterialTheme.typography.bodySmall)
            section.fields.forEach { field ->
                Column {
                    Text(field.name, style = MaterialTheme.typography.labelLarge)
                    Text("File value: ${field.entry?.rawValue ?: "NOT IN FILE"}")
                    field.entry?.let { Text("Source line ${it.line}", style = MaterialTheme.typography.bodySmall) }
                    field.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
