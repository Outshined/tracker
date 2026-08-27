package org.bohme.tracker.data

import java.time.Instant
import kotlinx.serialization.json.JsonElement

data class FieldDef(
    val id: String,
    val label: String,
    val unit: String,
    val color: Int? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
)

data class MetricDef(
    val id: String,
    val label: String,
    val fields: List<FieldDef>,
    val graphMin: Double? = null,
    val graphMax: Double? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
)

data class Sample(
    val id: String,
    val metricId: String,
    val recordedAt: Instant,
    val modifiedAt: Instant,
    val source: String,
    val values: Map<String, Double>,
    val extras: Map<String, JsonElement> = emptyMap(),
)

data class Snapshot(
    val exportedAt: Instant,
    val metrics: List<MetricDef>,
    val samples: List<Sample>,
    val extras: Map<String, JsonElement> = emptyMap(),
)

object Sources {
    const val MANUAL = "manual"
    const val BLUETOOTH = "bluetooth"
}

sealed class LoadState {
    data object Loading : LoadState()
    data object Ready : LoadState()
    data class Corrupt(val message: String) : LoadState()
}

fun mergeEditedSample(
    existing: Sample?,
    metric: MetricDef,
    parsedValues: Map<String, Double>,
    recordedAt: Instant,
    idForNew: String,
): Sample {
    if (existing == null) {
        return Sample(
            id = idForNew,
            metricId = metric.id,
            recordedAt = recordedAt,
            modifiedAt = recordedAt,
            source = Sources.MANUAL,
            values = parsedValues,
            extras = emptyMap(),
        )
    }
    val fieldIds = metric.fields.map { it.id }.toSet()
    val keptExtrasValues = existing.values.filterKeys { it !in fieldIds }
    return existing.copy(
        recordedAt = recordedAt,
        values = keptExtrasValues + parsedValues,
    )
}

data class FieldEdit(val label: String, val unit: String, val color: Int)

fun mergeEditedMetric(
    existing: MetricDef,
    label: String,
    fields: List<FieldEdit>,
    graphMin: Double,
    graphMax: Double,
): MetricDef {
    require(fields.size == existing.fields.size)
    val merged = existing.fields.zip(fields) { old, edit ->
        old.copy(label = edit.label, unit = edit.unit, color = edit.color)
    }
    return existing.copy(label = label, fields = merged, graphMin = graphMin, graphMax = graphMax)
}
