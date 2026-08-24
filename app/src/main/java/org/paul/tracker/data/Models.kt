package org.paul.tracker.data

import java.time.Instant
import kotlinx.serialization.json.JsonElement

data class FieldDef(
    val id: String,
    val label: String,
    val unit: String,
    val extras: Map<String, JsonElement> = emptyMap(),
)

data class MetricDef(
    val id: String,
    val label: String,
    val fields: List<FieldDef>,
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

fun mergeEditedMetric(
    existing: MetricDef,
    label: String,
    fieldLabelUnits: List<Pair<String, String>>,
): MetricDef {
    require(fieldLabelUnits.size == existing.fields.size)
    val fields = existing.fields.zip(fieldLabelUnits) { old, (lab, unit) ->
        old.copy(label = lab, unit = unit)
    }
    return existing.copy(label = label, fields = fields)
}
