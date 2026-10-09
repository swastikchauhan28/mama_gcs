package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun PasswordChangeForm(enabled: Boolean, onSubmit: (CharArray, CharArray) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        expanded = !expanded
        current = ""; replacement = ""; confirmation = ""; error = null
    }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("access-change-password")) {
        Text(if (expanded) "Cancel password change" else "Change password")
    }
    if (expanded) Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Change your password", style = MaterialTheme.typography.titleMedium)
            Text("Enter your current password and choose a new one with 12–128 characters. You will sign in again after the change.")
            PasswordField("Current password", current, enabled, "access-current-password") { current = it; error = null }
            PasswordField("New password", replacement, enabled, "access-new-password") { replacement = it; error = null }
            PasswordField("Confirm new password", confirmation, enabled, "access-confirm-new-password") { confirmation = it; error = null }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    when {
                        replacement != confirmation -> error = "New passwords do not match."
                        replacement.length !in 12..128 -> error = "Use 12–128 characters for the new password."
                        replacement == current -> error = "Choose a different new password."
                        else -> {
                            val oldSecret = current.toCharArray()
                            val newSecret = replacement.toCharArray()
                            current = ""; replacement = ""; confirmation = ""; error = null
                            onSubmit(oldSecret, newSecret)
                        }
                    }
                },
                enabled = enabled && current.isNotEmpty() && replacement.isNotEmpty() && confirmation.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("access-save-password"),
            ) { Text("Update password") }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, enabled: Boolean, tag: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(128)) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
