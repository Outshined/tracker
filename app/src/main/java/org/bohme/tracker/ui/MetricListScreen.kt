package org.bohme.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample
import org.bohme.tracker.stats.formatSampleValues

@Composable
fun MetricListScreen(
    metrics: List<MetricDef>,
    samples: List<Sample>,
    zone: ZoneId,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(metrics, key = { it.id }) { metric ->
            val last = samples.filter { it.metricId == metric.id }.maxByOrNull { it.recordedAt }
            MetricRow(
                metric = metric,
                last = last,
                zone = zone,
                onOpenEntry = { onOpenEntry(metric.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MetricRow(
    metric: MetricDef,
    last: Sample?,
    zone: ZoneId,
    onOpenEntry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("metric-row-${metric.id}")
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
    }
}

private fun formatRecordedAtLocal(instant: Instant, zone: ZoneId): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(instant)
}
