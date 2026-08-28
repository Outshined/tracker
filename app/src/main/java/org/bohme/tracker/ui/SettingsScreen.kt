package org.bohme.tracker.ui

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample
import org.bohme.tracker.stats.formatSampleValues

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
    metrics: List<MetricDef>,
    samples: List<Sample>,
    onAddMetric: () -> Unit,
    onEditMetric: (String) -> Unit,
    onDeleteMetric: (String) -> Unit,
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
    var replaceLocked by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(restoreNeedsExtraConfirm) {
        if (restoreNeedsExtraConfirm) replaceLocked = false
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Metric Management")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAddMetric,
            modifier = Modifier.fillMaxWidth().testTag("btn-settings-add-metric"),
        ) { Text("Add metric") }
        Spacer(Modifier.height(8.dp))
        if (metrics.isNotEmpty()) {
            metrics.forEach { metric ->
                val last = samples.filter { it.metricId == metric.id }.maxByOrNull { it.recordedAt }
                MetricManagementRow(
                    metric = metric,
                    last = last,
                    onEdit = { onEditMetric(metric.id) },
                    onDelete = { pendingDelete = metric.id },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        } else {
            Text("No metrics yet.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("WebDAV URL") },
            singleLine = true,
            enabled = !davInFlight,
            modifier = Modifier.fillMaxWidth().testTag("field-dav-url"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUserChange,
            label = { Text("Username") },
            singleLine = true,
            enabled = !davInFlight,
            modifier = Modifier.fillMaxWidth().testTag("field-dav-user"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !davInFlight,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("field-dav-pass"),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = insecureTls,
                onCheckedChange = onInsecureChange,
                enabled = !davInFlight,
                modifier = Modifier.testTag("check-insecure-tls"),
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
            modifier = Modifier.fillMaxWidth().testTag("btn-backup"),
        ) { Text("Backup now") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmRestore = true },
            enabled = restoreEnabled,
            modifier = Modifier.fillMaxWidth().testTag("btn-restore"),
        ) { Text("Restore now") }
        if (showReset) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().testTag("btn-reset-local"),
            ) {
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
                TextButton(
                    enabled = !replaceLocked,
                    onClick = {
                        replaceLocked = true
                        onConfirmEmptyRestore()
                    },
                ) { Text("Replace") }
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
    val deletingId = pendingDelete
    if (deletingId != null) {
        val metric = metrics.find { it.id == deletingId }
        if (metric != null) {
            val n = samples.count { it.metricId == deletingId }
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                text = { Text("Delete ${metric.label} and $n sample(s)? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteMetric(deletingId)
                            pendingDelete = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun MetricManagementRow(
    metric: MetricDef,
    last: Sample?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("metric-row-${metric.id}")
                .padding(end = 8.dp),
        ) {
            Text(metric.label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (last == null) "No samples" else formatSampleValues(metric, last),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onEdit, modifier = Modifier.testTag("metric-edit-${metric.id}")) {
            Text("Edit")
        }
        TextButton(onClick = onDelete, modifier = Modifier.testTag("metric-delete-${metric.id}")) {
            Text("Delete")
        }
    }
}
