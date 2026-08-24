package org.paul.tracker.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.paul.tracker.data.BuiltInMetrics
import org.paul.tracker.stats.Point

class ChartProjectTest {
    private val start = Instant.parse("2026-08-01T00:00:00Z")
    private val end = Instant.parse("2026-08-08T00:00:00Z")

    @Test
    fun `downsample 10000 points width 100 is at most 100`() {
        val pts = (0 until 10_000).map { i ->
            Point(start.plusSeconds(i.toLong() * 30), i.toDouble())
        }
        val out = downsample(pts, start, end, 100)
        assertTrue(out.size <= 100)
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.y.isFinite() && !it.t.isBefore(start) && it.t.isBefore(end) })
    }

    @Test
    fun `equal y does not divide by zero`() {
        val pts = listOf(Point(start, 5.0), Point(start.plusSeconds(60), 5.0))
        val px = project(listOf(Series("a", "A", "u", pts)), start, end, 100f, 100f, 8f)
        assertEquals(1, px.size)
        assertEquals(2, px[0].size)
        assertTrue(px[0].all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `floorMod color index for hash Int MIN_VALUE is in 0 to 7`() {
        val idx = seriesColorIndex(Int.MIN_VALUE)
        assertTrue(idx in 0..7)
        assertEquals(Math.floorMod(Int.MIN_VALUE, 8), idx)
        assertEquals(8, SERIES_COLORS.size)
        val byId = seriesColorIndex("weight")
        assertTrue(byId in 0..7)
        assertEquals(SERIES_COLORS[byId], seriesColor("weight"))
    }

    @Test
    fun `empty series yields empty Px lists of same arity`() {
        val empty = project(
            listOf(
                Series("a", "A", "", emptyList()),
                Series("b", "B", "", emptyList()),
            ),
            start,
            end,
            100f,
            100f,
            8f,
        )
        assertEquals(2, empty.size)
        assertTrue(empty.all { it.isEmpty() })
        val mixed = project(
            listOf(
                Series("a", "A", "", listOf(Point(start, 1.0))),
                Series("b", "B", "", emptyList()),
            ),
            start,
            end,
            100f,
            100f,
            8f,
        )
        assertEquals(2, mixed.size)
        assertEquals(1, mixed[0].size)
        assertEquals(0, mixed[1].size)
        val none = project(emptyList(), start, end, 100f, 100f, 8f)
        assertEquals(0, none.size)
        val invalid = project(
            listOf(Series("a", "A", "", listOf(Point(start, 1.0)))),
            start,
            start,
            100f,
            100f,
            8f,
        )
        assertEquals(1, invalid.size)
        assertTrue(invalid[0].isEmpty())
        assertTrue(downsample(listOf(Point(start, 1.0)), start, end, 0).isEmpty())
        assertTrue(downsample(listOf(Point(start, 1.0)), end, start, 10).isEmpty())
    }

    @Test
    fun `two points at start and just below end map near left and right inside pad`() {
        val pad = 10f
        val width = 200f
        val height = 100f
        val pts = listOf(
            Point(start, 0.0),
            Point(end.minusMillis(1), 10.0),
        )
        val px = project(listOf(Series("a", "A", "u", pts)), start, end, width, height, pad)
        assertEquals(2, px[0].size)
        val left = px[0][0]
        val right = px[0][1]
        assertTrue(left.x.isFinite() && right.x.isFinite())
        assertTrue(left.y.isFinite() && right.y.isFinite())
        assertTrue(left.x >= pad && left.x <= width - pad)
        assertTrue(right.x >= pad && right.x <= width - pad)
        assertEquals(pad, left.x, 0.5f)
        assertEquals(width - pad, right.x, 1f)
        assertTrue(left.y > right.y)
    }

    @Test
    fun `BP three series share one y-axis and pulse sits low`() {
        val t = start.plusSeconds(3600)
        val bp = BuiltInMetrics.ALL.first { it.id == "blood_pressure" }
        val series = bp.fields.map { field ->
            val y = when (field.id) {
                "systolic" -> 120.0
                "diastolic" -> 80.0
                else -> 60.0
            }
            Series(field.id, field.label, field.unit, listOf(Point(t, y)))
        }
        assertEquals(3, series.size)
        val px = project(series, start, end, 100f, 100f, 10f)
        assertEquals(3, px.size)
        assertEquals(1, px[0].size)
        assertEquals(1, px[1].size)
        assertEquals(1, px[2].size)
        assertEquals(px[0][0].x, px[1][0].x, 0.01f)
        assertTrue(px[0][0].y < px[1][0].y)
        assertTrue(px[1][0].y < px[2][0].y)
    }

    @Test
    fun `downsample size at most width returns sorted without merge`() {
        val pts = listOf(
            Point(start.plusSeconds(30), 3.0),
            Point(start, 1.0),
            Point(start.plusSeconds(10), 2.0),
        )
        val out = downsample(pts, start, end, 10)
        assertEquals(listOf(1.0, 2.0, 3.0), out.map { it.y })
        assertEquals(listOf(start, start.plusSeconds(10), start.plusSeconds(30)), out.map { it.t })
    }
}
