package org.paul.tracker

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.paul.tracker.data.BuiltInMetrics
import org.paul.tracker.data.Clock
import org.paul.tracker.data.LoadState
import org.paul.tracker.data.MetricDef
import org.paul.tracker.data.Sample
import org.paul.tracker.data.Sources
import org.paul.tracker.data.Store
import org.paul.tracker.stats.defaultSelectedMetricIds

class AppViewModelTest {
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var io: QueueDispatcher
    private lateinit var store: Store
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("vm-test").toFile()
        file = File(dir, "store.json")
        io = QueueDispatcher()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `first frame is Loading then Ready after load`() {
        createVm()
        assertEquals(LoadState.Loading, vm.state.value.load)
        assertEquals(emptyList<MetricDef>(), vm.state.value.snapshot.metrics)
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals(BuiltInMetrics.ALL.map { it.id }, vm.state.value.snapshot.metrics.map { it.id })
        assertEquals(
            BuiltInMetrics.ALL.map { it.id }.toSet(),
            vm.state.value.graphSelectedIds,
        )
    }

    @Test
    fun `load of empty object is Ready with zero metrics and no seed`() {
        file.writeText("{}")
        createVm()
        assertEquals(LoadState.Loading, vm.state.value.load)
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals(emptyList<MetricDef>(), vm.state.value.snapshot.metrics)
        assertEquals(emptyList<Sample>(), vm.state.value.snapshot.samples)
        assertEquals(emptySet<String>(), vm.state.value.graphSelectedIds)
        assertEquals("{}", file.readText())
        assertFalse(file.readText().contains("weight"))
    }

    @Test
    fun `corrupt files yield Corrupt empty snapshot`() {
        file.writeText("NOT JSON")
        File(dir, "store.json.bak").writeText("ALSO BAD")
        createVm()
        io.runAll()
        assertEquals(LoadState.Corrupt("Local data file is unreadable."), vm.state.value.load)
        assertEquals(emptyList<MetricDef>(), vm.state.value.snapshot.metrics)
        assertEquals(emptyList<Sample>(), vm.state.value.snapshot.samples)
        assertEquals("NOT JSON", file.readText())
    }

    @Test
    fun `tab switch does not reset MetricsSub`() {
        createVm()
        io.runAll()
        vm.openAddMetric()
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        vm.selectTab(Tab.Graphs)
        assertEquals(Tab.Graphs, vm.state.value.tab)
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        vm.selectTab(Tab.Metrics)
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        assertEquals("", vm.state.value.addLabel)
    }

    @Test
    fun `blank label save is rejected without writing`() {
        createVm()
        io.runAll()
        val before = file.readText()
        vm.openAddMetric()
        vm.setAddLabel("  ")
        vm.setFieldLabel(0, "Count")
        vm.saveMetric()
        assertNotNull(vm.state.value.formError)
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        assertEquals(before, file.readText())
        assertEquals(4, vm.state.value.snapshot.metrics.size)
    }

    @Test
    fun `add metric save persists uuid ids and returns to list`() {
        createVm()
        io.runAll()
        vm.openAddMetric()
        vm.setAddLabel("Steps")
        vm.setFieldLabel(0, "Count")
        vm.setFieldUnit(0, "steps")
        vm.addField()
        vm.setFieldLabel(1, "Goal")
        vm.setFieldUnit(1, "")
        vm.saveMetric()
        io.runAll()
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
        assertNull(vm.state.value.formError)
        val created = vm.state.value.snapshot.metrics.single { it.label == "Steps" }
        assertTrue(created.id.isNotBlank())
        assertEquals(listOf("Count", "Goal"), created.fields.map { it.label })
        assertEquals(listOf("steps", ""), created.fields.map { it.unit })
        assertTrue(created.fields.all { it.id.isNotBlank() && it.id != created.id })
        assertEquals(2, created.fields.map { it.id }.toSet().size)
        val reloaded = Store(file, Clock { t0 })
        assertEquals(LoadState.Ready, reloaded.load())
        assertEquals("Steps", reloaded.metrics().single { it.id == created.id }.label)
    }

    @Test
    fun `edit metric save uses mergeEditedMetric and freezes field ids`() {
        createVm()
        io.runAll()
        val original = vm.state.value.snapshot.metrics.first { it.id == "weight" }
        vm.openEditMetric("weight")
        assertEquals(MetricsSub.Edit, vm.state.value.metricsSub)
        assertEquals("Weight", vm.state.value.addLabel)
        vm.setAddLabel("Massa")
        vm.setFieldLabel(0, "Peso")
        vm.setFieldUnit(0, "kg")
        vm.addField()
        assertEquals(1, vm.state.value.addFields.size)
        vm.saveMetric()
        io.runAll()
        val edited = vm.state.value.snapshot.metrics.first { it.id == "weight" }
        assertEquals("Massa", edited.label)
        assertEquals(original.fields.map { it.id }, edited.fields.map { it.id })
        assertEquals("Peso", edited.fields.single().label)
        assertEquals("kg", edited.fields.single().unit)
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
    }

    @Test
    fun `deleteMetric uses selectionAfterDelete and leaves list if editing that metric`() {
        createVm()
        io.runAll()
        store.upsert(
            Sample(
                id = "s1",
                metricId = "weight",
                recordedAt = t0,
                modifiedAt = t0,
                source = Sources.MANUAL,
                values = mapOf("lb" to 180.0),
            ),
        )
        io.runAll()
        createVm()
        io.runAll()
        assertEquals(setOf("weight"), vm.state.value.graphSelectedIds)
        vm.openEditMetric("weight")
        vm.deleteMetric("weight")
        io.runAll()
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
        assertNull(vm.state.value.entryMetricId)
        assertFalse(vm.state.value.snapshot.metrics.any { it.id == "weight" })
        assertEquals(emptyList<Sample>(), vm.state.value.snapshot.samples)
        val remaining = vm.state.value.snapshot.metrics
        assertEquals(defaultSelectedMetricIds(remaining, emptyList()).toSet(), vm.state.value.graphSelectedIds)
    }

    @Test
    fun `resetLocalData after Corrupt seeds built-ins and applyCatalogReset`() {
        file.writeText("NOT JSON")
        File(dir, "store.json.bak").writeText("ALSO BAD")
        createVm()
        io.runAll()
        assertTrue(vm.state.value.load is LoadState.Corrupt)
        vm.openAddMetric()
        vm.selectTab(Tab.Settings)
        vm.resetLocalData()
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals(BuiltInMetrics.ALL.map { it.id }, vm.state.value.snapshot.metrics.map { it.id })
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
        assertEquals(Tab.Settings, vm.state.value.tab)
        assertEquals(BuiltInMetrics.ALL.map { it.id }.toSet(), vm.state.value.graphSelectedIds)
        assertTrue(File(dir, "store.json.corrupt").exists())
    }

    @Test
    fun `saveMetric rejects blank field label without persist`() {
        createVm()
        io.runAll()
        val beforeIds = vm.state.value.snapshot.metrics.map { it.id }
        vm.openAddMetric()
        vm.setAddLabel("Nope")
        vm.setFieldLabel(0, "  ")
        vm.saveMetric()
        assertNotNull(vm.state.value.formError)
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        assertEquals(beforeIds, vm.state.value.snapshot.metrics.map { it.id })
    }

    private fun createVm() {
        val clock = Clock { t0 }
        store = Store(file, clock)
        vm = AppViewModel(store, clock, ZoneOffset.UTC, io)
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.add(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }
}
