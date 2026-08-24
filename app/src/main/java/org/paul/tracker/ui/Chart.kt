package org.paul.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.floor
import org.paul.tracker.stats.Point

data class Series(
    val id: String,
    val label: String,
    val unit: String,
    val points: List<Point>,
)

data class Px(val x: Float, val y: Float)

val SERIES_COLORS = intArrayOf(
    0xFF1F77B4.toInt(), 0xFFFF7F0E.toInt(), 0xFF2CA02C.toInt(), 0xFFD62728.toInt(),
    0xFF9467BD.toInt(), 0xFF8C564B.toInt(), 0xFFE377C2.toInt(), 0xFF17BECF.toInt(),
)

fun seriesColorIndex(hash: Int): Int = Math.floorMod(hash, SERIES_COLORS.size)

fun seriesColorIndex(id: String): Int = seriesColorIndex(id.hashCode())

fun seriesColor(id: String): Int = SERIES_COLORS[seriesColorIndex(id)]

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

fun project(
    series: List<Series>,
    start: Instant,
    end: Instant,
    widthPx: Float,
    heightPx: Float,
    padPx: Float,
): List<List<Px>> {
    if (series.isEmpty()) return emptyList()
    if (!end.isAfter(start) || widthPx <= 0f || heightPx <= 0f) {
        return series.map { emptyList() }
    }
    val ys = series.flatMap { it.points }.map { it.y }
    if (ys.isEmpty()) return series.map { emptyList() }
    val minY = ys.min()
    val maxY = ys.max()
    val y0: Double
    val y1: Double
    if (minY == maxY) {
        y0 = minY - 1.0
        y1 = maxY + 1.0
    } else {
        val pad = 0.05 * (maxY - minY)
        y0 = minY - pad
        y1 = maxY + pad
    }
    val innerW = widthPx - 2f * padPx
    val innerH = heightPx - 2f * padPx
    val span = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
    return series.map { s ->
        s.points.map { p ->
            val x = padPx + ((p.t.toEpochMilli() - start.toEpochMilli()) / span).toFloat() * innerW
            // Canvas y grows downward; larger values plot higher (smaller y).
            val y = padPx + ((y1 - p.y) / (y1 - y0)).toFloat() * innerH
            Px(x, y)
        }
    }
}

@Composable
fun Chart(
    metricId: String,
    raw: List<Series>,
    means: List<Series>,
    start: Instant,
    end: Instant,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = "chart-$metricId" },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || !end.isAfter(start)) return@Canvas
        val padPx = 8.dp.toPx()
        val widthCol = w.toInt().coerceAtLeast(0)
        val rawDs = raw.map { s -> s.copy(points = downsample(s.points, start, end, widthCol)) }
        val meanDs = means.map { s -> s.copy(points = downsample(s.points, start, end, widthCol)) }
        val combined = rawDs + meanDs
        val px = project(combined, start, end, w, h, padPx)
        val rawCount = rawDs.size
        val radius = 3.dp.toPx()
        val stroke = 2.dp.toPx()
        for (i in rawDs.indices) {
            val color = Color(seriesColor(rawDs[i].id))
            for (pt in px[i]) {
                drawCircle(color = color, radius = radius, center = Offset(pt.x, pt.y))
            }
        }
        for (i in meanDs.indices) {
            val pts = px[rawCount + i]
            if (pts.isEmpty()) continue
            val color = Color(seriesColor(meanDs[i].id))
            if (pts.size == 1) {
                drawCircle(color = color, radius = radius, center = Offset(pts[0].x, pts[0].y))
            } else {
                for (j in 1 until pts.size) {
                    drawLine(
                        color = color,
                        start = Offset(pts[j - 1].x, pts[j - 1].y),
                        end = Offset(pts[j].x, pts[j].y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
