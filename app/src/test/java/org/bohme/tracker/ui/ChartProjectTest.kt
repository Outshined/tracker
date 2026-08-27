package org.bohme.tracker.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.stats.Point

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
        val fourArg = Series("weight", "W", "lb", emptyList())
        assertEquals(seriesColor("weight"), fourArg.color)
    }

    @Test
    fun `resolvedFieldColor stored wins else seriesColor of field id`() {
        val stored = 0xFFD62728.toInt()
        val withColor = FieldDef(id = "lb", label = "Weight", unit = "lb", color = stored)
        assertEquals(stored, resolvedFieldColor(withColor))
        val fallback = FieldDef(id = "lb", label = "Weight", unit = "lb")
        assertEquals(seriesColor("lb"), resolvedFieldColor(fallback))
        assertEquals(seriesColor("lb"), resolvedFieldColor(fallback.copy(color = null)))
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

    @Test
    fun `explicit y-domain maps min to bottom max to top`() {
        val pad = 10f
        val width = 200f
        val height = 100f
        val t = start.plusSeconds(3600)
        val pts = listOf(
            Point(t, 100.0),
            Point(t.plusSeconds(60), 180.0),
            Point(t.plusSeconds(120), 300.0),
        )
        val px = project(
            listOf(Series("a", "A", "u", pts)),
            start,
            end,
            width,
            height,
            pad,
            yMin = 100.0,
            yMax = 300.0,
        )
        assertEquals(3, px[0].size)
        val atMin = px[0][0]
        val at180 = px[0][1]
        val atMax = px[0][2]
        assertEquals(height - pad, atMin.y, 0.05f)
        assertEquals(pad, atMax.y, 0.05f)
        assertTrue(at180.y > pad && at180.y < height - pad)
        assertTrue("180 on 100-300 is not at the top", at180.y > pad + 1f)
    }

    @Test
    fun `invalid yMin yMax falls back to auto scale`() {
        val pts = listOf(Point(start, 180.0), Point(start.plusSeconds(60), 192.0))
        val series = listOf(Series("a", "A", "u", pts))
        val auto = project(series, start, end, 100f, 100f, 8f)
        val inverted = project(series, start, end, 100f, 100f, 8f, yMin = 300.0, yMax = 100.0)
        val equal = project(series, start, end, 100f, 100f, 8f, yMin = 100.0, yMax = 100.0)
        assertEquals(auto, inverted)
        assertEquals(auto, equal)
    }

    @Test
    fun `resolvedGraphRange stored wins over built-in id`() {
        val metric = MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(FieldDef(id = "lb", label = "Weight", unit = "lb")),
            graphMin = 150.0,
            graphMax = 250.0,
        )
        assertEquals(150.0 to 250.0, resolvedGraphRange(metric))
    }

    @Test
    fun `resolvedGraphRange built-in id without stored uses defaults`() {
        val weight = MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(FieldDef(id = "lb", label = "Weight", unit = "lb")),
        )
        assertEquals(
            BuiltInMetrics.WEIGHT_GRAPH_MIN to BuiltInMetrics.WEIGHT_GRAPH_MAX,
            resolvedGraphRange(weight),
        )
        assertEquals(
            BuiltInMetrics.BHB_GRAPH_MIN to BuiltInMetrics.BHB_GRAPH_MAX,
            resolvedGraphRange(
                MetricDef(
                    id = "bhb",
                    label = "BHB",
                    fields = listOf(FieldDef(id = "mmol_l", label = "BHB", unit = "mmol/L")),
                ),
            ),
        )
        assertEquals(
            BuiltInMetrics.GLUCOSE_GRAPH_MIN to BuiltInMetrics.GLUCOSE_GRAPH_MAX,
            resolvedGraphRange(
                MetricDef(
                    id = "glucose",
                    label = "Glucose",
                    fields = listOf(FieldDef(id = "mg_dl", label = "Glucose", unit = "mg/dL")),
                ),
            ),
        )
        assertEquals(
            BuiltInMetrics.BLOOD_PRESSURE_GRAPH_MIN to BuiltInMetrics.BLOOD_PRESSURE_GRAPH_MAX,
            resolvedGraphRange(
                MetricDef(
                    id = "blood_pressure",
                    label = "BP",
                    fields = listOf(FieldDef(id = "systolic", label = "Sys", unit = "mmHg")),
                ),
            ),
        )
    }

    @Test
    fun `resolvedGraphRange unknown id without stored is null`() {
        val custom = MetricDef(
            id = "steps",
            label = "Steps",
            fields = listOf(FieldDef(id = "count", label = "Count", unit = "")),
        )
        assertNull(resolvedGraphRange(custom))
    }

    @Test
    fun `resolvedGraphRange min greater or equal max treated as missing`() {
        val weightInvalid = MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(FieldDef(id = "lb", label = "Weight", unit = "lb")),
            graphMin = 300.0,
            graphMax = 100.0,
        )
        assertEquals(
            BuiltInMetrics.WEIGHT_GRAPH_MIN to BuiltInMetrics.WEIGHT_GRAPH_MAX,
            resolvedGraphRange(weightInvalid),
        )
        val equal = weightInvalid.copy(graphMin = 100.0, graphMax = 100.0)
        assertEquals(
            BuiltInMetrics.WEIGHT_GRAPH_MIN to BuiltInMetrics.WEIGHT_GRAPH_MAX,
            resolvedGraphRange(equal),
        )
        val customInvalid = MetricDef(
            id = "steps",
            label = "Steps",
            fields = listOf(FieldDef(id = "count", label = "Count", unit = "")),
            graphMin = 10.0,
            graphMax = 1.0,
        )
        assertNull(resolvedGraphRange(customInvalid))
    }

    @Test
    fun `polylineSegments empty or one point is empty`() {
        assertTrue(polylineSegments(emptyList()).isEmpty())
        assertTrue(polylineSegments(listOf(Px(1f, 2f))).isEmpty())
    }

    @Test
    fun `polylineSegments two points is one segment of those instances`() {
        val a = Px(0f, 0f)
        val b = Px(10f, 5f)
        val segs = polylineSegments(listOf(a, b))
        assertEquals(1, segs.size)
        assertSame(a, segs[0].first)
        assertSame(b, segs[0].second)
    }

    @Test
    fun `polylineSegments three points is two consecutive segments same instances`() {
        val a = Px(0f, 0f)
        val b = Px(10f, 5f)
        val c = Px(20f, 1f)
        val segs = polylineSegments(listOf(a, b, c))
        assertEquals(2, segs.size)
        assertSame(a, segs[0].first)
        assertSame(b, segs[0].second)
        assertSame(b, segs[1].first)
        assertSame(c, segs[1].second)
    }

    @Test
    fun `polylineSegments does not reorder`() {
        val a = Px(20f, 0f)
        val b = Px(0f, 5f)
        val c = Px(10f, 1f)
        val segs = polylineSegments(listOf(a, b, c))
        assertEquals(2, segs.size)
        assertSame(a, segs[0].first)
        assertSame(b, segs[0].second)
        assertSame(b, segs[1].first)
        assertSame(c, segs[1].second)
    }

    @Test
    fun `chartYDomain explicit range wins when ys empty`() {
        assertEquals(100.0 to 300.0, chartYDomain(emptyList(), 100.0, 300.0))
        assertEquals(0.0 to 5.0, chartYDomain(listOf(1.0, 4.0), 0.0, 5.0))
    }

    @Test
    fun `chartYDomain auto when no explicit`() {
        val domain = chartYDomain(listOf(10.0, 20.0), null, null)!!
        assertEquals(10.0 - 0.5, domain.first, 1e-12)
        assertEquals(20.0 + 0.5, domain.second, 1e-12)
        val onlyMin = chartYDomain(listOf(10.0, 20.0), 0.0, null)!!
        assertEquals(domain, onlyMin)
    }

    @Test
    fun `chartYDomain null when no ys and no range`() {
        assertNull(chartYDomain(emptyList(), null, null))
        assertNull(chartYDomain(emptyList(), 300.0, 100.0))
        assertNull(chartYDomain(emptyList(), 100.0, 100.0))
        assertNull(chartYDomain(emptyList(), Double.NaN, 10.0))
    }

    @Test
    fun `chartYDomain equal ys plus minus 1`() {
        assertEquals(4.0 to 6.0, chartYDomain(listOf(5.0, 5.0), null, null))
        assertEquals(4.0 to 6.0, chartYDomain(listOf(5.0), 10.0, 10.0))
    }

    @Test
    fun `chartYDomain invalid yMin yMax falls through to auto when ys present`() {
        val auto = chartYDomain(listOf(10.0, 20.0), null, null)
        assertEquals(auto, chartYDomain(listOf(10.0, 20.0), 300.0, 100.0))
        assertEquals(auto, chartYDomain(listOf(10.0, 20.0), 100.0, 100.0))
        assertEquals(auto, chartYDomain(listOf(10.0, 20.0), Double.NaN, 50.0))
    }

    @Test
    fun `yAxisTicks 100 to 300 includes endpoints and is strictly increasing`() {
        val ticks = yAxisTicks(100.0, 300.0)
        assertTrue(ticks.first() == 100.0)
        assertTrue(ticks.last() == 300.0)
        assertTrue(ticks.size in 3..8)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ticks.all { it in 100.0..300.0 })
    }

    @Test
    fun `yAxisTicks 0 to 5 includes 0 and 5`() {
        val ticks = yAxisTicks(0.0, 5.0)
        assertTrue(ticks.first() == 0.0)
        assertTrue(ticks.last() == 5.0)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ticks.all { it in 0.0..5.0 })
    }

    @Test
    fun `yAxisTicks 40 to 200 includes 40 and 200`() {
        val ticks = yAxisTicks(40.0, 200.0)
        assertTrue(ticks.first() == 40.0)
        assertTrue(ticks.last() == 200.0)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ticks.all { it in 40.0..200.0 })
    }

    @Test
    fun `yAxisTicks empty or invalid domain is empty`() {
        assertTrue(yAxisTicks(1.0, 1.0).isEmpty())
        assertTrue(yAxisTicks(2.0, 1.0).isEmpty())
        assertTrue(yAxisTicks(Double.NaN, 1.0).isEmpty())
        assertTrue(yAxisTicks(0.0, Double.POSITIVE_INFINITY).isEmpty())
        assertTrue(yAxisTicks(Double.NEGATIVE_INFINITY, 0.0).isEmpty())
    }

    @Test
    fun `formatAxisTick integers have no decimal and fractions trim zeros`() {
        assertEquals("100", formatAxisTick(100.0))
        assertEquals("0.5", formatAxisTick(0.5))
        assertEquals("0", formatAxisTick(0.0))
        assertEquals("2.5", formatAxisTick(2.5))
    }

    @Test
    fun `project padLeftPx maps start x and yMin yMax to inner pads`() {
        val pad = 8f
        val padLeft = 40f
        val width = 200f
        val height = 100f
        val pts = listOf(
            Point(start, 300.0),
            Point(start.plusSeconds(60), 100.0),
        )
        val px = project(
            listOf(Series("a", "A", "u", pts)),
            start,
            end,
            width,
            height,
            pad,
            yMin = 100.0,
            yMax = 300.0,
            padLeftPx = padLeft,
        )
        assertEquals(2, px[0].size)
        assertEquals(padLeft, px[0][0].x, 0.5f)
        assertEquals(pad, px[0][0].y, 0.05f)
        assertEquals(height - pad, px[0][1].y, 0.05f)
        assertTrue(px[0][1].x > padLeft)
        assertTrue(px[0][1].x <= width - pad)
    }
}
