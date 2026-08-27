package org.bohme.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.lightenArgb
import org.bohme.tracker.stats.Point

val SERIES_COLORS = intArrayOf(
    0xFF1F77B4.toInt(), 0xFFFF7F0E.toInt(), 0xFF2CA02C.toInt(), 0xFFD62728.toInt(),
    0xFF9467BD.toInt(), 0xFF8C564B.toInt(), 0xFFE377C2.toInt(), 0xFF17BECF.toInt(),
)

fun seriesColorIndex(hash: Int): Int = Math.floorMod(hash, SERIES_COLORS.size)

fun seriesColorIndex(id: String): Int = seriesColorIndex(id.hashCode())

fun seriesColor(id: String): Int = SERIES_COLORS[seriesColorIndex(id)]

fun resolvedFieldColor(field: FieldDef): Int {
    val stored = field.color
    return if (stored != null) stored else seriesColor(field.id)
}

data class Series(
    val id: String,
    val label: String,
    val unit: String,
    val points: List<Point>,
    val color: Int = seriesColor(id),
)

data class Px(val x: Float, val y: Float)

fun downsample(
    points: List<Point>,
    start: Instant,
    end: Instant,
    widthPx: Int,
): List<Point> {
    if (widthPx <= 0 || !end.isAfter(start)) return emptyList()
    if (points.size <= widthPx) return points.sortedBy { it.t }
    val span = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
    val sums = DoubleArray(widthPx)
    val counts = IntArray(widthPx)
    for (p in points) {
        val col = floor((p.t.toEpochMilli() - start.toEpochMilli()) / span * widthPx)
            .toInt()
            .coerceIn(0, widthPx - 1)
        sums[col] += p.y
        counts[col] += 1
    }
    val out = ArrayList<Point>(widthPx)
    for (i in 0 until widthPx) {
        if (counts[i] == 0) continue
        val tMs = start.toEpochMilli() + (i + 0.5) / widthPx * span
        out.add(Point(Instant.ofEpochMilli(tMs.toLong()), sums[i] / counts[i]))
    }
    return out
}

fun resolvedGraphRange(metric: MetricDef): Pair<Double, Double>? {
    val storedMin = metric.graphMin
    val storedMax = metric.graphMax
    if (
        storedMin != null && storedMax != null &&
        storedMin.isFinite() && storedMax.isFinite() &&
        storedMin < storedMax
    ) {
        return storedMin to storedMax
    }
    return BuiltInMetrics.defaultGraphRange(metric.id)
}

fun chartYDomain(ys: List<Double>, yMin: Double?, yMax: Double?): Pair<Double, Double>? {
    if (yMin != null && yMax != null && yMin.isFinite() && yMax.isFinite() && yMin < yMax) {
        return yMin to yMax
    }
    if (ys.isEmpty()) return null
    val minY = ys.min()
    val maxY = ys.max()
    return if (minY == maxY) {
        (minY - 1.0) to (maxY + 1.0)
    } else {
        val pad = 0.05 * (maxY - minY)
        (minY - pad) to (maxY + pad)
    }
}

fun yAxisTicks(yMin: Double, yMax: Double, targetCount: Int = 5): List<Double> {
    if (!yMin.isFinite() || !yMax.isFinite() || yMin >= yMax) return emptyList()
    val intervals = (targetCount - 1).coerceAtLeast(1)
    val step = niceAxisStep((yMax - yMin) / intervals)
    if (!step.isFinite() || step <= 0.0) return listOf(yMin, yMax)
    val span = yMax - yMin
    val eps = maxOf(span * 1e-9, abs(yMax) * 1e-12, abs(yMin) * 1e-12, 1e-12)
    val ticks = ArrayList<Double>()
    ticks.add(yMin)
    var k = ceil(yMin / step)
    var guard = 0
    while (guard++ < 64) {
        val t = k * step
        if (t >= yMax - eps) break
        if (t > yMin + eps) ticks.add(t)
        k += 1.0
    }
    ticks.add(yMax)
    return ticks
}

fun formatAxisTick(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val asLong = value.toLong()
    if (value == asLong.toDouble()) return asLong.toString()
    val df = DecimalFormat("0.##########", DecimalFormatSymbols(Locale.US))
    df.roundingMode = RoundingMode.HALF_UP
    val formatted = df.format(value)
    return if (formatted == "-0") "0" else formatted
}

private fun niceAxisStep(raw: Double): Double {
    if (!raw.isFinite() || raw <= 0.0) return Double.NaN
    val exp = floor(log10(raw))
    val mag = 10.0.pow(exp)
    if (!mag.isFinite() || mag == 0.0) return Double.NaN
    val f = raw / mag
    val nf = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return nf * mag
}

fun project(
    series: List<Series>,
    start: Instant,
    end: Instant,
    widthPx: Float,
    heightPx: Float,
    padPx: Float,
    yMin: Double? = null,
    yMax: Double? = null,
    padLeftPx: Float = padPx,
): List<List<Px>> {
    if (series.isEmpty()) return emptyList()
    if (!end.isAfter(start) || widthPx <= 0f || heightPx <= 0f) {
        return series.map { emptyList() }
    }
    val ys = series.flatMap { it.points }.map { it.y }
    if (ys.isEmpty()) return series.map { emptyList() }
    val domain = chartYDomain(ys, yMin, yMax) ?: return series.map { emptyList() }
    val y0 = domain.first
    val y1 = domain.second
    val innerW = widthPx - padLeftPx - padPx
    val innerH = heightPx - 2f * padPx
    val span = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
    return series.map { s ->
        s.points.map { p ->
            val x = padLeftPx + ((p.t.toEpochMilli() - start.toEpochMilli()) / span).toFloat() * innerW
            // Canvas y grows downward; larger values plot higher (smaller y).
            val y = padPx + ((y1 - p.y) / (y1 - y0)).toFloat() * innerH
            Px(x, y)
        }
    }
}

fun polylineSegments(pts: List<Px>): List<Pair<Px, Px>> = pts.zipWithNext()

@Composable
fun Chart(
    metricId: String,
    raw: List<Series>,
    means: List<Series>,
    start: Instant,
    end: Instant,
    modifier: Modifier = Modifier,
    yMin: Double? = null,
    yMax: Double? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.12f)
    val labelColor = onSurface.copy(alpha = 0.70f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = "chart-$metricId" }
            .testTag("chart-$metricId"),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || !end.isAfter(start)) return@Canvas
        val padPx = 8.dp.toPx()
        val gapPx = 4.dp.toPx()
        val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)
        val preYs = (raw + means).flatMap { it.points }.map { it.y }
        val preDomain = chartYDomain(preYs, yMin, yMax)
        val padLeftPx = if (preDomain == null) {
            padPx
        } else {
            val preTicks = yAxisTicks(preDomain.first, preDomain.second)
            val maxLabelW = preTicks.maxOfOrNull { tick ->
                textMeasurer.measure(formatAxisTick(tick), style = labelStyle).size.width.toFloat()
            } ?: 0f
            maxOf(padPx, maxLabelW + gapPx)
        }
        val innerW = (w - padLeftPx - padPx).coerceAtLeast(0f)
        val widthCol = innerW.toInt()
        val rawDs = raw.map { s -> s.copy(points = downsample(s.points, start, end, widthCol)) }
        val meanDs = means.map { s -> s.copy(points = downsample(s.points, start, end, widthCol)) }
        val combined = rawDs + meanDs
        val px = project(combined, start, end, w, h, padPx, yMin, yMax, padLeftPx)
        val domain = chartYDomain(combined.flatMap { it.points }.map { it.y }, yMin, yMax)
        val ticks = if (domain != null) yAxisTicks(domain.first, domain.second) else emptyList()
        val innerH = h - 2f * padPx
        val gridStroke = 1.dp.toPx()
        if (domain != null) {
            val y0 = domain.first
            val y1 = domain.second
            for (tick in ticks) {
                val y = padPx + ((y1 - tick) / (y1 - y0)).toFloat() * innerH
                drawLine(
                    color = gridColor,
                    start = Offset(padLeftPx, y),
                    end = Offset(w - padPx, y),
                    strokeWidth = gridStroke,
                )
            }
        }
        val rawCount = rawDs.size
        val radius = 3.dp.toPx()
        val stroke = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f)
        for (i in rawDs.indices) {
            val pts = px[i]
            val color = Color(rawDs[i].color)
            for ((a, b) in polylineSegments(pts)) {
                drawLine(
                    color = color,
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            for (pt in pts) {
                drawCircle(color = color, radius = radius, center = Offset(pt.x, pt.y))
            }
        }
        for (i in meanDs.indices) {
            val pts = px[rawCount + i]
            if (pts.isEmpty()) continue
            val color = Color(lightenArgb(meanDs[i].color))
            if (pts.size == 1) {
                drawCircle(color = color, radius = radius, center = Offset(pts[0].x, pts[0].y))
            } else {
                for ((a, b) in polylineSegments(pts)) {
                    drawLine(
                        color = color,
                        start = Offset(a.x, a.y),
                        end = Offset(b.x, b.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = dash,
                    )
                }
            }
        }
        if (domain != null) {
            val y0 = domain.first
            val y1 = domain.second
            for (tick in ticks) {
                val y = padPx + ((y1 - tick) / (y1 - y0)).toFloat() * innerH
                val layout = textMeasurer.measure(formatAxisTick(tick), style = labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (padLeftPx - gapPx - layout.size.width).coerceAtLeast(0f),
                        y - layout.size.height / 2f,
                    ),
                )
            }
        }
    }
}
