package org.bohme.tracker.stats

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources

class FormatTest {
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bp = BuiltInMetrics.ALL.first { it.id == "blood_pressure" }
    private val user = MetricDef(
        id = "user-1",
        label = "Steps",
        fields = listOf(FieldDef(id = "count", label = "Count", unit = "steps")),
    )

    @Test
    fun `formatSampleValues weight is 180 lb`() {
        assertEquals("180 lb", formatSampleValues(weight, sample("weight", mapOf("lb" to 180.0))))
    }

    @Test
    fun `formatSampleValues blank unit has no suffix`() {
        val steps = MetricDef(
            id = "steps",
            label = "Steps",
            fields = listOf(FieldDef(id = "count", label = "Count", unit = "")),
        )
        assertEquals("10", formatSampleValues(steps, sample("steps", mapOf("count" to 10.0))))
    }

    @Test
    fun `formatSampleValues BP all three is 118 slash 76 mmHg comma 72 bpm`() {
        assertEquals(
            "118/76 mmHg, 72 bpm",
            formatSampleValues(
                bp,
                sample("blood_pressure", mapOf("systolic" to 118.0, "diastolic" to 76.0, "pulse" to 72.0)),
            ),
        )
    }

    @Test
    fun `formatSampleValues BP missing pulse is 118 slash 76 mmHg`() {
        assertEquals(
            "118/76 mmHg",
            formatSampleValues(bp, sample("blood_pressure", mapOf("systolic" to 118.0, "diastolic" to 76.0))),
        )
    }

    @Test
    fun `formatSampleValues BP missing diastolic is 118 mmHg comma 72 bpm`() {
        assertEquals(
            "118 mmHg, 72 bpm",
            formatSampleValues(bp, sample("blood_pressure", mapOf("systolic" to 118.0, "pulse" to 72.0))),
        )
    }

    @Test
    fun `defaultSelectedMetricIds only user metric has samples returns that id`() {
        val catalog = BuiltInMetrics.ALL + user
        val samples = listOf(sample("user-1", mapOf("count" to 10.0)))
        assertEquals(listOf("user-1"), defaultSelectedMetricIds(catalog, samples))
    }

    @Test
    fun `defaultSelectedMetricIds none have samples returns all catalog ids`() {
        assertEquals(
            BuiltInMetrics.ALL.map { it.id },
            defaultSelectedMetricIds(BuiltInMetrics.ALL, emptyList()),
        )
    }

    @Test
    fun `defaultSelectedMetricIds empty catalog returns empty`() {
        assertEquals(emptyList<String>(), defaultSelectedMetricIds(emptyList(), emptyList()))
        assertEquals(
            emptyList<String>(),
            defaultSelectedMetricIds(emptyList(), listOf(sample("orphan", mapOf("x" to 1.0)))),
        )
    }

    @Test
    fun `selectionAfterDelete removing last selected id reruns defaultSelectedMetricIds`() {
        val metrics = BuiltInMetrics.ALL
        val samples = emptyList<Sample>()
        val next = selectionAfterDelete(setOf("weight"), "weight", metrics, samples)
        assertEquals(defaultSelectedMetricIds(metrics, samples).toSet(), next)
        assertEquals(metrics.map { it.id }.toSet(), next)
    }

    @Test
    fun `selectionAfterDelete keeps remaining selected ids`() {
        assertEquals(
            setOf("bhb"),
            selectionAfterDelete(setOf("weight", "bhb"), "weight", BuiltInMetrics.ALL, emptyList()),
        )
    }

    private fun sample(metricId: String, values: Map<String, Double>): Sample = Sample(
        id = "s-$metricId",
        metricId = metricId,
        recordedAt = t0,
        modifiedAt = t0,
        source = Sources.MANUAL,
        values = values,
    )
}
