package org.bohme.tracker

import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources
import org.bohme.tracker.stats.formatLocaleNumber
import org.bohme.tracker.stats.parseLocaleNumber
import org.bohme.tracker.stats.parseRequiredFields
import org.bohme.tracker.ui.samplesNewestFirst

class EntryParseTest {
    private val us = Locale.US
    private val de = Locale.GERMANY
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bp = BuiltInMetrics.ALL.first { it.id == "blood_pressure" }

    @Test
    fun `locale number parse US decimal`() {
        assertEquals(180.0, parseLocaleNumber("180.0", us)!!, 0.0)
        assertEquals(180.5, parseLocaleNumber("180.5", us)!!, 0.0)
        assertEquals(180.0, parseLocaleNumber("180", us)!!, 0.0)
        assertEquals(-1.5, parseLocaleNumber("-1.5", us)!!, 0.0)
    }

    @Test
    fun `locale number parse German decimal comma`() {
        assertEquals(180.0, parseLocaleNumber("180,0", de)!!, 0.0)
        assertEquals(180.5, parseLocaleNumber("180,5", de)!!, 0.0)
    }

    @Test
    fun `entry parse uses locale unlike codec English toDouble`() {
        assertEquals(180.0, parseLocaleNumber("180,0", de)!!, 0.0)
        val usParsed = parseLocaleNumber("180,0", us)
        val nf = NumberFormat.getNumberInstance(us).parse("180,0")
        if (usParsed == null) {
            assertNull(nf)
        } else {
            assertNotNull(nf)
            assertEquals(nf!!.toDouble(), usParsed, 0.0)
        }
    }

    @Test
    fun `reject empty and whitespace`() {
        assertNull(parseLocaleNumber("", us))
        assertNull(parseLocaleNumber("   ", us))
        assertNull(parseRequiredFields(weight.fields, emptyMap(), us))
        assertNull(parseRequiredFields(weight.fields, mapOf("lb" to ""), us))
        assertNull(parseRequiredFields(weight.fields, mapOf("lb" to "  "), us))
    }

    @Test
    fun `US grouping and trailing junk`() {
        assertEquals(1800.0, parseLocaleNumber("1,800", us)!!, 0.0)
        assertEquals(1800.0, parseLocaleNumber(" 1,800 ", us)!!, 0.0)
        assertNull(parseLocaleNumber("180kg", us))
        assertNull(parseLocaleNumber("180 kg", us))
        assertNull(parseLocaleNumber("180x", us))
    }

    @Test
    fun `formatLocaleNumber US round-trip`() {
        val values = listOf(180.0, 180.5, 1.0, 0.0, -1.5, 1800.0)
        for (value in values) {
            val text = formatLocaleNumber(value, us)
            assertEquals(value, parseLocaleNumber(text, us)!!, 0.0)
        }
    }

    @Test
    fun `reject non-finite`() {
        assertNull(parseLocaleNumber("NaN", us))
        assertNull(parseLocaleNumber("∞", us))
        assertNull(parseRequiredFields(weight.fields, mapOf("lb" to "NaN"), us))
        assertNull(parseRequiredFields(weight.fields, mapOf("lb" to "∞"), us))
    }

    @Test
    fun `BP three-field map in FieldDef order`() {
        val parsed = parseRequiredFields(
            bp.fields,
            mapOf(
                "pulse" to "72",
                "systolic" to "118",
                "diastolic" to "76",
            ),
            us,
        )
        assertNotNull(parsed)
        assertEquals(listOf("systolic", "diastolic", "pulse"), parsed!!.keys.toList())
        assertEquals(118.0, parsed.getValue("systolic"), 0.0)
        assertEquals(76.0, parsed.getValue("diastolic"), 0.0)
        assertEquals(72.0, parsed.getValue("pulse"), 0.0)
    }

    @Test
    fun `BP missing pulse rejected`() {
        assertNull(
            parseRequiredFields(
                bp.fields,
                mapOf("systolic" to "118", "diastolic" to "76"),
                us,
            ),
        )
    }

    @Test
    fun `no systolic greater than diastolic rule`() {
        val parsed = parseRequiredFields(
            bp.fields,
            mapOf("systolic" to "80", "diastolic" to "120", "pulse" to "70"),
            us,
        )
        assertNotNull(parsed)
        assertEquals(80.0, parsed!!.getValue("systolic"), 0.0)
        assertEquals(120.0, parsed.getValue("diastolic"), 0.0)
    }

    @Test
    fun `samplesNewestFirst is recordedAt descending for that metric`() {
        val t0 = Instant.parse("2026-08-24T08:00:00Z")
        val t1 = Instant.parse("2026-08-24T09:00:00Z")
        val t2 = Instant.parse("2026-08-24T07:00:00Z")
        val samples = listOf(
            sample("a", "weight", t0),
            sample("b", "bhb", t1),
            sample("c", "weight", t1),
            sample("d", "weight", t2),
        )
        assertEquals(listOf("c", "a", "d"), samplesNewestFirst(samples, "weight").map { it.id })
    }

    private fun sample(id: String, metricId: String, recordedAt: Instant): Sample = Sample(
        id = id,
        metricId = metricId,
        recordedAt = recordedAt,
        modifiedAt = recordedAt,
        source = Sources.MANUAL,
        values = mapOf("x" to 1.0),
    )
}
