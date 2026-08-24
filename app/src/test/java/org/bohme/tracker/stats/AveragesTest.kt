package org.bohme.tracker.stats

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources
import org.bohme.tracker.ui.Px
import org.bohme.tracker.ui.Series
import org.bohme.tracker.ui.project

class AveragesTest {
    private val ny = ZoneId.of("America/New_York")
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `bucketMeans Off throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            bucketMeans(listOf(Point(Instant.EPOCH, 1.0)), AverageMode.Off, ny, Instant.EPOCH)
        }
    }

    @Test
    fun `empty bucketMeans is empty`() {
        assertEquals(
            emptyList<Point>(),
            bucketMeans(emptyList(), AverageMode.Daily, ny, Instant.EPOCH),
        )
        assertEquals(
            emptyList<Point>(),
            bucketMeans(emptyList(), AverageMode.Weekly, paris, Instant.EPOCH),
        )
        assertEquals(
            emptyList<Point>(),
            bucketMeans(emptyList(), AverageMode.Monthly, ny, Instant.EPOCH),
        )
    }

    @Test
    fun `two points same local day yield one daily mean`() {
        val a = LocalDate.of(2026, 8, 20).atTime(8, 0).atZone(ny).toInstant()
        val b = LocalDate.of(2026, 8, 20).atTime(20, 0).atZone(ny).toInstant()
        val means = bucketMeans(
            listOf(Point(a, 10.0), Point(b, 20.0)),
            AverageMode.Daily,
            ny,
            Instant.EPOCH,
        )
        assertEquals(1, means.size)
        assertEquals(15.0, means.single().y, 0.0)
        assertEquals(LocalDate.of(2026, 8, 20).atStartOfDay(ny).toInstant(), means.single().t)
    }

    @Test
    fun `ISO week boundary America New_York`() {
        val sunday = LocalDate.of(2026, 8, 23).atTime(23, 0).atZone(ny).toInstant()
        val monday = LocalDate.of(2026, 8, 24).atTime(0, 30).atZone(ny).toInstant()
        val sunMean = bucketMeans(listOf(Point(sunday, 1.0)), AverageMode.Weekly, ny, Instant.EPOCH)
        val monMean = bucketMeans(listOf(Point(monday, 2.0)), AverageMode.Weekly, ny, Instant.EPOCH)
        assertEquals(LocalDate.of(2026, 8, 17).atStartOfDay(ny).toInstant(), sunMean.single().t)
        assertEquals(LocalDate.of(2026, 8, 24).atStartOfDay(ny).toInstant(), monMean.single().t)
        val both = bucketMeans(
            listOf(Point(sunday, 1.0), Point(monday, 3.0)),
            AverageMode.Weekly,
            ny,
            Instant.EPOCH,
        )
        assertEquals(2, both.size)
        assertEquals(1.0, both[0].y, 0.0)
        assertEquals(3.0, both[1].y, 0.0)
    }

    @Test
    fun `ISO week boundary Europe Paris`() {
        val sunday = LocalDate.of(2026, 8, 23).atTime(23, 0).atZone(paris).toInstant()
        val monday = LocalDate.of(2026, 8, 24).atTime(0, 30).atZone(paris).toInstant()
        val sunMean = bucketMeans(
            listOf(Point(sunday, 1.0)),
            AverageMode.Weekly,
            paris,
            Instant.EPOCH,
        )
        val monMean = bucketMeans(
            listOf(Point(monday, 2.0)),
            AverageMode.Weekly,
            paris,
            Instant.EPOCH,
        )
        assertEquals(LocalDate.of(2026, 8, 17).atStartOfDay(paris).toInstant(), sunMean.single().t)
        assertEquals(LocalDate.of(2026, 8, 24).atStartOfDay(paris).toInstant(), monMean.single().t)
    }

    @Test
    fun `monthly YearMonth`() {
        val aug1 = LocalDate.of(2026, 8, 5).atTime(12, 0).atZone(ny).toInstant()
        val aug2 = LocalDate.of(2026, 8, 31).atTime(18, 0).atZone(ny).toInstant()
        val sep = LocalDate.of(2026, 9, 1).atTime(0, 0).atZone(ny).toInstant()
        val means = bucketMeans(
            listOf(Point(aug1, 10.0), Point(aug2, 20.0), Point(sep, 30.0)),
            AverageMode.Monthly,
            ny,
            Instant.EPOCH,
        )
        assertEquals(2, means.size)
        assertEquals(15.0, means[0].y, 0.0)
        assertEquals(LocalDate.of(2026, 8, 1).atStartOfDay(ny).toInstant(), means[0].t)
        assertEquals(30.0, means[1].y, 0.0)
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(ny).toInstant(), means[1].t)
    }

    @Test
    fun `empty buckets omitted`() {
        val d1 = LocalDate.of(2026, 8, 1).atTime(12, 0).atZone(ny).toInstant()
        val d3 = LocalDate.of(2026, 8, 3).atTime(12, 0).atZone(ny).toInstant()
        val means = bucketMeans(
            listOf(Point(d1, 4.0), Point(d3, 6.0)),
            AverageMode.Daily,
            ny,
            Instant.EPOCH,
        )
        assertEquals(2, means.size)
        assertEquals(LocalDate.of(2026, 8, 1).atStartOfDay(ny).toInstant(), means[0].t)
        assertEquals(LocalDate.of(2026, 8, 3).atStartOfDay(ny).toInstant(), means[1].t)
    }

    @Test
    fun `DST spring-forward is one daily bucket`() {
        val a = LocalDate.of(2026, 3, 8).atTime(0, 30).atZone(ny).toInstant()
        val b = LocalDate.of(2026, 3, 8).atTime(12, 0).atZone(ny).toInstant()
        val means = bucketMeans(
            listOf(Point(a, 10.0), Point(b, 20.0)),
            AverageMode.Daily,
            ny,
            Instant.EPOCH,
        )
        assertEquals(1, means.size)
        assertEquals(15.0, means.single().y, 0.0)
        assertEquals(LocalDate.of(2026, 3, 8).atStartOfDay(ny).toInstant(), means.single().t)
    }

    @Test
    fun `filter-then-bucket point before start sharing ISO week does not affect weekly mean`() {
        val start = LocalDate.of(2026, 8, 19).atStartOfDay(paris).toInstant()
        val end = LocalDate.of(2026, 8, 22).atStartOfDay(paris).toInstant()
        val monday = sample(
            "weight",
            mapOf("lb" to 10.0),
            LocalDate.of(2026, 8, 17).atTime(12, 0).atZone(paris).toInstant(),
        )
        val thursday = sample(
            "weight",
            mapOf("lb" to 20.0),
            LocalDate.of(2026, 8, 20).atTime(12, 0).atZone(paris).toInstant(),
        )
        val filtered = filterFieldPoints(listOf(monday, thursday), "weight", "lb", start, end)
        assertEquals(1, filtered.size)
        assertEquals(20.0, filtered.single().y, 0.0)
        val means = bucketMeans(filtered, AverageMode.Weekly, paris, start)
        assertEquals(1, means.size)
        assertEquals(20.0, means.single().y, 0.0)
    }

    @Test
    fun `rangeBounds D7 start is today minus 6 and sample on today minus 7 is out`() {
        val now = LocalDate.of(2026, 8, 19).atTime(15, 0).atZone(ny).toInstant()
        val today = LocalDate.of(2026, 8, 19)
        val (start, end) = rangeBounds(RangePreset.D7, now, ny, null, null, null)
        assertEquals(today.minusDays(6).atStartOfDay(ny).toInstant(), start)
        assertEquals(today.plusDays(1).atStartOfDay(ny).toInstant(), end)
        val out = LocalDate.of(2026, 8, 12).atTime(18, 0).atZone(ny).toInstant()
        assertTrue(Duration.between(out, now).toHours() < 7 * 24)
        assertFalse(out >= start && out < end)
        val inAt = today.minusDays(6).atTime(0, 0).atZone(ny).toInstant()
        assertTrue(inAt >= start && inAt < end)
    }

    @Test
    fun `rangeBounds D30 D90 Y1 are calendar dates including today`() {
        val now = LocalDate.of(2026, 8, 19).atTime(15, 0).atZone(ny).toInstant()
        val today = LocalDate.of(2026, 8, 19)
        val end = today.plusDays(1).atStartOfDay(ny).toInstant()
        val d30 = rangeBounds(RangePreset.D30, now, ny, null, null, null)
        assertEquals(today.minusDays(29).atStartOfDay(ny).toInstant(), d30.first)
        assertEquals(end, d30.second)
        val d90 = rangeBounds(RangePreset.D90, now, ny, null, null, null)
        assertEquals(today.minusDays(89).atStartOfDay(ny).toInstant(), d90.first)
        assertEquals(end, d90.second)
        val y1 = rangeBounds(RangePreset.Y1, now, ny, null, null, null)
        assertEquals(today.minusYears(1).atStartOfDay(ny).toInstant(), y1.first)
        assertEquals(end, y1.second)
    }

    @Test
    fun `filterFieldPoints skips missing keys and non-finite y`() {
        val start = Instant.parse("2026-08-20T00:00:00Z")
        val end = Instant.parse("2026-08-21T00:00:00Z")
        val t = Instant.parse("2026-08-20T12:00:00Z")
        val samples = listOf(
            sample("weight", mapOf("lb" to Double.NaN), t),
            sample("weight", mapOf("lb" to Double.POSITIVE_INFINITY), t.plusSeconds(1)),
            sample("weight", mapOf("kg" to 80.0), t.plusSeconds(2)),
            sample("weight", mapOf("lb" to 180.0), t.plusSeconds(3)),
        )
        val pts = filterFieldPoints(samples, "weight", "lb", start, end)
        assertEquals(listOf(180.0), pts.map { it.y })
    }

    @Test
    fun `rangeBounds All with minRecordedAt and null is EPOCH`() {
        val t0 = Instant.parse("2020-01-01T00:00:00Z")
        val now = Instant.parse("2026-08-24T12:00:00Z")
        val (start, end) = rangeBounds(RangePreset.All, now, ZoneOffset.UTC, null, null, t0)
        assertEquals(t0, start)
        assertEquals(LocalDate.of(2026, 8, 25).atStartOfDay(ZoneOffset.UTC).toInstant(), end)
        val (startEpoch, _) = rangeBounds(RangePreset.All, now, ZoneOffset.UTC, null, null, null)
        assertEquals(Instant.EPOCH, startEpoch)
    }

    @Test
    fun `All ignores orphan earlier timestamp`() {
        val tEarly = Instant.parse("2019-01-01T00:00:00Z")
        val tWeight = Instant.parse("2021-06-01T00:00:00Z")
        val samples = listOf(
            sample("orphan", mapOf("x" to 1.0), tEarly),
            sample("weight", mapOf("lb" to 180.0), tWeight),
            sample("bhb", mapOf("mmol_l" to 1.0), tEarly),
        )
        val catalog = setOf("weight", "bhb")
        val min = minSelectedRecordedAt(samples, setOf("weight"), catalog)
        assertEquals(tWeight, min)
        val now = Instant.parse("2026-08-24T12:00:00Z")
        val (start, _) = rangeBounds(RangePreset.All, now, ZoneOffset.UTC, null, null, min)
        assertEquals(tWeight, start)
    }

    @Test
    fun `Custom from equals to is one local day`() {
        val now = Instant.parse("2026-08-24T12:00:00Z")
        val day = LocalDate.of(2026, 8, 20)
        val (start, end) = rangeBounds(RangePreset.Custom, now, ny, day, day, null)
        assertEquals(day.atStartOfDay(ny).toInstant(), start)
        assertEquals(day.plusDays(1).atStartOfDay(ny).toInstant(), end)
        val inAt = day.atTime(23, 30).atZone(ny).toInstant()
        val outAt = day.plusDays(1).atStartOfDay(ny).toInstant()
        assertTrue(inAt >= start && inAt < end)
        assertFalse(outAt >= start && outAt < end)
    }

    @Test
    fun `Wednesday D7 Friday sample bucketMeans t at or after start and project finite Px inside pad`() {
        val now = LocalDate.of(2026, 8, 19).atTime(12, 0).atZone(ny).toInstant()
        val (start, end) = rangeBounds(RangePreset.D7, now, ny, null, null, null)
        val friday = LocalDate.of(2026, 8, 14).atTime(9, 0).atZone(ny).toInstant()
        val mondayOrigin = LocalDate.of(2026, 8, 10).atStartOfDay(ny).toInstant()
        assertTrue(friday >= start && friday < end)
        assertTrue(mondayOrigin < start)
        val sample = sample("weight", mapOf("lb" to 80.0), friday)
        val filtered = filterFieldPoints(listOf(sample), "weight", "lb", start, end)
        val means = bucketMeans(filtered, AverageMode.Weekly, ny, start)
        assertEquals(1, means.size)
        assertTrue(means.single().t >= start)
        assertEquals(start, means.single().t)
        val pad = 10f
        val width = 200f
        val height = 100f
        val px = project(
            listOf(Series("lb", "Weight", "lb", means)),
            start,
            end,
            width,
            height,
            pad,
        )
        assertEquals(1, px.size)
        assertEquals(1, px[0].size)
        val p: Px = px[0][0]
        assertTrue(p.x.isFinite() && p.y.isFinite())
        assertTrue(p.x >= pad && p.x <= width - pad)
        assertTrue(p.y >= pad && p.y <= height - pad)
        assertEquals(pad, p.x, 0.01f)
    }

    private fun sample(metricId: String, values: Map<String, Double>, recordedAt: Instant): Sample =
        Sample(
            id = "s-$metricId-${recordedAt.toEpochMilli()}",
            metricId = metricId,
            recordedAt = recordedAt,
            modifiedAt = recordedAt,
            source = Sources.MANUAL,
            values = values,
        )
}
