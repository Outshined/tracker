package org.paul.tracker.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.paul.tracker.ingest.SampleWriter

class StoreTest {
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private val t1 = Instant.parse("2026-08-24T09:00:00Z")
    private val t2 = Instant.parse("2026-08-24T10:00:00Z")
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bhb = BuiltInMetrics.ALL.first { it.id == "bhb" }

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var bak: File
    private lateinit var tmp: File
    private lateinit var corrupt: File
    private lateinit var bakCorrupt: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("store-test").toFile()
        file = File(dir, "store.json")
        bak = File(dir, "store.json.bak")
        tmp = File(dir, "store.json.tmp")
        corrupt = File(dir, "store.json.corrupt")
        bakCorrupt = File(dir, "store.json.bak.corrupt")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `both files absent load seeds four metrics zero samples file created Ready`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        assertTrue(file.exists())
        assertFalse(bak.exists())
        assertEquals(
            listOf("weight", "bhb", "glucose", "blood_pressure"),
            store.metrics().map { it.id },
        )
        assertEquals(BuiltInMetrics.ALL, store.metrics())
        assertEquals(emptyList<Sample>(), store.snapshot().samples)
        assertEquals(t0, store.snapshot().exportedAt)
        val onDisk = JsonCodec.decode(file.readText())
        assertEquals(BuiltInMetrics.ALL.map { it.id }, onDisk.metrics.map { it.id })
        assertEquals(emptyList<Sample>(), onDisk.samples)
    }

    @Test
    fun `existing empty object or empty metrics is Ready with zero metrics and no seed`() {
        file.writeText("{}")
        val emptyObject = Store(file, clock(t0))
        assertEquals(LoadState.Ready, emptyObject.load())
        assertEquals(emptyList<MetricDef>(), emptyObject.metrics())
        assertEquals(emptyList<Sample>(), emptyObject.snapshot().samples)
        assertEquals("{}", file.readText())
        assertFalse(file.readText().contains("weight"))

        file.writeText("""{"metrics":[]}""")
        val emptyMetrics = Store(file, clock(t0))
        assertEquals(LoadState.Ready, emptyMetrics.load())
        assertEquals(emptyList<MetricDef>(), emptyMetrics.metrics())
        assertEquals(emptyList<Sample>(), emptyMetrics.snapshot().samples)
        assertEquals("""{"metrics":[]}""", file.readText())
        assertFalse(emptyMetrics.metrics().any { it.id in setOf("weight", "bhb", "glucose", "blood_pressure") })
    }

    @Test
    fun `upsert then new Store instance load has sample`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(
            sample(
                id = "s1",
                extras = mapOf("note" to JsonPrimitive("fasted")),
                values = mapOf("lb" to 180.0, "pulse" to 72.0),
                source = "watch",
            ),
        )
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        val loaded = again.snapshot().samples.single()
        assertEquals("s1", loaded.id)
        assertEquals(180.0, loaded.values.getValue("lb"), 0.0)
        assertEquals(72.0, loaded.values.getValue("pulse"), 0.0)
        assertEquals("watch", loaded.source)
        assertEquals(JsonPrimitive("fasted"), loaded.extras.getValue("note"))
        assertEquals(t0, loaded.modifiedAt)
    }

    @Test
    fun `upsert same id replaces values and bumps modifiedAt`() {
        val clock = MutableClock(t0)
        val store = Store(file, clock)
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "s1", values = mapOf("lb" to 170.0), modifiedAt = Instant.EPOCH))
        assertEquals(t0, store.snapshot().samples.single().modifiedAt)
        clock.now = t1
        store.upsert(
            sample(
                id = "s1",
                values = mapOf("lb" to 180.0),
                modifiedAt = Instant.EPOCH,
                recordedAt = t1,
                extras = mapOf("note" to JsonPrimitive("fed")),
            ),
        )
        val updated = store.snapshot().samples.single()
        assertEquals("s1", updated.id)
        assertEquals(180.0, updated.values.getValue("lb"), 0.0)
        assertEquals(t1, updated.modifiedAt)
        assertEquals(t1, updated.recordedAt)
        assertEquals(JsonPrimitive("fed"), updated.extras.getValue("note"))
        assertEquals(1, store.snapshot().samples.size)
    }

    @Test
    fun `upsert non-finite throws IAE and file unchanged`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "s1"))
        val before = file.readBytes()
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                store.upsert(sample(id = "s2", values = mapOf("lb" to 1.0, "x" to bad)))
            }
            assertTrue(before.contentEquals(file.readBytes()))
            assertEquals(listOf("s1"), store.snapshot().samples.map { it.id })
        }
    }

    @Test
    fun `upsert blank id throws IAE`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        val before = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            store.upsert(sample(id = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsert(sample(id = "   "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsert(sample(id = "s1", metricId = ""))
        }
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(emptyList<Sample>(), store.snapshot().samples)
    }

    @Test
    fun `deleteSample removes one`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "keep"))
        store.upsert(sample(id = "drop", recordedAt = t1))
        store.deleteSample("drop")
        assertEquals(listOf("keep"), store.snapshot().samples.map { it.id })
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertEquals(listOf("keep"), again.snapshot().samples.map { it.id })
        store.deleteSample("missing")
        assertEquals(listOf("keep"), store.snapshot().samples.map { it.id })
    }

    @Test
    fun `upsertMetric create same field ids changes label unit different field ids IAE catalog unchanged`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        val created = MetricDef(
            id = "custom",
            label = "Custom",
            fields = listOf(FieldDef(id = "f1", label = "A", unit = "u")),
        )
        store.upsertMetric(created)
        assertTrue(store.metrics().any { it.id == "custom" })

        store.upsertMetric(
            created.copy(
                label = "Renamed",
                fields = listOf(FieldDef(id = "f1", label = "B", unit = "v")),
            ),
        )
        val updated = store.metrics().single { it.id == "custom" }
        assertEquals("Renamed", updated.label)
        assertEquals("B", updated.fields.single().label)
        assertEquals("v", updated.fields.single().unit)
        assertEquals("f1", updated.fields.single().id)

        val before = store.metrics()
        val beforeBytes = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(
                created.copy(fields = listOf(FieldDef(id = "other", label = "B", unit = "v"))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(
                weight.copy(fields = listOf(FieldDef(id = "kg", label = "Weight", unit = "kg"))),
            )
        }
        assertEquals(before, store.metrics())
        assertTrue(beforeBytes.contentEquals(file.readBytes()))
        assertEquals("Renamed", store.metrics().single { it.id == "custom" }.label)
        assertEquals("lb", store.metrics().single { it.id == "weight" }.fields.single().id)
    }

    @Test
    fun `deleteMetric removes def and its samples other metrics remain`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "w1", metricId = "weight"))
        store.upsert(sample(id = "b1", metricId = "bhb", values = mapOf("mmol_l" to 1.2)))
        store.deleteMetric("weight")
        assertFalse(store.metrics().any { it.id == "weight" })
        assertTrue(store.metrics().any { it.id == "bhb" })
        assertEquals(listOf("b1"), store.snapshot().samples.map { it.id })
        assertEquals(emptyList<Sample>(), store.samplesFor("weight"))
        assertEquals(listOf("b1"), store.samplesFor("bhb").map { it.id })
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertFalse(again.metrics().any { it.id == "weight" })
        assertEquals(listOf("b1"), again.snapshot().samples.map { it.id })
    }

    @Test
    fun `replaceAll is full replace local-only sample gone`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "local-only"))
        val remote = Snapshot(
            exportedAt = t2,
            metrics = listOf(bhb),
            samples = listOf(
                sample(id = "remote", metricId = "bhb", values = mapOf("mmol_l" to 0.8)),
            ),
        )
        store.replaceAll(remote)
        assertEquals(listOf("bhb"), store.metrics().map { it.id })
        assertEquals(listOf("remote"), store.snapshot().samples.map { it.id })
        assertFalse(store.snapshot().samples.any { it.id == "local-only" })
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertEquals(listOf("bhb"), again.metrics().map { it.id })
        assertEquals(listOf("remote"), again.snapshot().samples.map { it.id })
    }

    @Test
    fun `dump exportedAt uses clock file exportedAt unchanged`() {
        val clock = MutableClock(t0)
        val store = Store(file, clock)
        assertEquals(LoadState.Ready, store.load())
        val onDiskBefore = JsonCodec.decode(file.readText()).exportedAt
        assertEquals(t0, onDiskBefore)
        clock.now = t2
        val dumped = store.dump()
        assertEquals(t2, dumped.exportedAt)
        assertEquals(store.metrics(), dumped.metrics)
        assertEquals(t0, JsonCodec.decode(file.readText()).exportedAt)
        assertEquals(t0, store.snapshot().exportedAt)
    }

    @Test
    fun `after trusted persist bak exists and parses`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "s1"))
        assertTrue(bak.exists())
        val decoded = JsonCodec.decode(bak.readText(Charsets.UTF_8))
        assertEquals(BuiltInMetrics.ALL.map { it.id }, decoded.metrics.map { it.id })
        assertEquals(emptyList<Sample>(), decoded.samples)
        val live = JsonCodec.decode(file.readText())
        assertEquals(listOf("s1"), live.samples.map { it.id })
    }

    @Test
    fun `garbage live good bak loads bak live to corrupt live repaired bak still good`() {
        val good = JsonCodec.encode(
            Snapshot(
                exportedAt = t1,
                metrics = listOf(weight),
                samples = listOf(sample(id = "from-bak")),
            ),
        )
        bak.writeText(good)
        val garbage = "<html>not json</html>"
        file.writeText(garbage)
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        assertEquals(listOf("from-bak"), store.snapshot().samples.map { it.id })
        assertEquals(listOf("weight"), store.metrics().map { it.id })
        assertTrue(corrupt.exists())
        assertEquals(garbage, corrupt.readText())
        assertEquals(good, bak.readText())
        JsonCodec.decode(bak.readText())
        val repaired = JsonCodec.decode(file.readText())
        assertEquals(listOf("from-bak"), repaired.samples.map { it.id })
        assertFalse(file.readText().contains("<html>"))
    }

    @Test
    fun `both garbage Corrupt files not replaced by seed no store json rewrite`() {
        val liveGarbage = "NOT JSON LIVE"
        val bakGarbage = "NOT JSON BAK"
        file.writeText(liveGarbage)
        bak.writeText(bakGarbage)
        val liveModified = file.lastModified()
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Corrupt("Local data file is unreadable."), store.load())
        assertEquals(liveGarbage, file.readText())
        assertEquals(bakGarbage, bak.readText())
        assertEquals(liveModified, file.lastModified())
        assertFalse(file.readText().contains("weight"))
        assertEquals(emptyList<MetricDef>(), store.metrics())
        assertEquals(emptyList<Sample>(), store.snapshot().samples)
    }

    @Test
    fun `after Corrupt queries return empty and do not throw upsert throws ISE`() {
        file.writeText("garbage")
        bak.writeText("garbage")
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Corrupt("Local data file is unreadable."), store.load())
        assertEquals(Snapshot(Instant.EPOCH, emptyList(), emptyList()), store.snapshot())
        assertEquals(emptyList<MetricDef>(), store.metrics())
        assertEquals(emptyList<Sample>(), store.samplesFor("weight"))
        val dumped = store.dump()
        assertEquals(emptyList<MetricDef>(), dumped.metrics)
        assertEquals(emptyList<Sample>(), dumped.samples)
        val ise = assertThrows(IllegalStateException::class.java) {
            store.upsert(sample(id = "s1"))
        }
        assertEquals("store not ready", ise.message)
        assertThrows(IllegalStateException::class.java) { store.upsertMetric(weight) }
        assertThrows(IllegalStateException::class.java) { store.deleteSample("s1") }
        assertThrows(IllegalStateException::class.java) { store.deleteMetric("weight") }
    }

    @Test
    fun `resetLocalData after Corrupt sets aside corrupt and seeds built-ins`() {
        val liveGarbage = "LIVE GARBAGE"
        val bakGarbage = "BAK GARBAGE"
        file.writeText(liveGarbage)
        bak.writeText(bakGarbage)
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Corrupt("Local data file is unreadable."), store.load())
        store.resetLocalData()
        assertEquals(liveGarbage, corrupt.readText())
        assertEquals(bakGarbage, bakCorrupt.readText())
        assertEquals(BuiltInMetrics.ALL.map { it.id }, store.metrics().map { it.id })
        assertEquals(emptyList<Sample>(), store.snapshot().samples)
        val onDisk = JsonCodec.decode(file.readText())
        assertEquals(BuiltInMetrics.ALL.map { it.id }, onDisk.metrics.map { it.id })
        store.upsert(sample(id = "after-reset"))
        assertEquals(listOf("after-reset"), store.snapshot().samples.map { it.id })
    }

    @Test
    fun `leftover tmp deleted not parsed`() {
        tmp.writeText(
            JsonCodec.encode(
                Snapshot(
                    exportedAt = t0,
                    metrics = listOf(
                        MetricDef(
                            id = "from-tmp",
                            label = "From tmp",
                            fields = listOf(FieldDef(id = "x", label = "X", unit = "u")),
                        ),
                    ),
                    samples = listOf(sample(id = "tmp-sample", metricId = "from-tmp", values = mapOf("x" to 1.0))),
                ),
            ),
        )
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        assertFalse(tmp.exists())
        assertFalse(store.metrics().any { it.id == "from-tmp" })
        assertEquals(BuiltInMetrics.ALL.map { it.id }, store.metrics().map { it.id })
        assertEquals(emptyList<Sample>(), store.snapshot().samples)
    }

    @Test
    fun `persist IO failure via onBeforeCommitFile second upsert throws new Store has only first sample`() {
        var fail = false
        val store = Store(file, clock(t0), onBeforeCommitFile = {
            if (fail) throw IOException("injected")
        })
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "s1"))
        fail = true
        val thrown = assertThrows(IOException::class.java) {
            store.upsert(sample(id = "s2"))
        }
        assertEquals("injected", thrown.message)
        assertEquals(listOf("s1"), store.snapshot().samples.map { it.id })
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertEquals(listOf("s1"), again.snapshot().samples.map { it.id })
        assertFalse(again.snapshot().samples.any { it.id == "s2" })
    }

    @Test
    fun `SampleWriter upsert visible in new Store instance`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        val w: SampleWriter = store
        w.upsert(sample(id = "via-writer"))
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertEquals(listOf("via-writer"), again.snapshot().samples.map { it.id })
    }

    @Test
    fun `orphans kept on disk not returned as a metric row`() {
        file.writeText(
            JsonCodec.encode(
                Snapshot(
                    exportedAt = t0,
                    metrics = listOf(weight),
                    samples = listOf(
                        sample(id = "owned"),
                        sample(id = "orphan", metricId = "gone", values = mapOf("x" to 1.0)),
                    ),
                ),
            ),
        )
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        assertEquals(listOf("weight"), store.metrics().map { it.id })
        assertFalse(store.metrics().any { it.id == "gone" })
        assertEquals(setOf("owned", "orphan"), store.snapshot().samples.map { it.id }.toSet())
        assertEquals(listOf("orphan"), store.samplesFor("gone").map { it.id })
        val again = Store(file, clock(t1))
        assertEquals(LoadState.Ready, again.load())
        assertEquals(listOf("weight"), again.metrics().map { it.id })
        assertEquals(setOf("owned", "orphan"), again.snapshot().samples.map { it.id }.toSet())
        val onDisk = JsonCodec.decode(file.readText())
        assertEquals("gone", onDisk.samples.single { it.id == "orphan" }.metricId)
    }

    @Test
    fun `queries before load return empty and do not throw`() {
        val store = Store(file, clock(t0))
        assertEquals(Snapshot(Instant.EPOCH, emptyList(), emptyList()), store.snapshot())
        assertEquals(emptyList<MetricDef>(), store.metrics())
        assertEquals(emptyList<Sample>(), store.samplesFor("weight"))
        val dumped = store.dump()
        assertEquals(emptyList<MetricDef>(), dumped.metrics)
        assertEquals(emptyList<Sample>(), dumped.samples)
        assertEquals(t0, dumped.exportedAt)
        assertThrows(IllegalStateException::class.java) { store.upsert(sample(id = "s1")) }
        assertFalse(file.exists())
    }

    @Test
    fun `samplesFor is recordedAt descending`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "old", recordedAt = t0))
        store.upsert(sample(id = "new", recordedAt = t2))
        store.upsert(sample(id = "mid", recordedAt = t1))
        assertEquals(listOf("new", "mid", "old"), store.samplesFor("weight").map { it.id })
    }

    @Test
    fun `replaceAll from Corrupt persists remote without copying garbage onto bak`() {
        val liveGarbage = "LIVE GARBAGE"
        val bakGarbage = "BAK GARBAGE"
        file.writeText(liveGarbage)
        bak.writeText(bakGarbage)
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Corrupt("Local data file is unreadable."), store.load())
        store.replaceAll(
            Snapshot(
                exportedAt = t2,
                metrics = listOf(bhb),
                samples = listOf(sample(id = "restored", metricId = "bhb", values = mapOf("mmol_l" to 0.5))),
            ),
        )
        assertEquals(listOf("bhb"), store.metrics().map { it.id })
        assertEquals(listOf("restored"), store.snapshot().samples.map { it.id })
        assertEquals(bakGarbage, bak.readText())
        val live = JsonCodec.decode(file.readText())
        assertEquals(listOf("restored"), live.samples.map { it.id })
    }

    @Test
    fun `upsertMetric rejects blank id empty fields duplicate field ids`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        val before = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(weight.copy(id = " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(weight.copy(label = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(weight.copy(fields = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(
                weight.copy(id = "n", fields = listOf(FieldDef(id = " ", label = "A", unit = "u"))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.upsertMetric(
                MetricDef(
                    id = "n",
                    label = "N",
                    fields = listOf(
                        FieldDef(id = "f", label = "A", unit = "u"),
                        FieldDef(id = "f", label = "B", unit = "v"),
                    ),
                ),
            )
        }
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(BuiltInMetrics.ALL.map { it.id }, store.metrics().map { it.id })
    }

    @Test
    fun `replaceAll last-wins duplicate ids`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.replaceAll(
            Snapshot(
                exportedAt = t0,
                metrics = listOf(
                    weight.copy(label = "First"),
                    weight.copy(label = "Second"),
                ),
                samples = listOf(
                    sample(id = "s1", values = mapOf("lb" to 1.0)),
                    sample(id = "s1", values = mapOf("lb" to 2.0)),
                ),
            ),
        )
        assertEquals(listOf("weight"), store.metrics().map { it.id })
        assertEquals("Second", store.metrics().single().label)
        assertEquals(1, store.snapshot().samples.size)
        assertEquals(2.0, store.snapshot().samples.single().values.getValue("lb"), 0.0)
    }

    @Test
    fun `snapshot defensive copy is not live identity`() {
        val store = Store(file, clock(t0))
        assertEquals(LoadState.Ready, store.load())
        store.upsert(sample(id = "s1"))
        val snap = store.snapshot()
        store.upsert(sample(id = "s2"))
        assertEquals(listOf("s1"), snap.samples.map { it.id })
        assertEquals(listOf("s1", "s2"), store.snapshot().samples.map { it.id })
    }

    private fun clock(instant: Instant): Clock = Clock { instant }

    private fun sample(
        id: String,
        metricId: String = "weight",
        recordedAt: Instant = t0,
        modifiedAt: Instant = Instant.EPOCH,
        values: Map<String, Double> = mapOf("lb" to 180.0),
        source: String = Sources.MANUAL,
        extras: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ) = Sample(
        id = id,
        metricId = metricId,
        recordedAt = recordedAt,
        modifiedAt = modifiedAt,
        source = source,
        values = values,
        extras = extras,
    )

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }
}
