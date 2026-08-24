package org.paul.tracker.data

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

@OptIn(ExperimentalSerializationApi::class)
object JsonCodec {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val snapshotKnown = setOf("exportedAt", "metrics", "samples")
    private val metricKnown = setOf("id", "label", "fields")
    private val fieldKnown = setOf("id", "label", "unit")
    private val sampleKnown = setOf("id", "metricId", "recordedAt", "modifiedAt", "source", "values")

    fun decode(text: String): Snapshot {
        val element = json.parseToJsonElement(text)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("dump must be a JSON object")
        val exportedAt = parseInstant(obj["exportedAt"]) ?: Instant.EPOCH
        val metrics = lastWinsById(decodeMetrics(obj["metrics"])) { it.id }
        val samples = lastWinsById(decodeSamples(obj["samples"])) { it.id }
        return Snapshot(
            exportedAt = exportedAt,
            metrics = metrics,
            samples = samples,
            extras = extrasOf(obj, snapshotKnown),
        )
    }

    fun encode(snapshot: Snapshot): String {
        val known = linkedMapOf<String, JsonElement>(
            "exportedAt" to JsonPrimitive(snapshot.exportedAt.toString()),
            "metrics" to JsonArray(snapshot.metrics.map { encodeMetric(it) }),
            "samples" to JsonArray(snapshot.samples.map { encodeSample(it) }),
        )
        return json.encodeToString(JsonElement.serializer(), overlay(snapshot.extras, known))
    }

    private fun decodeMetrics(el: JsonElement?): List<MetricDef> {
        val arr = el as? JsonArray ?: return emptyList()
        val out = ArrayList<MetricDef>(arr.size)
        for (item in arr) {
            val obj = item as? JsonObject ?: continue
            val id = stringOrNull(obj["id"])?.takeUnless { it.isBlank() } ?: continue
            val label = stringOrNull(obj["label"])?.takeUnless { it.isBlank() } ?: continue
            out.add(
                MetricDef(
                    id = id,
                    label = label,
                    fields = lastWinsById(decodeFields(obj["fields"])) { it.id },
                    extras = extrasOf(obj, metricKnown),
                ),
            )
        }
        return out
    }

    private fun decodeFields(el: JsonElement?): List<FieldDef> {
        val arr = el as? JsonArray ?: return emptyList()
        val out = ArrayList<FieldDef>(arr.size)
        for (item in arr) {
            val obj = item as? JsonObject ?: continue
            val id = stringOrNull(obj["id"])?.takeUnless { it.isBlank() } ?: continue
            out.add(
                FieldDef(
                    id = id,
                    label = stringOrNull(obj["label"]) ?: "",
                    unit = stringOrNull(obj["unit"]) ?: "",
                    extras = extrasOf(obj, fieldKnown),
                ),
            )
        }
        return out
    }

    private fun decodeSamples(el: JsonElement?): List<Sample> {
        val arr = el as? JsonArray ?: return emptyList()
        val out = ArrayList<Sample>(arr.size)
        for (item in arr) {
            val obj = item as? JsonObject ?: continue
            val id = stringOrNull(obj["id"])?.takeUnless { it.isBlank() } ?: continue
            val metricId = stringOrNull(obj["metricId"])?.takeUnless { it.isBlank() } ?: continue
            val recordedAt = parseInstant(obj["recordedAt"]) ?: continue
            val modifiedAt = parseInstant(obj["modifiedAt"]) ?: recordedAt
            val source = stringOrNull(obj["source"]) ?: Sources.MANUAL
            out.add(
                Sample(
                    id = id,
                    metricId = metricId,
                    recordedAt = recordedAt,
                    modifiedAt = modifiedAt,
                    source = source,
                    values = decodeValues(obj["values"]),
                    extras = extrasOf(obj, sampleKnown),
                ),
            )
        }
        return out
    }

    private fun decodeValues(el: JsonElement?): Map<String, Double> {
        val obj = el as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, Double>(obj.size)
        for ((key, value) in obj) {
            val n = numericOrNull(value) ?: continue
            out[key] = n
        }
        return out
    }

    private fun encodeMetric(metric: MetricDef): JsonObject {
        val known = linkedMapOf<String, JsonElement>(
            "id" to JsonPrimitive(metric.id),
            "label" to JsonPrimitive(metric.label),
            "fields" to JsonArray(metric.fields.map { encodeField(it) }),
        )
        return overlay(metric.extras, known)
    }

    private fun encodeField(field: FieldDef): JsonObject {
        val known = linkedMapOf<String, JsonElement>(
            "id" to JsonPrimitive(field.id),
            "label" to JsonPrimitive(field.label),
            "unit" to JsonPrimitive(field.unit),
        )
        return overlay(field.extras, known)
    }

    private fun encodeSample(sample: Sample): JsonObject {
        val values = LinkedHashMap<String, JsonElement>()
        for ((key, value) in sample.values) {
            if (value.isFinite()) {
                values[key] = JsonPrimitive(value)
            }
        }
        val known = linkedMapOf<String, JsonElement>(
            "id" to JsonPrimitive(sample.id),
            "metricId" to JsonPrimitive(sample.metricId),
            "recordedAt" to JsonPrimitive(sample.recordedAt.toString()),
            "modifiedAt" to JsonPrimitive(sample.modifiedAt.toString()),
            "source" to JsonPrimitive(sample.source),
            "values" to JsonObject(values),
        )
        return overlay(sample.extras, known)
    }

    private fun overlay(
        extras: Map<String, JsonElement>,
        known: Map<String, JsonElement>,
    ): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(extras.size + known.size)
        map.putAll(extras)
        map.putAll(known)
        return JsonObject(map)
    }

    private fun extrasOf(obj: JsonObject, known: Set<String>): Map<String, JsonElement> {
        val extras = obj.filterKeys { it !in known }
        return if (extras.isEmpty()) emptyMap() else extras
    }

    private fun <T> lastWinsById(items: List<T>, id: (T) -> String): List<T> {
        if (items.size < 2) return items
        val map = LinkedHashMap<String, T>(items.size)
        for (item in items) {
            map[id(item)] = item
        }
        return if (map.size == items.size) items else map.values.toList()
    }

    private fun parseInstant(el: JsonElement?): Instant? {
        val text = stringOrNull(el) ?: return null
        return try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun stringOrNull(el: JsonElement?): String? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull || !p.isString) return null
        return p.content
    }

    private fun numericOrNull(el: JsonElement): Double? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        val d = if (p.isString) {
            try {
                p.content.toDouble()
            } catch (_: NumberFormatException) {
                return null
            }
        } else {
            if (p.booleanOrNull != null) return null
            p.doubleOrNull ?: return null
        }
        return if (d.isFinite()) d else null
    }
}
