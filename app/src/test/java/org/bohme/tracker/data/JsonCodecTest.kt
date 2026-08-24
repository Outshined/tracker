package org.bohme.tracker.data

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCodecTest {
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private val t1 = Instant.parse("2026-08-24T08:05:00Z")
    private val exported = Instant.parse("2026-08-24T12:00:00Z")

    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }

    @Test
    fun `roundtrip four built-ins plus BP and weight with exact field names`() {
        val snapshot = Snapshot(
            exportedAt = exported,
            metrics = BuiltInMetrics.ALL,
            samples = listOf(
                Sample(
                    id = "550e8400-e29b-41d4-a716-446655440000",
                    metricId = "weight",
                    recordedAt = t0,
                    modifiedAt = Instant.parse("2026-08-24T08:00:01Z"),
                    source = Sources.MANUAL,
                    values = mapOf("lb" to 180.0),
                ),
                Sample(
                    id = "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                    metricId = "blood_pressure",
                    recordedAt = t1,
                    modifiedAt = t1,
                    source = Sources.MANUAL,
                    values = mapOf("systolic" to 118.0, "diastolic" to 76.0, "pulse" to 72.0),
                ),
            ),
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(snapshot))
        assertEquals(listOf("weight", "bhb", "glucose", "blood_pressure"), decoded.metrics.map { it.id })
        assertEquals(BuiltInMetrics.ALL, decoded.metrics)
        val byId = decoded.metrics.associateBy { it.id }
        assertEquals(listOf("lb"), byId.getValue("weight").fields.map { it.id })
        assertEquals(listOf("mmol_l"), byId.getValue("bhb").fields.map { it.id })
        assertEquals(listOf("mg_dl"), byId.getValue("glucose").fields.map { it.id })
        assertEquals(listOf("systolic", "diastolic", "pulse"), byId.getValue("blood_pressure").fields.map { it.id })
        val weightSample = decoded.samples.first { it.metricId == "weight" }
        assertEquals(setOf("lb"), weightSample.values.keys)
        val bpSample = decoded.samples.first { it.metricId == "blood_pressure" }
        assertEquals(setOf("systolic", "diastolic", "pulse"), bpSample.values.keys)
        assertEquals(180.0, weightSample.values.getValue("lb"), 0.0)
        assertEquals(118.0, bpSample.values.getValue("systolic"), 0.0)
        assertEquals(76.0, bpSample.values.getValue("diastolic"), 0.0)
        assertEquals(72.0, bpSample.values.getValue("pulse"), 0.0)
        assertEquals(exported, decoded.exportedAt)
    }

    @Test
    fun `extra top-level _extra round-trips via Snapshot extras and encode`() {
        val decoded = JsonCodec.decode(
            """
            {
              "exportedAt": "2026-08-24T12:00:00Z",
              "metrics": [],
              "samples": [],
              "_extra": 1
            }
            """.trimIndent(),
        )
        assertEquals(1, decoded.extras.getValue("_extra").jsonPrimitive.int)
        val encoded = Json.parseToJsonElement(JsonCodec.encode(decoded)).jsonObject
        assertEquals(1, encoded.getValue("_extra").jsonPrimitive.int)
        assertTrue(encoded.containsKey("exportedAt"))
        assertTrue(encoded.containsKey("metrics"))
        assertTrue(encoded.containsKey("samples"))
    }

    @Test
    fun `extra key on sample metric and field round-trips via extras`() {
        val decoded = JsonCodec.decode(
            """
            {
              "exportedAt": "2026-08-24T12:00:00Z",
              "metrics": [
                {
                  "id": "weight",
                  "label": "Weight",
                  "color": "blue",
                  "fields": [
                    { "id": "lb", "label": "Weight", "unit": "lb", "hint": "scale" }
                  ]
                }
              ],
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "modifiedAt": "2026-08-24T08:00:00Z",
                  "source": "manual",
                  "values": { "lb": 180.0 },
                  "device": "scale"
                }
              ]
            }
            """.trimIndent(),
        )
        val metric = decoded.metrics.single()
        assertEquals("blue", metric.extras.getValue("color").jsonPrimitive.content)
        assertEquals("scale", metric.fields.single().extras.getValue("hint").jsonPrimitive.content)
        assertEquals("scale", decoded.samples.single().extras.getValue("device").jsonPrimitive.content)
        val encoded = Json.parseToJsonElement(JsonCodec.encode(decoded)).jsonObject
        val metricObj = encoded.getValue("metrics").jsonArray.single().jsonObject
        assertEquals("blue", metricObj.getValue("color").jsonPrimitive.content)
        assertEquals("scale", metricObj.getValue("fields").jsonArray.single().jsonObject.getValue("hint").jsonPrimitive.content)
        assertEquals(
            "scale",
            encoded.getValue("samples").jsonArray.single().jsonObject.getValue("device").jsonPrimitive.content,
        )
    }

    @Test
    fun `extra values pulse preserved even if not in FieldDef`() {
        val decoded = JsonCodec.decode(
            """
            {
              "metrics": [
                { "id": "weight", "label": "Weight", "fields": [{ "id": "lb", "label": "Weight", "unit": "lb" }] }
              ],
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 180.0, "pulse": 72.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        val sample = decoded.samples.single()
        assertEquals(180.0, sample.values.getValue("lb"), 0.0)
        assertEquals(72.0, sample.values.getValue("pulse"), 0.0)
        assertEquals(weight.fields.map { it.id }, listOf("lb"))
        val again = JsonCodec.decode(JsonCodec.encode(decoded)).samples.single()
        assertEquals(72.0, again.values.getValue("pulse"), 0.0)
        val valuesObj = Json.parseToJsonElement(JsonCodec.encode(decoded))
            .jsonObject.getValue("samples").jsonArray.single().jsonObject.getValue("values").jsonObject
        assertEquals(72.0, valuesObj.getValue("pulse").jsonPrimitive.double, 0.0)
    }

    @Test
    fun `sibling sample note fasted round-trips and is not placed in values`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 180.0 },
                  "note": "fasted"
                }
              ]
            }
            """.trimIndent(),
        )
        val sample = decoded.samples.single()
        assertEquals("fasted", sample.extras.getValue("note").jsonPrimitive.content)
        assertFalse(sample.values.containsKey("note"))
        val encodedSample = Json.parseToJsonElement(JsonCodec.encode(decoded))
            .jsonObject.getValue("samples").jsonArray.single().jsonObject
        assertEquals("fasted", encodedSample.getValue("note").jsonPrimitive.content)
        assertFalse(encodedSample.getValue("values").jsonObject.containsKey("note"))
        assertEquals(180.0, encodedSample.getValue("values").jsonObject.getValue("lb").jsonPrimitive.double, 0.0)
    }

    @Test
    fun `missing samples yields empty list`() {
        val decoded = JsonCodec.decode("""{"exportedAt":"2026-08-24T12:00:00Z","metrics":[]}""")
        assertEquals(emptyList<Sample>(), decoded.samples)
        assertEquals(emptyList<MetricDef>(), decoded.metrics)
        val emptyObject = JsonCodec.decode("{}")
        assertEquals(emptyList<Sample>(), emptyObject.samples)
        assertEquals(Instant.EPOCH, emptyObject.exportedAt)
    }

    @Test
    fun `sample without id skipped siblings kept`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 1.0 }
                },
                {
                  "id": "kept",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T09:00:00Z",
                  "values": { "lb": 2.0 }
                },
                {
                  "id": "   ",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T10:00:00Z",
                  "values": { "lb": 3.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("kept"), decoded.samples.map { it.id })
        assertEquals(2.0, decoded.samples.single().values.getValue("lb"), 0.0)
    }

    @Test
    fun `non-numeric values lb skipped other fields kept`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": "heavy", "pulse": 72, "note": null }
                }
              ]
            }
            """.trimIndent(),
        )
        val values = decoded.samples.single().values
        assertFalse(values.containsKey("lb"))
        assertFalse(values.containsKey("note"))
        assertEquals(72.0, values.getValue("pulse"), 0.0)
    }

    @Test
    fun `numeric string 180_0 accepted via toDouble comma 180_0 skipped`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": "180.0", "kg": "180,0" }
                }
              ]
            }
            """.trimIndent(),
        )
        val values = decoded.samples.single().values
        assertEquals(180.0, values.getValue("lb"), 0.0)
        assertFalse(values.containsKey("kg"))
    }

    @Test
    fun `modifiedAt missing equals recordedAt`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 180.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        val sample = decoded.samples.single()
        assertEquals(t0, sample.recordedAt)
        assertEquals(sample.recordedAt, sample.modifiedAt)
    }

    @Test
    fun `unknown source preserved`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "source": "watch",
                  "values": { "lb": 180.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("watch", decoded.samples.single().source)
        val again = JsonCodec.decode(JsonCodec.encode(decoded)).samples.single()
        assertEquals("watch", again.source)
    }

    @Test
    fun `invalid document array empty string and HTML throws`() {
        assertThrows(Exception::class.java) { JsonCodec.decode("[]") }
        assertThrows(Exception::class.java) { JsonCodec.decode("") }
        assertThrows(Exception::class.java) { JsonCodec.decode("<html>not json</html>") }
    }

    @Test
    fun `pretty-print contains newlines`() {
        val encoded = JsonCodec.encode(
            Snapshot(exportedAt = exported, metrics = BuiltInMetrics.ALL, samples = emptyList()),
        )
        assertTrue(encoded.contains("\n"))
        assertTrue(encoded.contains("\n  "))
    }

    @Test
    fun `duplicate sample ids last-wins`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 100.0 }
                },
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T09:00:00Z",
                  "values": { "lb": 200.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, decoded.samples.size)
        assertEquals(200.0, decoded.samples.single().values.getValue("lb"), 0.0)
        assertEquals(Instant.parse("2026-08-24T09:00:00Z"), decoded.samples.single().recordedAt)
    }

    @Test
    fun `duplicate metric ids last-wins`() {
        val decoded = JsonCodec.decode(
            """
            {
              "metrics": [
                { "id": "weight", "label": "First", "fields": [{ "id": "lb", "label": "A", "unit": "kg" }] },
                { "id": "weight", "label": "Weight", "fields": [{ "id": "lb", "label": "Weight", "unit": "lb" }] }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, decoded.metrics.size)
        val metric = decoded.metrics.single()
        assertEquals("Weight", metric.label)
        assertEquals("lb", metric.fields.single().unit)
        assertEquals("Weight", metric.fields.single().label)
    }

    @Test
    fun `duplicate field ids in one metric last-wins`() {
        val decoded = JsonCodec.decode(
            """
            {
              "metrics": [
                {
                  "id": "weight",
                  "label": "Weight",
                  "fields": [
                    { "id": "lb", "label": "First", "unit": "kg" },
                    { "id": "lb", "label": "Weight", "unit": "lb" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val fields = decoded.metrics.single().fields
        assertEquals(1, fields.size)
        assertEquals("Weight", fields.single().label)
        assertEquals("lb", fields.single().unit)
    }

    @Test
    fun `encode skips non-finite values if present in memory`() {
        val snapshot = Snapshot(
            exportedAt = exported,
            metrics = listOf(weight),
            samples = listOf(
                Sample(
                    id = "s1",
                    metricId = "weight",
                    recordedAt = t0,
                    modifiedAt = t0,
                    source = Sources.MANUAL,
                    values = mapOf(
                        "lb" to 180.0,
                        "nan" to Double.NaN,
                        "inf" to Double.POSITIVE_INFINITY,
                        "ninf" to Double.NEGATIVE_INFINITY,
                    ),
                ),
            ),
        )
        val encoded = JsonCodec.encode(snapshot)
        assertFalse(encoded.contains("NaN"))
        assertFalse(encoded.contains("Infinity"))
        val values = Json.parseToJsonElement(encoded)
            .jsonObject.getValue("samples").jsonArray.single().jsonObject.getValue("values").jsonObject
        assertEquals(setOf("lb"), values.keys)
        assertEquals(180.0, values.getValue("lb").jsonPrimitive.double, 0.0)
        val decoded = JsonCodec.decode(encoded)
        assertEquals(setOf("lb"), decoded.samples.single().values.keys)
    }

    @Test
    fun `known key in extras collision known wins on encode`() {
        val snapshot = Snapshot(
            exportedAt = exported,
            metrics = listOf(weight),
            samples = listOf(
                Sample(
                    id = "real-id",
                    metricId = "weight",
                    recordedAt = t0,
                    modifiedAt = t0,
                    source = Sources.MANUAL,
                    values = mapOf("lb" to 180.0),
                    extras = mapOf("id" to JsonPrimitive("from-extras")),
                ),
            ),
            extras = mapOf(
                "exportedAt" to JsonPrimitive("should-not-win"),
                "samples" to JsonPrimitive(0),
            ),
        )
        val encoded = Json.parseToJsonElement(JsonCodec.encode(snapshot)).jsonObject
        assertEquals("2026-08-24T12:00:00Z", encoded.getValue("exportedAt").jsonPrimitive.content)
        assertTrue(encoded.getValue("samples") is kotlinx.serialization.json.JsonArray)
        assertEquals(
            "real-id",
            encoded.getValue("samples").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content,
        )
        val decoded = JsonCodec.decode(JsonCodec.encode(snapshot))
        assertEquals(exported, decoded.exportedAt)
        assertEquals("real-id", decoded.samples.single().id)
    }

    @Test
    fun `missing source defaults to manual`() {
        val decoded = JsonCodec.decode(
            """
            {
              "samples": [
                {
                  "id": "s1",
                  "metricId": "weight",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "lb": 180.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(Sources.MANUAL, decoded.samples.single().source)
    }

    @Test
    fun `orphan sample with unknown metricId is kept`() {
        val decoded = JsonCodec.decode(
            """
            {
              "metrics": [],
              "samples": [
                {
                  "id": "orphan",
                  "metricId": "gone",
                  "recordedAt": "2026-08-24T08:00:00Z",
                  "values": { "x": 1.0 }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("gone", decoded.samples.single().metricId)
        assertEquals(1.0, decoded.samples.single().values.getValue("x"), 0.0)
    }
}
