package org.bohme.tracker.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources

class TodayTest {
    private val utc = ZoneOffset.UTC
    private val ny = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 8, 24)
    private val yesterdaySample = sample(
        id = "yest",
        metricId = "weight",
        recordedAt = Instant.parse("2026-08-23T20:00:00Z"),
        values = mapOf("lb" to 170.0),
    )
    private val todayMorning = sample(
        id = "am",
        metricId = "weight",
        recordedAt = Instant.parse("2026-08-24T08:00:00Z"),
        values = mapOf("lb" to 180.0),
    )
    private val todayAfternoon = sample(
        id = "pm",
        metricId = "weight",
        recordedAt = Instant.parse("2026-08-24T16:00:00Z"),
        values = mapOf("lb" to 181.0),
    )
    private val glucoseToday = sample(
        id = "glu",
        metricId = "glucose",
        recordedAt = Instant.parse("2026-08-24T12:00:00Z"),
        values = mapOf("mg_dl" to 95.0),
    )
    private val orphan = sample(
        id = "orphan",
        metricId = "no-such",
        recordedAt = Instant.parse("2026-08-24T10:00:00Z"),
        values = mapOf("x" to 1.0),
    )

    @Test
    fun `sampleOnLocalDate excludes yesterday`() {
        val found = sampleOnLocalDate(
            listOf(yesterdaySample, todayMorning),
            "weight",
            today,
            utc,
        )
        assertEquals("am", found?.id)
        assertNull(
            sampleOnLocalDate(listOf(yesterdaySample), "weight", today, utc),
        )
    }

    @Test
    fun `sampleOnLocalDate two today picks newest recordedAt`() {
        val found = sampleOnLocalDate(
            listOf(todayAfternoon, todayMorning, yesterdaySample),
            "weight",
            today,
            utc,
        )
        assertEquals("pm", found?.id)
        assertEquals(
            "pm",
            sampleOnLocalDate(
                listOf(todayMorning, todayAfternoon),
                "weight",
                today,
                utc,
            )?.id,
        )
    }

    @Test
    fun `sampleOnLocalDate ignores other metric and orphan`() {
        val samples = listOf(glucoseToday, orphan, todayMorning)
        assertEquals("am", sampleOnLocalDate(samples, "weight", today, utc)?.id)
        assertEquals("glu", sampleOnLocalDate(samples, "glucose", today, utc)?.id)
        assertNull(sampleOnLocalDate(samples, "bhb", today, utc))
        assertNull(sampleOnLocalDate(emptyList(), "weight", today, utc))
    }

    @Test
    fun `sampleOnLocalDate TZ date line`() {
        // 2026-08-24 00:30 in NY is still 2026-08-24 04:30 UTC.
        val afterMidnightNy = LocalDate.of(2026, 8, 24).atTime(0, 30).atZone(ny).toInstant()
        val beforeMidnightNy = LocalDate.of(2026, 8, 23).atTime(23, 30).atZone(ny).toInstant()
        val after = sample("after", "weight", afterMidnightNy, mapOf("lb" to 1.0))
        val before = sample("before", "weight", beforeMidnightNy, mapOf("lb" to 2.0))
        assertEquals("after", sampleOnLocalDate(listOf(after, before), "weight", today, ny)?.id)
        assertEquals("before", sampleOnLocalDate(listOf(after, before), "weight", LocalDate.of(2026, 8, 23), ny)?.id)
        // Both instants fall on 2026-08-24 in UTC.
        assertEquals(
            "after",
            sampleOnLocalDate(listOf(before, after), "weight", today, utc)?.id,
        )
        assertEquals(LocalDate.of(2026, 8, 24), localDateOf(afterMidnightNy, utc))
        assertEquals(LocalDate.of(2026, 8, 24), localDateOf(afterMidnightNy, ny))
        assertEquals(LocalDate.of(2026, 8, 23), localDateOf(beforeMidnightNy, ny))
    }

    @Test
    fun `todayFieldKey is metric slash field`() {
        assertEquals("weight/lb", todayFieldKey("weight", "lb"))
        assertEquals("blood_pressure/systolic", todayFieldKey("blood_pressure", "systolic"))
    }

    private fun sample(
        id: String,
        metricId: String,
        recordedAt: Instant,
        values: Map<String, Double>,
    ): Sample = Sample(
        id = id,
        metricId = metricId,
        recordedAt = recordedAt,
        modifiedAt = recordedAt,
        source = Sources.MANUAL,
        values = values,
    )
}
