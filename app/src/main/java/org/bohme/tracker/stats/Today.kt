package org.bohme.tracker.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.bohme.tracker.data.Sample

fun localDateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

fun todayFieldKey(metricId: String, fieldId: String): String = "$metricId/$fieldId"

fun sampleOnLocalDate(
    samples: List<Sample>,
    metricId: String,
    date: LocalDate,
    zone: ZoneId,
): Sample? =
    samples
        .filter { it.metricId == metricId && localDateOf(it.recordedAt, zone) == date }
        .maxByOrNull { it.recordedAt }
