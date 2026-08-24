package org.paul.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.paul.tracker.data.MetricDef
import org.paul.tracker.data.Sample
import org.paul.tracker.data.Sources
import org.paul.tracker.stats.formatSampleValues

fun samplesNewestFirst(samples: List<Sample>, metricId: String): List<Sample> =
    samples.filter { it.metricId == metricId }.sortedByDescending { it.recordedAt }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    metric: MetricDef,
    samples: List<Sample>,
    fieldText: Map<String, String>,
    recordedAt: Instant,
    zone: ZoneId,
    error: String?,
    onFieldChange: (String, String) -> Unit,
    onRecordedAtChange: (Instant) -> Unit,
    onSave: () -> Unit,
    onNewSample: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Sample?>(null) }
    val local = recordedAt.atZone(zone)
    val ordered = samplesNewestFirst(samples, metric.id)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        item {
            Text(metric.label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            metric.fields.forEach { field ->
                OutlinedTextField(
                    value = fieldText[field.id].orEmpty(),
                    onValueChange = { onFieldChange(field.id, it) },
                    label = { Text(field.label) },
                    suffix = if (field.unit.isEmpty()) {
                        null
                    } else {
                        { Text(field.unit) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDate = true }) {
                    Text(local.toLocalDate().toString())
                }
                TextButton(onClick = { showTime = true }) {
                    Text(TIME_FORMAT.format(local))
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            TextButton(onClick = onNewSample) { Text("New sample") }
            Spacer(Modifier.height(16.dp))
        }
        items(ordered, key = { it.id }) { sample ->
            SampleRow(
                metric = metric,
                sample = sample,
                zone = zone,
                onEdit = { onEdit(sample.id) },
                onDelete = { pendingDelete = sample },
            )
            HorizontalDivider()
        }
    }
    if (showDate) {
        val dateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onRecordedAtChange(newDate.atTime(local.toLocalTime()).atZone(zone).toInstant())
                        }
                        showDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRecordedAtChange(
                            local.withHour(timeState.hour)
                                .withMinute(timeState.minute)
                                .withSecond(0)
                                .withNano(0)
                                .toInstant(),
                        )
                        showTime = false
                    },
                ) { Text("OK") }
            },
            title = { Text("Time") },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancel") }
            },
        ) {
            TimePicker(state = timeState)
        }
    }
    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = { Text("Delete this sample? This cannot be undone.") },
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
private fun SampleRow(
    metric: MetricDef,
    sample: Sample,
    zone: ZoneId,
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
                .clickable(onClick = onEdit)
                .padding(end = 8.dp),
        ) {
            Text(formatSampleValues(metric, sample), style = MaterialTheme.typography.bodyMedium)
            Text(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(sample.recordedAt),
                style = MaterialTheme.typography.bodySmall,
            )
            if (sample.source != Sources.MANUAL) {
                Text(sample.source, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
