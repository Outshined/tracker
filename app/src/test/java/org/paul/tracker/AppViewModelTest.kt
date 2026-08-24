package org.paul.tracker

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
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
import org.paul.tracker.data.ConfigStore
import org.paul.tracker.data.FieldDef
import org.paul.tracker.data.JsonCodec
import org.paul.tracker.data.LoadState
import org.paul.tracker.data.MetricDef
import org.paul.tracker.data.Sample
import org.paul.tracker.data.Snapshot
import org.paul.tracker.data.Sources
import org.paul.tracker.data.Store
import org.paul.tracker.stats.AverageMode
import org.paul.tracker.stats.RangePreset
import org.paul.tracker.stats.defaultSelectedMetricIds
import org.paul.tracker.webdav.WebDavClient

class AppViewModelTest {
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private val t1 = Instant.parse("2026-08-24T09:00:00Z")
    private val t2 = Instant.parse("2026-08-24T10:00:00Z")
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var io: QueueDispatcher
    private lateinit var store: Store
    private lateinit var config: ConfigStore
    private lateinit var clock: MutableClock
    private lateinit var fakeDav: FakeWebDavClient
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("vm-test").toFile()
        file = File(dir, "store.json")
        io = QueueDispatcher()
        clock = MutableClock(t0)
        fakeDav = FakeWebDavClient()
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

    @Test
    fun `T-configstore-missing-file-defaults-no-throw-no-file`() {
        createVm()
        io.runAll()
        val cfgFile = File(dir, "webdav.json")
        assertFalse(cfgFile.exists())
        assertEquals("", vm.state.value.settingsUrl)
        assertEquals("", vm.state.value.settingsPass)
        assertNull(vm.state.value.lastBackupAt)
        assertEquals("", vm.state.value.lastError)
        assertFalse(vm.state.value.load is LoadState.Corrupt)
    }

    @Test
    fun `T-configstore-garbage-file-defaults-no-throw`() {
        File(dir, "webdav.json").writeText("GARBAGE {")
        createVm()
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals("", vm.state.value.settingsUrl)
        assertEquals("", vm.state.value.settingsUser)
        assertFalse(vm.state.value.load is LoadState.Corrupt)
        assertEquals("GARBAGE {", File(dir, "webdav.json").readText())
    }

    @Test
    fun `T-restore-html-array-not-json-leaves-local-sample`() {
        val bodies = listOf("<html>not a dump</html>", "[]", "not json")
        for (body in bodies) {
            dir.deleteRecursively()
            dir.mkdirs()
            file = File(dir, "store.json")
            fakeDav = FakeWebDavClient()
            clock.now = t0
            createVm()
            io.runAll()
            store.upsert(localSample())
            createVm()
            io.runAll()
            configureDav()
            fakeDav.getResult = body.toByteArray(Charsets.UTF_8)
            vm.restore()
            io.runAll()
            assertEquals(
                "body=$body",
                listOf("local-only"),
                vm.state.value.snapshot.samples.map { it.id },
            )
            assertEquals("Server file is not a valid dump", vm.state.value.lastError)
            assertNull(vm.state.value.lastRestoreAt)
            assertEquals("local-only", store.snapshot().samples.single().id)
            assertFalse(vm.state.value.davInFlight)
        }
    }

    @Test
    fun `T-restore-valid-dump-replaceAll-local-only-gone`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        fakeDav.getResult = remoteDump()
        vm.restore()
        io.runAll()
        assertEquals(listOf("steps"), vm.state.value.snapshot.metrics.map { it.id })
        assertEquals(listOf("remote-1"), vm.state.value.snapshot.samples.map { it.id })
        assertFalse(vm.state.value.snapshot.samples.any { it.id == "local-only" })
        assertEquals(t0, vm.state.value.lastRestoreAt)
        assertEquals("", vm.state.value.lastError)
        assertEquals(LoadState.Ready, vm.state.value.load)
        val reloaded = Store(file, clock)
        assertEquals(LoadState.Ready, reloaded.load())
        assertEquals(listOf("remote-1"), reloaded.snapshot().samples.map { it.id })
    }

    @Test
    fun `T-shouldExtraConfirm-local-positive-remote-zero`() {
        assertTrue(shouldExtraConfirm(1, 0))
        assertTrue(shouldExtraConfirm(3, 0))
        assertFalse(shouldExtraConfirm(0, 0))
        assertFalse(shouldExtraConfirm(1, 1))
        assertFalse(shouldExtraConfirm(0, 1))
        assertFalse(shouldExtraConfirm(2, 5))
    }

    @Test
    fun `T-restore-empty-remote-needs-extra-confirm-before-replace`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        fakeDav.getResult = remoteDump(samples = emptyList())
        vm.restore()
        io.runAll()
        assertTrue(vm.state.value.restoreNeedsExtraConfirm)
        assertEquals(listOf("local-only"), vm.state.value.snapshot.samples.map { it.id })
        assertNull(vm.state.value.lastRestoreAt)
        assertEquals(1, fakeDav.getCount)
        vm.confirmEmptyRestore()
        io.runAll()
        assertFalse(vm.state.value.restoreNeedsExtraConfirm)
        assertEquals(emptyList<Sample>(), vm.state.value.snapshot.samples)
        assertEquals(t0, vm.state.value.lastRestoreAt)
        assertEquals(1, fakeDav.getCount)
        assertEquals(listOf("steps"), vm.state.value.snapshot.metrics.map { it.id })
    }

    @Test
    fun `cancel extra confirm leaves local sample`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        fakeDav.getResult = remoteDump(samples = emptyList())
        vm.restore()
        io.runAll()
        vm.cancelEmptyRestore()
        assertEquals(listOf("local-only"), vm.state.value.snapshot.samples.map { it.id })
        assertNull(vm.state.value.lastRestoreAt)
        assertFalse(vm.state.value.davInFlight)
        assertFalse(vm.state.value.restoreNeedsExtraConfirm)
    }

    @Test
    fun `T-backup-disabled-when-not-Ready`() {
        file.writeText("NOT JSON")
        File(dir, "store.json.bak").writeText("ALSO BAD")
        createVm()
        io.runAll()
        assertTrue(vm.state.value.load is LoadState.Corrupt)
        assertFalse(backupEnabled(vm.state.value.load, vm.state.value.davInFlight))
        configureDav()
        vm.backup()
        io.runAll()
        assertEquals(0, fakeDav.putCount)
        assertNull(vm.state.value.lastBackupAt)
    }

    @Test
    fun `T-restore-omitted-selected-metric-resets-graph-and-sub`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        assertEquals(setOf("weight"), vm.state.value.graphSelectedIds)
        vm.openAddMetric()
        configureDav()
        fakeDav.getResult = remoteDump()
        vm.restore()
        io.runAll()
        val snap = vm.state.value.snapshot
        assertEquals(
            defaultSelectedMetricIds(snap.metrics, snap.samples).toSet(),
            vm.state.value.graphSelectedIds,
        )
        assertEquals(setOf("steps"), vm.state.value.graphSelectedIds)
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
        assertNull(vm.state.value.entryMetricId)
    }

    @Test
    fun `T-lastBackupAt-is-not-dump-exportedAt`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        clock.now = t1
        vm.backup()
        io.runAll()
        assertEquals(t1, vm.state.value.lastBackupAt)
        assertEquals(t0, store.snapshot().exportedAt)
        val dumped = JsonCodec.decode(String(fakeDav.lastPut!!, Charsets.UTF_8))
        assertEquals(t1, dumped.exportedAt)
        clock.now = t2
        store.upsert(localSample(id = "later"))
        assertEquals(t2, store.snapshot().exportedAt)
        assertEquals(t1, vm.state.value.lastBackupAt)
        assertEquals(t1, ConfigStore(File(dir, "webdav.json")).load().lastBackupAt)
    }

    @Test
    fun `T-dump-has-no-password-keys-or-credentials`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        clock.now = t1
        vm.backup()
        io.runAll()
        val dumpText = String(fakeDav.lastPut!!, Charsets.UTF_8)
        val dumpObj = Json.parseToJsonElement(dumpText) as JsonObject
        assertFalse(dumpObj.containsKey("password"))
        assertFalse(dumpObj.containsKey("username"))
        assertFalse(dumpObj.containsKey("url"))
        assertFalse(dumpText.contains("password"))
        assertFalse(dumpText.contains("app-password-xyz"))
        assertFalse(dumpText.contains("webdav.json"))
        assertTrue(dumpObj.containsKey("exportedAt"))
        assertTrue(dumpObj.containsKey("metrics"))
        assertTrue(dumpObj.containsKey("samples"))
        val cfgText = File(dir, "webdav.json").readText()
        assertTrue(cfgText.contains("app-password-xyz"))
        assertTrue(cfgText.contains("\"password\""))
        assertFalse(file.readText().contains("app-password-xyz"))
        val encoded = JsonCodec.encode(store.dump())
        assertFalse(encoded.contains("password"))
        assertFalse(encoded.contains("webdav.json"))
        assertFalse(encoded.contains("app-password-xyz"))
    }

    @Test
    fun `T-configstore-path-never-in-JsonCodec-encode`() {
        createVm()
        io.runAll()
        configureDav()
        val encoded = JsonCodec.encode(store.dump())
        assertFalse(encoded.contains(File(dir, "webdav.json").path))
        assertFalse(encoded.contains("webdav.json"))
        assertFalse(encoded.contains("password"))
    }

    @Test
    fun `restore 404 does not replaceAll`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        createVm()
        io.runAll()
        configureDav()
        fakeDav.getResult = null
        vm.restore()
        io.runAll()
        assertEquals("No backup at that URL", vm.state.value.lastError)
        assertEquals(listOf("local-only"), vm.state.value.snapshot.samples.map { it.id })
        assertNull(vm.state.value.lastRestoreAt)
    }

    @Test
    fun `backup HTTP and timeout and IO set lastError without lastBackupAt`() {
        createVm()
        io.runAll()
        configureDav()
        fakeDav.putError = WebDavClient.HttpException(500, "HTTP 500")
        vm.backup()
        io.runAll()
        assertEquals("HTTP 500", vm.state.value.lastError)
        assertNull(vm.state.value.lastBackupAt)
        fakeDav.putError = SocketTimeoutException("read timed out")
        vm.backup()
        io.runAll()
        assertEquals("timeout", vm.state.value.lastError)
        assertNull(vm.state.value.lastBackupAt)
        fakeDav.putError = IOException("connection reset")
        vm.backup()
        io.runAll()
        assertEquals("IO connection reset", vm.state.value.lastError)
        assertNull(vm.state.value.lastBackupAt)
        assertEquals("IO connection reset", ConfigStore(File(dir, "webdav.json")).load().lastError)
    }

    @Test
    fun `backup success then failed put keeps lastBackupAt`() {
        createVm()
        io.runAll()
        configureDav()
        clock.now = t1
        vm.backup()
        io.runAll()
        assertEquals(t1, vm.state.value.lastBackupAt)
        fakeDav.putError = IOException("nope")
        clock.now = t2
        vm.backup()
        io.runAll()
        assertEquals(t1, vm.state.value.lastBackupAt)
        assertEquals("IO nope", vm.state.value.lastError)
    }

    @Test
    fun `restore from Corrupt copies sidecars and replaceAll without seeding bak`() {
        file.writeText("LIVE GARBAGE")
        File(dir, "store.json.bak").writeText("BAK GARBAGE")
        createVm()
        io.runAll()
        assertTrue(vm.state.value.load is LoadState.Corrupt)
        configureDav()
        fakeDav.getResult = remoteDump()
        vm.restore()
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals(listOf("remote-1"), vm.state.value.snapshot.samples.map { it.id })
        assertEquals("LIVE GARBAGE", File(dir, "store.json.corrupt").readText())
        assertEquals("BAK GARBAGE", File(dir, "store.json.bak.corrupt").readText())
        assertEquals("BAK GARBAGE", File(dir, "store.json.bak").readText())
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
    }

    @Test
    fun `ConfigStore persist failure after replaceAll still applyCatalogReset Ready`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        var failCfg = false
        val cfgFile = File(dir, "webdav.json")
        val cfg = ConfigStore(
            cfgFile,
            onBeforeCommitFile = {
                if (failCfg) throw IOException("cfg")
            },
        )
        createVm(configOverride = cfg)
        io.runAll()
        vm.openAddMetric()
        configureDav()
        fakeDav.getResult = remoteDump()
        failCfg = true
        vm.restore()
        io.runAll()
        assertEquals(LoadState.Ready, vm.state.value.load)
        assertEquals(listOf("remote-1"), vm.state.value.snapshot.samples.map { it.id })
        assertFalse(vm.state.value.snapshot.samples.any { it.id == "local-only" })
        assertEquals(MetricsSub.List, vm.state.value.metricsSub)
        assertEquals(setOf("steps"), vm.state.value.graphSelectedIds)
        assertEquals(t0, vm.state.value.lastRestoreAt)
        assertEquals("IO cfg", vm.state.value.lastError)
        assertFalse(vm.state.value.davInFlight)
        val reloaded = Store(file, clock)
        assertEquals(LoadState.Ready, reloaded.load())
        assertEquals(listOf("remote-1"), reloaded.snapshot().samples.map { it.id })
        val onDisk = ConfigStore(cfgFile).load()
        assertNull(onDisk.lastRestoreAt)
        assertEquals("app-password-xyz", onDisk.password)
    }

    @Test
    fun `ConfigStore persist failure after PUT still sets lastBackupAt`() {
        var failCfg = false
        val cfgFile = File(dir, "webdav.json")
        val cfg = ConfigStore(
            cfgFile,
            onBeforeCommitFile = {
                if (failCfg) throw IOException("cfg")
            },
        )
        createVm(configOverride = cfg)
        io.runAll()
        configureDav()
        failCfg = true
        clock.now = t1
        vm.backup()
        io.runAll()
        assertEquals(1, fakeDav.putCount)
        assertEquals(t1, vm.state.value.lastBackupAt)
        assertEquals("IO cfg", vm.state.value.lastError)
        assertFalse(vm.state.value.davInFlight)
        val onDisk = ConfigStore(cfgFile).load()
        assertNull(onDisk.lastBackupAt)
    }

    @Test
    fun `extra-confirm Restore is single-flight and does not GET again`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        var storeCommits = 0
        val counting = Store(file, clock, onBeforeCommitFile = { storeCommits++ })
        createVm(storeOverride = counting)
        io.runAll()
        configureDav()
        fakeDav.getResult = remoteDump(samples = emptyList())
        vm.restore()
        io.runAll()
        assertTrue(vm.state.value.restoreNeedsExtraConfirm)
        assertEquals(1, fakeDav.getCount)
        val commitsBefore = storeCommits
        fakeDav.getResult = null
        fakeDav.getError = IOException("second get")
        vm.confirmEmptyRestore()
        vm.confirmEmptyRestore()
        vm.cancelEmptyRestore()
        io.runAll()
        assertEquals(1, fakeDav.getCount)
        assertEquals(commitsBefore + 1, storeCommits)
        assertEquals(emptyList<Sample>(), vm.state.value.snapshot.samples)
        assertEquals(listOf("steps"), vm.state.value.snapshot.metrics.map { it.id })
        assertEquals(t0, vm.state.value.lastRestoreAt)
        assertEquals("", vm.state.value.lastError)
        assertFalse(vm.state.value.restoreNeedsExtraConfirm)
        assertFalse(vm.state.value.davInFlight)
        assertEquals(LoadState.Ready, vm.state.value.load)
    }

    @Test
    fun `credential persist last-write-wins`() {
        val cfgFile = File(dir, "webdav.json")
        val writes = mutableListOf<String>()
        var inject = true
        val cfg = ConfigStore(
            cfgFile,
            onBeforeTransform = {
                if (inject) {
                    inject = false
                    vm.setSettingsPass("latest-pass")
                }
            },
            onBeforeCommitFile = {
                writes.add(ConfigStore.decode(File(cfgFile.path + ".tmp").readText()).password)
            },
        )
        createVm(configOverride = cfg)
        io.runAll()
        vm.setSettingsUrl("https://nas.example/tracker.json")
        vm.setSettingsUser("paul")
        vm.setSettingsPass("stale-pass")
        io.runAll()
        assertEquals("latest-pass", writes.first())
        assertFalse(writes.first() == "stale-pass")
        assertEquals("latest-pass", cfg.snapshot().password)
        assertEquals("latest-pass", vm.state.value.settingsPass)
        assertEquals("latest-pass", ConfigStore(cfgFile).load().password)
    }

    @Test
    fun `restore persist failure leaves memory and nav unchanged`() {
        createVm()
        io.runAll()
        store.upsert(localSample())
        var fail = false
        val failing = Store(file, clock, onBeforeCommitFile = {
            if (fail) throw IOException("injected")
        })
        assertEquals(LoadState.Ready, failing.load())
        createVm(storeOverride = failing)
        io.runAll()
        vm.openAddMetric()
        val selected = vm.state.value.graphSelectedIds
        configureDav()
        fakeDav.getResult = remoteDump()
        fail = true
        vm.restore()
        io.runAll()
        assertEquals("IO injected", vm.state.value.lastError)
        assertEquals(listOf("local-only"), vm.state.value.snapshot.samples.map { it.id })
        assertNull(vm.state.value.lastRestoreAt)
        assertEquals(MetricsSub.Add, vm.state.value.metricsSub)
        assertEquals(selected, vm.state.value.graphSelectedIds)
        assertEquals(LoadState.Ready, vm.state.value.load)
    }

    @Test
    fun `validateDavConfig rejects non-http and colon username`() {
        assertNotNull(validateDavConfig("ftp://nas/x", "paul"))
        assertNotNull(validateDavConfig("nas.example/x", "paul"))
        assertNotNull(validateDavConfig("https://nas/x", ""))
        assertNotNull(validateDavConfig("https://nas/x", "   "))
        assertNotNull(validateDavConfig("https://nas/x", "user:name"))
        assertNull(validateDavConfig("https://nas.example/tracker.json", "paul"))
        assertNull(validateDavConfig("http://192.168.1.5/tracker.json", "paul"))
        assertNull(validateDavConfig("  HTTPS://Nas.example/t.json  ", "paul"))
    }

    @Test
    fun `settings persist seeds next load`() {
        createVm()
        io.runAll()
        vm.setSettingsUrl("https://nas.example/tracker.json")
        vm.setSettingsUser("paul")
        vm.setSettingsPass("app-password-xyz")
        vm.setSettingsInsecureTls(true)
        io.runAll()
        createVm()
        io.runAll()
        assertEquals("https://nas.example/tracker.json", vm.state.value.settingsUrl)
        assertEquals("paul", vm.state.value.settingsUser)
        assertEquals("app-password-xyz", vm.state.value.settingsPass)
        assertTrue(vm.state.value.settingsInsecureTls)
    }

    @Test
    fun `second backup while in flight is ignored`() {
        createVm()
        io.runAll()
        configureDav()
        vm.backup()
        vm.backup()
        io.runAll()
        assertEquals(1, fakeDav.putCount)
    }


    @Test
    fun `save new sample appears and resets form`() {
        createVm()
        io.runAll()
        vm.openEntry("weight")
        assertEquals(MetricsSub.Entry, vm.state.value.metricsSub)
        assertEquals("weight", vm.state.value.entryMetricId)
        assertNull(vm.state.value.entrySampleId)
        assertEquals(t0, vm.state.value.entryRecordedAt)
        vm.setEntryFieldText("lb", "180.5")
        vm.saveSample()
        io.runAll()
        val saved = vm.state.value.snapshot.samples.single()
        assertEquals("weight", saved.metricId)
        assertEquals(180.5, saved.values.getValue("lb"), 0.0)
        assertEquals(Sources.MANUAL, saved.source)
        assertEquals(t0, saved.recordedAt)
        assertTrue(saved.id.isNotBlank())
        assertNull(vm.state.value.entrySampleId)
        assertTrue(vm.state.value.entryFieldText.isEmpty())
        assertEquals(t0, vm.state.value.entryRecordedAt)
        assertNull(vm.state.value.entryError)
        assertEquals(MetricsSub.Entry, vm.state.value.metricsSub)
        val reloaded = Store(file, Clock { t0 })
        assertEquals(LoadState.Ready, reloaded.load())
        assertEquals(180.5, reloaded.samplesFor("weight").single().values.getValue("lb"), 0.0)
    }

    @Test
    fun `save sample rejects empty and non-finite without persist`() {
        createVm()
        io.runAll()
        vm.openEntry("weight")
        vm.saveSample()
        assertNotNull(vm.state.value.entryError)
        assertTrue(vm.state.value.snapshot.samples.isEmpty())
        vm.setEntryFieldText("lb", "NaN")
        vm.saveSample()
        assertNotNull(vm.state.value.entryError)
        assertTrue(vm.state.value.snapshot.samples.isEmpty())
        vm.setEntryFieldText("lb", "∞")
        vm.saveSample()
        assertNotNull(vm.state.value.entryError)
        assertTrue(vm.state.value.snapshot.samples.isEmpty())
        val reloaded = Store(file, Clock { t0 })
        assertEquals(LoadState.Ready, reloaded.load())
        assertTrue(reloaded.samplesFor("weight").isEmpty())
    }

    @Test
    fun `BP save requires systolic diastolic and pulse`() {
        createVm()
        io.runAll()
        vm.openEntry("blood_pressure")
        vm.setEntryFieldText("systolic", "118")
        vm.setEntryFieldText("diastolic", "76")
        vm.saveSample()
        assertNotNull(vm.state.value.entryError)
        assertTrue(vm.state.value.snapshot.samples.isEmpty())
        vm.setEntryFieldText("pulse", "72")
        vm.saveSample()
        io.runAll()
        val saved = vm.state.value.snapshot.samples.single()
        assertEquals(118.0, saved.values.getValue("systolic"), 0.0)
        assertEquals(76.0, saved.values.getValue("diastolic"), 0.0)
        assertEquals(72.0, saved.values.getValue("pulse"), 0.0)
        assertEquals(3, saved.values.size)
    }

    @Test
    fun `edit save preserves extras extra pulse and source`() {
        createVm()
        io.runAll()
        store.upsert(
            Sample(
                id = "s1",
                metricId = "weight",
                recordedAt = t0,
                modifiedAt = t0,
                source = Sources.BLUETOOTH,
                values = mapOf("lb" to 170.0, "pulse" to 72.0),
                extras = mapOf("note" to JsonPrimitive("fasted")),
            ),
        )
        createVm()
        io.runAll()
        vm.openEntry("weight")
        vm.editSample("s1")
        assertEquals("s1", vm.state.value.entrySampleId)
        assertEquals("170", vm.state.value.entryFieldText["lb"])
        assertFalse(vm.state.value.entryFieldText.containsKey("pulse"))
        vm.setEntryFieldText("lb", "180")
        vm.saveSample()
        io.runAll()
        val saved = vm.state.value.snapshot.samples.single { it.id == "s1" }
        assertEquals(180.0, saved.values.getValue("lb"), 0.0)
        assertEquals(72.0, saved.values.getValue("pulse"), 0.0)
        assertEquals(JsonPrimitive("fasted"), saved.extras.getValue("note"))
        assertEquals(Sources.BLUETOOTH, saved.source)
        assertNull(vm.state.value.entrySampleId)
        val encoded = JsonCodec.encode(store.dump())
        val sampleObj = Json.parseToJsonElement(encoded).jsonObject.getValue("samples").jsonArray.single().jsonObject
        assertEquals("fasted", sampleObj.getValue("note").jsonPrimitive.content)
        assertEquals(72.0, sampleObj.getValue("values").jsonObject.getValue("pulse").jsonPrimitive.double, 0.0)
        assertEquals(Sources.BLUETOOTH, sampleObj.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `deleteSample removes row and newSample clears form`() {
        createVm()
        io.runAll()
        vm.openEntry("weight")
        vm.setEntryFieldText("lb", "180")
        val later = t0.plusSeconds(60)
        vm.setEntryRecordedAt(later)
        vm.saveSample()
        io.runAll()
        val saved = vm.state.value.snapshot.samples.single()
        assertEquals(later, saved.recordedAt)
        val id = saved.id
        vm.editSample(id)
        assertEquals(id, vm.state.value.entrySampleId)
        vm.newSample()
        assertNull(vm.state.value.entrySampleId)
        assertTrue(vm.state.value.entryFieldText.isEmpty())
        assertEquals(t0, vm.state.value.entryRecordedAt)
        vm.deleteSample(id)
        io.runAll()
        assertTrue(vm.state.value.snapshot.samples.none { it.id == id })
    }

    @Test
    fun `tab switch preserves entry form buffers`() {
        createVm()
        io.runAll()
        vm.openEntry("weight")
        vm.setEntryFieldText("lb", "180")
        vm.selectTab(Tab.Graphs)
        vm.selectTab(Tab.Metrics)
        assertEquals(MetricsSub.Entry, vm.state.value.metricsSub)
        assertEquals("180", vm.state.value.entryFieldText["lb"])
        assertEquals("weight", vm.state.value.entryMetricId)
    }

    @Test
    fun `graph chips update selection range and average mode`() {
        createVm()
        io.runAll()
        assertEquals(RangePreset.D7, vm.state.value.rangePreset)
        assertEquals(AverageMode.Off, vm.state.value.averageMode)
        assertEquals(t0, vm.now())
        vm.toggleGraphMetric("weight")
        assertFalse("weight" in vm.state.value.graphSelectedIds)
        vm.toggleGraphMetric("weight")
        assertTrue("weight" in vm.state.value.graphSelectedIds)
        vm.setRangePreset(RangePreset.Custom)
        vm.setCustomFrom(LocalDate.of(2026, 8, 20))
        vm.setCustomTo(LocalDate.of(2026, 8, 20))
        vm.setAverageMode(AverageMode.Weekly)
        assertEquals(RangePreset.Custom, vm.state.value.rangePreset)
        assertEquals(LocalDate.of(2026, 8, 20), vm.state.value.customFrom)
        assertEquals(LocalDate.of(2026, 8, 20), vm.state.value.customTo)
        assertEquals(AverageMode.Weekly, vm.state.value.averageMode)
    }

    private fun createVm(storeOverride: Store? = null, configOverride: ConfigStore? = null) {
        store = storeOverride ?: Store(file, clock)
        config = configOverride ?: ConfigStore(File(dir, "webdav.json"))
        vm = AppViewModel(store, config, fakeDav, clock, ZoneOffset.UTC, io, Locale.US)
    }

    private fun configureDav() {
        vm.setSettingsUrl("https://nas.example/tracker.json")
        vm.setSettingsUser("paul")
        vm.setSettingsPass("app-password-xyz")
        io.runAll()
    }

    private fun localSample(id: String = "local-only") = Sample(
        id = id,
        metricId = "weight",
        recordedAt = t0,
        modifiedAt = t0,
        source = Sources.MANUAL,
        values = mapOf("lb" to 180.0),
    )

    private fun remoteDump(
        metrics: List<MetricDef> = listOf(stepsMetric),
        samples: List<Sample> = listOf(
            Sample(
                id = "remote-1",
                metricId = "steps",
                recordedAt = t0,
                modifiedAt = t0,
                source = Sources.MANUAL,
                values = mapOf("count" to 100.0),
            ),
        ),
    ): ByteArray = JsonCodec.encode(Snapshot(t0, metrics, samples)).toByteArray(Charsets.UTF_8)

    private val stepsMetric = MetricDef(
        id = "steps",
        label = "Steps",
        fields = listOf(FieldDef(id = "count", label = "Count", unit = "steps")),
    )

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeWebDavClient : WebDavClient({ error("http") }) {
        var getResult: ByteArray? = null
        var getError: Exception? = null
        var putError: Exception? = null
        var lastPut: ByteArray? = null
        var putCount = 0
        var getCount = 0

        override fun get(config: Config): ByteArray? {
            getCount++
            getError?.let { throw it }
            return getResult
        }

        override fun put(config: Config, body: ByteArray) {
            putCount++
            lastPut = body.copyOf()
            putError?.let { throw it }
        }
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
