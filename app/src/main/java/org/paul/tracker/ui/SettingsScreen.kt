package org.paul.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun SettingsScreen(
    url: String,
    username: String,
    password: String,
    insecureTls: Boolean,
    lastBackupAt: Instant?,
    lastRestoreAt: Instant?,
    lastError: String,
    backupEnabled: Boolean,
    restoreEnabled: Boolean,
    davInFlight: Boolean,
    restoreNeedsExtraConfirm: Boolean,
    showReset: Boolean,
    onUrlChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onInsecureChange: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onConfirmEmptyRestore: () -> Unit,
    onCancelEmptyRestore: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmBackup by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Prefer https.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("WebDAV URL") },
            singleLine = true,
            enabled = !davInFlight,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUserChange,
            label = { Text("Username") },
            singleLine = true,
            enabled = !davInFlight,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !davInFlight,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = insecureTls,
                onCheckedChange = onInsecureChange,
                enabled = !davInFlight,
            )
            Text("Allow insecure TLS (self-signed). Traffic can be intercepted.")
        }
        Spacer(Modifier.height(16.dp))
        Text("Last backup: ${lastBackupAt?.toString() ?: "never"}")
        Text("Last restore: ${lastRestoreAt?.toString() ?: "never"}")
        if (lastError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(lastError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { confirmBackup = true },
            enabled = backupEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Backup now") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmRestore = true },
            enabled = restoreEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore now") }
        if (showReset) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset local data")
            }
        }
    }
    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            text = {
                Text("Overwrite the server copy with local data? The current file at this URL will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBackup = false
                        onBackup()
                    },
                ) { Text("Backup") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBackup = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            text = {
                Text("Replace all local metrics and samples with the server copy? Samples only on this phone will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        onRestore()
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }
    if (restoreNeedsExtraConfirm) {
        AlertDialog(
            onDismissRequest = onCancelEmptyRestore,
            text = { Text("Server copy has 0 samples. Replace local data anyway?") },
            confirmButton = {
                TextButton(onClick = onConfirmEmptyRestore) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = onCancelEmptyRestore) { Text("Cancel") }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = {
                Text(
                    "Discard unreadable local files (kept as store.json.corrupt) and start empty with built-in metrics?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}
