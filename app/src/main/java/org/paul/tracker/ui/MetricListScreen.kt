package org.paul.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.paul.tracker.data.MetricDef
import org.paul.tracker.data.Sample
import org.paul.tracker.stats.formatSampleValues

@Composable
fun MetricListScreen(
    metrics: List<MetricDef>,
    samples: List<Sample>,
    zone: ZoneId,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<MetricDef?>(null) }
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(metrics, key = { it.id }) { metric ->
            val last = samples.filter { it.metricId == metric.id }.maxByOrNull { it.recordedAt }
            MetricRow(
                metric = metric,
                last = last,
                zone = zone,
                onOpenEntry = { onOpenEntry(metric.id) },
                onEdit = { onEdit(metric.id) },
                onDelete = { pendingDelete = metric },
            )
            HorizontalDivider()
        }
    }
    val deleting = pendingDelete
    if (deleting != null) {
        val n = samples.count { it.metricId == deleting.id }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = { Text("Delete ${deleting.label} and $n sample(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleting.id)
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

@Composable
fun EntryStubScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(text = label, modifier = modifier.padding(16.dp))
}

@Composable
private fun MetricRow(
    metric: MetricDef,
    last: Sample?,
    zone: ZoneId,
    onOpenEntry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenEntry)
                .padding(end = 8.dp),
        ) {
            Text(metric.label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (last == null) "No samples" else formatSampleValues(metric, last),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (last != null) {
                Text(
                    formatRecordedAtLocal(last.recordedAt, zone),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

private fun formatRecordedAtLocal(instant: Instant, zone: ZoneId): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(instant)
}
