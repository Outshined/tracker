package org.bohme.tracker.stats

import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample

fun formatSampleValues(metric: MetricDef, sample: Sample): String {
    val groups = mutableListOf<ValueGroup>()
    for (field in metric.fields) {
        val value = sample.values[field.id] ?: continue
        val last = groups.lastOrNull()
        if (last != null && last.unit == field.unit) {
            last.values.add(value)
        } else {
            groups.add(ValueGroup(field.unit, mutableListOf(value)))
        }
    }
    return groups.joinToString(", ") { group ->
        val nums = group.values.joinToString("/") { formatNumber(it) }
        if (group.unit.isEmpty()) nums else "$nums ${group.unit}"
    }
}

fun defaultSelectedMetricIds(metrics: List<MetricDef>, samples: List<Sample>): List<String> {
    val withData = metrics.map { it.id }.filter { id -> samples.any { it.metricId == id } }
    return withData.ifEmpty { metrics.map { it.id } }
}

fun selectionAfterDelete(
    selected: Set<String>,
    deletedId: String,
    metrics: List<MetricDef>,
    samples: List<Sample>,
): Set<String> {
    val next = selected - deletedId
    return if (next.isEmpty()) defaultSelectedMetricIds(metrics, samples).toSet() else next
}

private class ValueGroup(val unit: String, val values: MutableList<Double>)

private fun formatNumber(value: Double): String {
    val asLong = value.toLong()
    return if (value.isFinite() && value == asLong.toDouble()) asLong.toString() else value.toString()
}
