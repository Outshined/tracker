package org.paul.tracker.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import org.paul.tracker.ingest.SampleWriter

fun interface Clock {
    fun now(): Instant
}

class Store(
    private val file: File,
    private val clock: Clock = Clock { Instant.now() },
    /** Invoked after tmp+sync, before ATOMIC_MOVE. Tests throw here to prove rollback. */
    private val onBeforeCommitFile: () -> Unit = {},
) : SampleWriter {
    private val lock = Any()
    private val bak = File(file.path + ".bak")
    private val tmp = File(file.path + ".tmp")
    private val corrupt = File(file.path + ".corrupt")
    private val bakCorrupt = File(file.path + ".bak.corrupt")

    private var ready = false
    private var trustedLiveFile = false
    private var metrics: List<MetricDef> = emptyList()
    private var samples: List<Sample> = emptyList()
    private var extras: Map<String, JsonElement> = emptyMap()
    private var exportedAt: Instant = Instant.EPOCH

    fun load(): LoadState = synchronized(lock) {
        if (tmp.exists()) {
            tmp.delete()
        }
        val liveExists = file.exists()
        val bakExists = bak.exists()
        if (!liveExists && !bakExists) {
            persist(seedSnapshot())
            return LoadState.Ready
        }
        if (liveExists) {
            val decoded = try {
                JsonCodec.decode(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
            if (decoded != null) {
                commit(decoded)
                trustedLiveFile = true
                return LoadState.Ready
            }
        }
        if (bakExists) {
            val decoded = try {
                JsonCodec.decode(bak.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
            if (decoded != null) {
                val next = canonicalize(decoded)
                trustedLiveFile = false
                if (liveExists) {
                    Files.copy(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                persist(next)
                return LoadState.Ready
            }
        }
        metrics = emptyList()
        samples = emptyList()
        extras = emptyMap()
        exportedAt = Instant.EPOCH
        ready = false
        trustedLiveFile = false
        LoadState.Corrupt("Local data file is unreadable.")
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        snapshotUnlocked()
    }

    fun metrics(): List<MetricDef> = synchronized(lock) {
        metrics.toList()
    }

    fun samplesFor(metricId: String): List<Sample> = synchronized(lock) {
        samples.filter { it.metricId == metricId }.sortedByDescending { it.recordedAt }
    }

    fun upsertMetric(metric: MetricDef) {
        synchronized(lock) {
            requireReady()
            require(metric.id.isNotBlank() && metric.label.isNotBlank()) {
                "id and label must be non-blank"
            }
            require(metric.fields.isNotEmpty()) { "fields must be non-empty" }
            require(metric.fields.all { it.id.isNotBlank() }) { "field ids must be non-blank" }
            val fieldIds = metric.fields.map { it.id }
            require(fieldIds.size == fieldIds.toSet().size) { "duplicate field ids" }
            val newMetrics = metrics.toMutableList()
            val idx = newMetrics.indexOfFirst { it.id == metric.id }
            if (idx < 0) {
                newMetrics.add(metric)
            } else {
                if (newMetrics[idx].fields.map { it.id } != fieldIds) {
                    throw IllegalArgumentException("cannot change fields")
                }
                newMetrics[idx] = metric
            }
            persistMutation(newMetrics, samples.toList())
        }
    }

    fun deleteMetric(metricId: String) {
        synchronized(lock) {
            requireReady()
            persistMutation(
                metrics.filter { it.id != metricId },
                samples.filter { it.metricId != metricId },
            )
        }
    }

    override fun upsert(sample: Sample) {
        synchronized(lock) {
            requireReady()
            require(sample.id.isNotBlank() && sample.metricId.isNotBlank()) {
                "id and metricId must be non-blank"
            }
            require(sample.values.values.all { it.isFinite() }) {
                "values must be finite"
            }
            val stamped = sample.copy(modifiedAt = clock.now())
            val newSamples = samples.toMutableList()
            val idx = newSamples.indexOfFirst { it.id == stamped.id }
            if (idx >= 0) {
                newSamples[idx] = stamped
            } else {
                newSamples.add(stamped)
            }
            persistMutation(metrics.toList(), newSamples)
        }
    }

    fun deleteSample(id: String) {
        synchronized(lock) {
            requireReady()
            persistMutation(metrics.toList(), samples.filter { it.id != id })
        }
    }

    fun replaceAll(snapshot: Snapshot) {
        synchronized(lock) {
            persist(canonicalize(snapshot))
        }
    }

    fun dump(): Snapshot = synchronized(lock) {
        snapshotUnlocked().copy(exportedAt = clock.now())
    }

    fun resetLocalData() {
        synchronized(lock) {
            copyUnreadableToCorruptUnlocked()
            file.delete()
            bak.delete()
            tmp.delete()
            trustedLiveFile = false
            persist(seedSnapshot())
        }
    }

    /** Restore-from-Corrupt set-aside: reset steps 1–2, no seed, so replaceAll does not copy garbage onto bak. */
    fun copyUnreadableToCorrupt() {
        synchronized(lock) {
            copyUnreadableToCorruptUnlocked()
        }
    }

    private fun copyUnreadableToCorruptUnlocked() {
        if (file.exists()) {
            Files.copy(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (bak.exists()) {
            Files.copy(bak.toPath(), bakCorrupt.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun snapshotUnlocked(): Snapshot {
        return Snapshot(
            exportedAt = exportedAt,
            metrics = metrics.toList(),
            samples = samples.toList(),
            extras = extras.toMap(),
        )
    }

    private fun requireReady() {
        if (!ready) throw IllegalStateException("store not ready")
    }

    private fun seedSnapshot(): Snapshot {
        return Snapshot(
            exportedAt = clock.now(),
            metrics = BuiltInMetrics.ALL.toList(),
            samples = emptyList(),
        )
    }

    private fun persistMutation(newMetrics: List<MetricDef>, newSamples: List<Sample>) {
        persist(
            Snapshot(
                exportedAt = clock.now(),
                metrics = newMetrics,
                samples = newSamples,
                extras = extras.toMap(),
            ),
        )
    }

    private fun commit(decoded: Snapshot) {
        val next = canonicalize(decoded)
        metrics = next.metrics.toList()
        samples = next.samples.toList()
        extras = next.extras.toMap()
        exportedAt = next.exportedAt
        ready = true
    }

    private fun persist(next: Snapshot) {
        val bytes = JsonCodec.encode(next).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        onBeforeCommitFile()
        if (trustedLiveFile && file.exists()) {
            Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            if (file.exists() && !file.delete()) {
                throw IOException("cannot replace ${file.path}")
            }
            if (!tmp.renameTo(file)) {
                throw IOException("cannot rename tmp to ${file.path}")
            }
        }
        trustedLiveFile = true
        commit(next)
    }

    private fun canonicalize(snapshot: Snapshot): Snapshot {
        val metrics = lastWinsById(snapshot.metrics) { it.id }.map { metric ->
            val fields = lastWinsById(metric.fields) { it.id }
            if (fields === metric.fields) metric else metric.copy(fields = fields)
        }
        val samples = lastWinsById(snapshot.samples) { it.id }
        return snapshot.copy(metrics = metrics, samples = samples)
    }

    private fun <T> lastWinsById(items: List<T>, id: (T) -> String): List<T> {
        if (items.size < 2) return items
        val map = LinkedHashMap<String, T>(items.size)
        for (item in items) {
            map[id(item)] = item
        }
        return if (map.size == items.size) items else map.values.toList()
    }
}
