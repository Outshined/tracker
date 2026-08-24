package org.bohme.tracker.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.bohme.tracker.data.Sample

enum class RangePreset { D7, D30, D90, Y1, All, Custom }

enum class AverageMode { Off, Daily, Weekly, Monthly }

data class Point(val t: Instant, val y: Double)

fun minSelectedRecordedAt(
    samples: List<Sample>,
    selectedIds: Set<String>,
    catalogIds: Set<String>,
): Instant? =
    samples.asSequence()
        .filter { it.metricId in selectedIds && it.metricId in catalogIds }
        .minOfOrNull { it.recordedAt }

fun rangeBounds(
    preset: RangePreset,
    now: Instant,
    zone: ZoneId,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    minRecordedAt: Instant? = null,
): Pair<Instant, Instant> {
    val today = now.atZone(zone).toLocalDate()
    val endExclusiveToday = today.plusDays(1).atStartOfDay(zone).toInstant()
    return when (preset) {
        RangePreset.D7 ->
            today.minusDays(6).atStartOfDay(zone).toInstant() to endExclusiveToday
        RangePreset.D30 ->
            today.minusDays(29).atStartOfDay(zone).toInstant() to endExclusiveToday
        RangePreset.D90 ->
            today.minusDays(89).atStartOfDay(zone).toInstant() to endExclusiveToday
        RangePreset.Y1 ->
            today.minusYears(1).atStartOfDay(zone).toInstant() to endExclusiveToday
        RangePreset.All ->
            (minRecordedAt ?: Instant.EPOCH) to endExclusiveToday
        RangePreset.Custom -> {
            val from = requireNotNull(customFrom)
            val to = requireNotNull(customTo)
            from.atStartOfDay(zone).toInstant() to to.plusDays(1).atStartOfDay(zone).toInstant()
        }
    }
}

fun filterFieldPoints(
    samples: List<Sample>,
    metricId: String,
    fieldId: String,
    start: Instant,
    end: Instant,
): List<Point> =
    samples.asSequence()
        .filter { it.metricId == metricId }
        .mapNotNull { sample ->
            val y = sample.values[fieldId] ?: return@mapNotNull null
            if (!y.isFinite()) return@mapNotNull null
            val t = sample.recordedAt
            if (t < start || t >= end) return@mapNotNull null
            Point(t, y)
        }
        .sortedBy { it.t }
        .toList()

fun bucketMeans(
    points: List<Point>,
    mode: AverageMode,
    zone: ZoneId,
    start: Instant,
): List<Point> {
    require(mode != AverageMode.Off) { "bucketMeans does not accept Off" }
    if (points.isEmpty()) return emptyList()
    data class Acc(val origin: Instant, var sum: Double, var n: Int)
    val buckets = LinkedHashMap<Any, Acc>()
    for (p in points) {
        val date = p.t.atZone(zone).toLocalDate()
        val (key, originDate) = when (mode) {
            AverageMode.Daily -> date to date
            AverageMode.Weekly -> {
                val monday = date.with(DayOfWeek.MONDAY)
                monday to monday
            }
            AverageMode.Monthly -> {
                val ym = YearMonth.from(date)
                ym to ym.atDay(1)
            }
            AverageMode.Off -> error("Off")
        }
        val origin = originDate.atStartOfDay(zone).toInstant()
        val acc = buckets.getOrPut(key) { Acc(origin, 0.0, 0) }
        acc.sum += p.y
        acc.n += 1
    }
    return buckets.values
        .map { acc -> Point(maxOf(acc.origin, start), acc.sum / acc.n) }
        .sortedBy { it.t }
}
