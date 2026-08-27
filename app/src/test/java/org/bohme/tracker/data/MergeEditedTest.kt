package org.bohme.tracker.data

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MergeEditedTest {
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val recorded = Instant.parse("2026-08-24T08:00:00Z")
    private val editedAt = Instant.parse("2026-08-24T09:00:00Z")

    @Test
    fun `mergeEditedSample keeps note extra pulse and bluetooth source on encode`() {
        val existing = Sample(
            id = "s1",
            metricId = "weight",
            recordedAt = recorded,
            modifiedAt = Instant.parse("2026-08-24T08:00:01Z"),
            source = Sources.BLUETOOTH,
            values = mapOf("lb" to 170.0, "pulse" to 72.0),
            extras = mapOf("note" to JsonPrimitive("fasted")),
        )
        val merged = mergeEditedSample(
            existing = existing,
            metric = weight,
            parsedValues = mapOf("lb" to 180.0),
            recordedAt = editedAt,
            idForNew = "unused",
        )
        assertEquals("s1", merged.id)
        assertEquals(Sources.BLUETOOTH, merged.source)
        assertEquals(editedAt, merged.recordedAt)
        assertEquals(180.0, merged.values.getValue("lb"), 0.0)
        assertEquals(72.0, merged.values.getValue("pulse"), 0.0)
        assertEquals(JsonPrimitive("fasted"), merged.extras.getValue("note"))

        val encoded = JsonCodec.encode(
            Snapshot(
                exportedAt = Instant.parse("2026-08-24T12:00:00Z"),
                metrics = listOf(weight),
                samples = listOf(merged),
            ),
        )
        val sampleObj = Json.parseToJsonElement(encoded).jsonObject.getValue("samples").jsonArray.single().jsonObject
        assertEquals("fasted", sampleObj.getValue("note").jsonPrimitive.content)
        assertEquals(72.0, sampleObj.getValue("values").jsonObject.getValue("pulse").jsonPrimitive.double, 0.0)
        assertEquals(180.0, sampleObj.getValue("values").jsonObject.getValue("lb").jsonPrimitive.double, 0.0)
        assertEquals(Sources.BLUETOOTH, sampleObj.getValue("source").jsonPrimitive.content)
        val decoded = JsonCodec.decode(encoded).samples.single()
        assertEquals("fasted", decoded.extras.getValue("note").jsonPrimitive.content)
        assertEquals(72.0, decoded.values.getValue("pulse"), 0.0)
        assertEquals(Sources.BLUETOOTH, decoded.source)
    }

    @Test
    fun `mergeEditedSample new sample uses idForNew manual source and empty extras`() {
        val merged = mergeEditedSample(
            existing = null,
            metric = weight,
            parsedValues = mapOf("lb" to 180.0),
            recordedAt = recorded,
            idForNew = "new-id",
        )
        assertEquals("new-id", merged.id)
        assertEquals("weight", merged.metricId)
        assertEquals(Sources.MANUAL, merged.source)
        assertEquals(recorded, merged.recordedAt)
        assertEquals(recorded, merged.modifiedAt)
        assertEquals(mapOf("lb" to 180.0), merged.values)
        assertEquals(emptyMap<String, kotlinx.serialization.json.JsonElement>(), merged.extras)
    }

    @Test
    fun `mergeEditedMetric field extras and metric extras survive a label change`() {
        val existing = MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(
                FieldDef(
                    id = "lb",
                    label = "Weight",
                    unit = "lb",
                    extras = mapOf("hint" to JsonPrimitive("scale")),
                ),
            ),
            extras = mapOf("color" to JsonPrimitive("blue")),
        )
        val color = 0xFF2CA02C.toInt()
        val merged = mergeEditedMetric(
            existing,
            "Massa",
            listOf(FieldEdit("Peso", "kg", color)),
            100.0,
            300.0,
        )
        assertEquals("weight", merged.id)
        assertEquals("Massa", merged.label)
        assertEquals("lb", merged.fields.single().id)
        assertEquals("Peso", merged.fields.single().label)
        assertEquals("kg", merged.fields.single().unit)
        assertEquals(color, merged.fields.single().color)
        assertEquals(100.0, merged.graphMin!!, 0.0)
        assertEquals(300.0, merged.graphMax!!, 0.0)
        assertEquals(JsonPrimitive("scale"), merged.fields.single().extras.getValue("hint"))
        assertEquals(JsonPrimitive("blue"), merged.extras.getValue("color"))
    }

    @Test
    fun `mergeEditedMetric rejects fields size mismatch`() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeEditedMetric(weight, "Weight", emptyList(), 100.0, 300.0)
        }
    }
}
