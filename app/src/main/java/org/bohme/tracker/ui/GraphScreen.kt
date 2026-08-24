package org.bohme.tracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample
import org.bohme.tracker.stats.AverageMode
import org.bohme.tracker.stats.RangePreset
import org.bohme.tracker.stats.bucketMeans
import org.bohme.tracker.stats.filterFieldPoints
import org.bohme.tracker.stats.minSelectedRecordedAt
import org.bohme.tracker.stats.rangeBounds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraphScreen(
    metrics: List<MetricDef>,
    samples: List<Sample>,
    selectedIds: Set<String>,
    rangePreset: RangePreset,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    averageMode: AverageMode,
    now: Instant,
    zone: ZoneId,
    onToggleMetric: (String) -> Unit,
    onRangePreset: (RangePreset) -> Unit,
    onCustomFrom: (LocalDate) -> Unit,
    onCustomTo: (LocalDate) -> Unit,
    onAverageMode: (AverageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogIds = metrics.map { it.id }.toSet()
    val minAt = minSelectedRecordedAt(samples, selectedIds, catalogIds)
    val customInvalid =
        rangePreset == RangePreset.Custom &&
            (customFrom == null || customTo == null || customFrom > customTo)
    val bounds =
        if (customInvalid) {
            null
        } else {
            rangeBounds(rangePreset, now, zone, customFrom, customTo, minAt)
        }
    val today = now.atZone(zone).toLocalDate()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            metrics.forEach { metric ->
                FilterChip(
                    selected = metric.id in selectedIds,
                    onClick = { onToggleMetric(metric.id) },
                    label = { Text(metric.label) },
                    modifier = Modifier.testTag("chip-metric-${metric.id}"),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RangePreset.entries.forEach { preset ->
                FilterChip(
                    selected = rangePreset == preset,
                    onClick = { onRangePreset(preset) },
                    label = { Text(rangeChipLabel(preset)) },
                    modifier = Modifier.testTag("chip-range-${preset.name}"),
                )
            }
        }
        if (rangePreset == RangePreset.Custom) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GraphDateButton("From", customFrom, today, onCustomFrom, "btn-custom-from")
                GraphDateButton("To", customTo, today, onCustomTo, "btn-custom-to")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AverageMode.entries.forEach { mode ->
                FilterChip(
                    selected = averageMode == mode,
                    onClick = { onAverageMode(mode) },
                    label = { Text(mode.name) },
                    modifier = Modifier.testTag("chip-average-${mode.name}"),
                )
            }
        }
        val start = bounds?.first
        val end = bounds?.second
        metrics.filter { it.id in selectedIds }.forEach { metric ->
            Spacer(Modifier.height(16.dp))
            Text(metric.label, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metric.fields.forEach { field ->
                    val color = Color(seriesColor(field.id))
                    val unit = if (field.unit.isEmpty()) "" else " ${field.unit}"
                    Text(
                        text = "${field.label}$unit",
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val raw =
                if (start != null && end != null) {
                    metric.fields.map { field ->
                        Series(
                            id = field.id,
                            label = field.label,
                            unit = field.unit,
                            points = filterFieldPoints(samples, metric.id, field.id, start, end),
                        )
                    }
                } else {
                    metric.fields.map { field ->
                        Series(field.id, field.label, field.unit, emptyList())
                    }
                }
            val means =
                if (start != null && end != null && averageMode != AverageMode.Off) {
                    raw.map { series ->
                        series.copy(points = bucketMeans(series.points, averageMode, zone, start))
                    }
                } else {
                    emptyList()
                }
            Chart(
                metricId = metric.id,
                raw = raw,
                means = means,
                start = start ?: Instant.EPOCH,
                end = end ?: Instant.EPOCH,
            )
        }
    }
}

private fun rangeChipLabel(preset: RangePreset): String =
    when (preset) {
        RangePreset.D7 -> "7d"
        RangePreset.D30 -> "30d"
        RangePreset.D90 -> "90d"
        RangePreset.Y1 -> "1y"
        RangePreset.All -> "All"
        RangePreset.Custom -> "Custom"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphDateButton(
    label: String,
    date: LocalDate?,
    today: LocalDate,
    onDate: (LocalDate) -> Unit,
    tag: String,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) {
        Text(if (date != null) "$label $date" else label)
    }
    if (open) {
        val initial = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        open = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
