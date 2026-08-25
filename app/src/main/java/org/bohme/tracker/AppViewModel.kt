package org.bohme.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bohme.tracker.data.Clock
import org.bohme.tracker.data.ConfigStore
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.JsonCodec
import org.bohme.tracker.data.LoadState
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Snapshot
import org.bohme.tracker.data.Store
import org.bohme.tracker.data.mergeEditedMetric
import org.bohme.tracker.data.mergeEditedSample
import org.bohme.tracker.stats.AverageMode
import org.bohme.tracker.stats.RangePreset
import org.bohme.tracker.stats.defaultSelectedMetricIds
import org.bohme.tracker.stats.formatLocaleNumber
import org.bohme.tracker.stats.localDateOf
import org.bohme.tracker.stats.parseRequiredFields
import org.bohme.tracker.stats.sampleOnLocalDate
import org.bohme.tracker.stats.selectionAfterDelete
import org.bohme.tracker.stats.todayFieldKey
import org.bohme.tracker.webdav.WebDavClient

enum class Tab { Metrics, Graphs, Settings }

enum class MetricsSub { List, Add, Edit, Entry, Today }

data class FieldForm(
    val id: String = "",
    val label: String = "",
    val unit: String = "",
)

data class AppUiState(
    val load: LoadState = LoadState.Loading,
    val snapshot: Snapshot = Snapshot(Instant.EPOCH, emptyList(), emptyList()),
    val storeError: String? = null,
    val formError: String? = null,
    val tab: Tab = Tab.Metrics,
    val metricsSub: MetricsSub = MetricsSub.List,
    val entryMetricId: String? = null,
    val entrySampleId: String? = null,
    val entryFieldText: Map<String, String> = emptyMap(),
    val entryRecordedAt: Instant = Instant.EPOCH,
    val entryError: String? = null,
    val todayFieldText: Map<String, String> = emptyMap(),
    val todayExistingIds: Map<String, String> = emptyMap(),
    val todayError: String? = null,
    val addLabel: String = "",
    val addFields: List<FieldForm> = listOf(FieldForm()),
    val graphSelectedIds: Set<String> = emptySet(),
    val rangePreset: RangePreset = RangePreset.D7,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val averageMode: AverageMode = AverageMode.Off,
    val settingsUrl: String = "",
    val settingsUser: String = "",
    val settingsPass: String = "",
    val settingsInsecureTls: Boolean = false,
    val lastBackupAt: Instant? = null,
    val lastRestoreAt: Instant? = null,
    val lastError: String = "",
    val davInFlight: Boolean = false,
    val restoreNeedsExtraConfirm: Boolean = false,
)

fun validateMetricForm(label: String, fields: List<FieldForm>): String? {
    if (label.isBlank()) return "Label is required."
    if (fields.isEmpty()) return "At least one field is required."
    if (fields.any { it.label.isBlank() }) return "Each field needs a label."
    return null
}

fun validateDavConfig(url: String, username: String): String? {
    val trimmedUrl = url.trim()
    val http = trimmedUrl.startsWith("http://", ignoreCase = true)
    val https = trimmedUrl.startsWith("https://", ignoreCase = true)
    if (!http && !https) return "URL must be http or https"
    val user = username.trim()
    if (user.isEmpty()) return "Username is required."
    if (user.contains(':')) return "Username must not contain ':'"
    return null
}

fun backupEnabled(load: LoadState, davInFlight: Boolean = false): Boolean =
    load is LoadState.Ready && !davInFlight

fun shouldExtraConfirm(localCount: Int, remoteCount: Int): Boolean =
    localCount > 0 && remoteCount == 0

class AppViewModel(
    private val store: Store,
    private val config: ConfigStore,
    private val webDav: WebDavClient,
    private val clock: Clock,
    val zone: ZoneId,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val davLock = Any()
    private var pendingRestore: Snapshot? = null

    init {
        viewModelScope.launch(io) {
            val cfg = config.load()
            val load = store.load()
            val snap = store.snapshot()
            _state.update { prev ->
                val selected =
                    if (load is LoadState.Ready && prev.graphSelectedIds.isEmpty()) {
                        defaultSelectedMetricIds(snap.metrics, snap.samples).toSet()
                    } else {
                        prev.graphSelectedIds
                    }
                prev.copy(
                    load = load,
                    snapshot = snap,
                    graphSelectedIds = selected,
                    settingsUrl = cfg.url,
                    settingsUser = cfg.username,
                    settingsPass = cfg.password,
                    settingsInsecureTls = cfg.insecureTls,
                    lastBackupAt = cfg.lastBackupAt,
                    lastRestoreAt = cfg.lastRestoreAt,
                    lastError = cfg.lastError,
                )
            }
        }
    }

    fun now(): Instant = clock.now()

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
    }

    fun toggleGraphMetric(id: String) {
        _state.update { s ->
            val next =
                if (id in s.graphSelectedIds) s.graphSelectedIds - id else s.graphSelectedIds + id
            s.copy(graphSelectedIds = next)
        }
    }

    fun setRangePreset(preset: RangePreset) {
        _state.update { it.copy(rangePreset = preset) }
    }

    fun setCustomFrom(date: LocalDate) {
        _state.update { it.copy(customFrom = date) }
    }

    fun setCustomTo(date: LocalDate) {
        _state.update { it.copy(customTo = date) }
    }

    fun setAverageMode(mode: AverageMode) {
        _state.update { it.copy(averageMode = mode) }
    }

    fun openAddMetric() {
        _state.update {
            it.copy(
                metricsSub = MetricsSub.Add,
                entryMetricId = null,
                addLabel = "",
                addFields = listOf(FieldForm()),
                formError = null,
            )
        }
    }

    fun openEditMetric(id: String) {
        val metric = _state.value.snapshot.metrics.find { it.id == id } ?: return
        _state.update {
            it.copy(
                metricsSub = MetricsSub.Edit,
                entryMetricId = id,
                addLabel = metric.label,
                addFields = metric.fields.map { field ->
                    FieldForm(id = field.id, label = field.label, unit = field.unit)
                },
                formError = null,
            )
        }
    }

    fun openEntry(id: String) {
        if (_state.value.snapshot.metrics.none { it.id == id }) return
        _state.update {
            it.copy(
                metricsSub = MetricsSub.Entry,
                entryMetricId = id,
                entrySampleId = null,
                entryFieldText = emptyMap(),
                entryRecordedAt = clock.now(),
                entryError = null,
            )
        }
    }

    fun openToday() {
        val (texts, ids) = todayBuffers(_state.value.snapshot)
        _state.update {
            it.copy(
                metricsSub = MetricsSub.Today,
                todayFieldText = texts,
                todayExistingIds = ids,
                todayError = null,
            )
        }
    }

    fun setTodayField(metricId: String, fieldId: String, value: String) {
        _state.update {
            it.copy(
                todayFieldText = it.todayFieldText + (todayFieldKey(metricId, fieldId) to value),
                todayError = null,
            )
        }
    }

    fun saveToday() {
        val current = _state.value
        val prepared = mutableListOf<Sample>()
        for (metric in current.snapshot.metrics) {
            val texts = metric.fields.associate { field ->
                field.id to current.todayFieldText[todayFieldKey(metric.id, field.id)].orEmpty()
            }
            if (texts.values.all { it.trim().isEmpty() }) continue
            val parsed = parseRequiredFields(metric.fields, texts, locale)
            if (parsed == null) {
                _state.update {
                    it.copy(todayError = "Every field is required for ${metric.label}.")
                }
                return
            }
            val existing = current.todayExistingIds[metric.id]?.let { id ->
                current.snapshot.samples.find { it.id == id }
            }
            prepared += mergeEditedSample(
                existing = existing,
                metric = metric,
                parsedValues = parsed,
                recordedAt = existing?.recordedAt ?: clock.now(),
                idForNew = UUID.randomUUID().toString(),
            )
        }
        viewModelScope.launch(io) {
            try {
                for (sample in prepared) {
                    store.upsert(sample)
                }
                val snap = store.snapshot()
                val (texts, ids) = todayBuffers(snap)
                _state.update {
                    it.copy(
                        snapshot = snap,
                        storeError = null,
                        todayError = null,
                        todayFieldText = texts,
                        todayExistingIds = ids,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setEntryFieldText(fieldId: String, value: String) {
        _state.update {
            it.copy(
                entryFieldText = it.entryFieldText + (fieldId to value),
                entryError = null,
            )
        }
    }

    fun setEntryRecordedAt(value: Instant) {
        _state.update { it.copy(entryRecordedAt = value) }
    }

    fun editSample(id: String) {
        val current = _state.value
        val metricId = current.entryMetricId ?: return
        val sample = current.snapshot.samples.find { it.id == id } ?: return
        if (sample.metricId != metricId) return
        val metric = current.snapshot.metrics.find { it.id == metricId } ?: return
        _state.update {
            it.copy(
                entrySampleId = sample.id,
                entryRecordedAt = sample.recordedAt,
                entryFieldText = metric.fields.associate { field ->
                    field.id to (sample.values[field.id]?.let { v -> formatLocaleNumber(v, locale) } ?: "")
                },
                entryError = null,
            )
        }
    }

    fun newSample() {
        _state.update {
            it.copy(
                entrySampleId = null,
                entryFieldText = emptyMap(),
                entryRecordedAt = clock.now(),
                entryError = null,
            )
        }
    }

    fun saveSample() {
        val current = _state.value
        val metricId = current.entryMetricId ?: return
        val metric = current.snapshot.metrics.find { it.id == metricId } ?: return
        val parsed = parseRequiredFields(metric.fields, current.entryFieldText, locale)
        if (parsed == null) {
            _state.update { it.copy(entryError = "Every field is required.") }
            return
        }
        val existing = current.entrySampleId?.let { id -> current.snapshot.samples.find { it.id == id } }
        val sample = mergeEditedSample(
            existing = existing,
            metric = metric,
            parsedValues = parsed,
            recordedAt = current.entryRecordedAt,
            idForNew = UUID.randomUUID().toString(),
        )
        viewModelScope.launch(io) {
            try {
                store.upsert(sample)
                val snap = store.snapshot()
                _state.update {
                    it.copy(
                        snapshot = snap,
                        storeError = null,
                        entryError = null,
                        entrySampleId = null,
                        entryFieldText = emptyMap(),
                        entryRecordedAt = clock.now(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun deleteSample(id: String) {
        viewModelScope.launch(io) {
            try {
                store.deleteSample(id)
                val snap = store.snapshot()
                _state.update { prev ->
                    val editingDeleted = prev.entrySampleId == id
                    prev.copy(
                        snapshot = snap,
                        storeError = null,
                        entrySampleId = if (editingDeleted) null else prev.entrySampleId,
                        entryFieldText = if (editingDeleted) emptyMap() else prev.entryFieldText,
                        entryRecordedAt = if (editingDeleted) clock.now() else prev.entryRecordedAt,
                        entryError = if (editingDeleted) null else prev.entryError,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun backToMetricList() {
        _state.update { it.copy(metricsSub = MetricsSub.List) }
    }

    fun setAddLabel(value: String) {
        _state.update { it.copy(addLabel = value, formError = null) }
    }

    fun setFieldLabel(index: Int, value: String) {
        _state.update { s ->
            s.copy(
                addFields = s.addFields.mapIndexed { i, field ->
                    if (i == index) field.copy(label = value) else field
                },
                formError = null,
            )
        }
    }

    fun setFieldUnit(index: Int, value: String) {
        _state.update { s ->
            s.copy(
                addFields = s.addFields.mapIndexed { i, field ->
                    if (i == index) field.copy(unit = value) else field
                },
                formError = null,
            )
        }
    }

    fun addField() {
        _state.update { s ->
            if (s.metricsSub != MetricsSub.Add) s
            else s.copy(addFields = s.addFields + FieldForm())
        }
    }

    fun removeField(index: Int) {
        _state.update { s ->
            if (s.metricsSub != MetricsSub.Add || s.addFields.size <= 1) s
            else s.copy(addFields = s.addFields.filterIndexed { i, _ -> i != index })
        }
    }

    fun saveMetric() {
        val current = _state.value
        val error = validateMetricForm(current.addLabel, current.addFields)
        if (error != null) {
            _state.update { it.copy(formError = error) }
            return
        }
        val sub = current.metricsSub
        val label = current.addLabel.trim()
        val drafts = current.addFields
        val editId = current.entryMetricId
        viewModelScope.launch(io) {
            try {
                when (sub) {
                    MetricsSub.Add -> {
                        store.upsertMetric(
                            MetricDef(
                                id = UUID.randomUUID().toString(),
                                label = label,
                                fields = drafts.map { draft ->
                                    FieldDef(
                                        id = UUID.randomUUID().toString(),
                                        label = draft.label.trim(),
                                        unit = draft.unit.trim(),
                                    )
                                },
                            ),
                        )
                    }
                    MetricsSub.Edit -> {
                        val existing = current.snapshot.metrics.find { it.id == editId }
                            ?: return@launch
                        store.upsertMetric(
                            mergeEditedMetric(
                                existing,
                                label,
                                drafts.map { it.label.trim() to it.unit.trim() },
                            ),
                        )
                    }
                    else -> return@launch
                }
                val snap = store.snapshot()
                _state.update {
                    it.copy(
                        snapshot = snap,
                        storeError = null,
                        formError = null,
                        metricsSub = MetricsSub.List,
                        entryMetricId = null,
                        addLabel = "",
                        addFields = listOf(FieldForm()),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun deleteMetric(id: String) {
        viewModelScope.launch(io) {
            try {
                store.deleteMetric(id)
                val snap = store.snapshot()
                _state.update { prev ->
                    val editingGone = prev.entryMetricId == id
                    prev.copy(
                        snapshot = snap,
                        storeError = null,
                        graphSelectedIds = selectionAfterDelete(
                            prev.graphSelectedIds,
                            id,
                            snap.metrics,
                            snap.samples,
                        ),
                        metricsSub = if (editingGone) MetricsSub.List else prev.metricsSub,
                        entryMetricId = if (editingGone) null else prev.entryMetricId,
                        entrySampleId = if (editingGone) null else prev.entrySampleId,
                        entryFieldText = if (editingGone) emptyMap() else prev.entryFieldText,
                        entryRecordedAt = if (editingGone) clock.now() else prev.entryRecordedAt,
                        entryError = if (editingGone) null else prev.entryError,
                        addLabel = if (editingGone) "" else prev.addLabel,
                        addFields = if (editingGone) listOf(FieldForm()) else prev.addFields,
                        formError = if (editingGone) null else prev.formError,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun resetLocalData() {
        viewModelScope.launch(io) {
            try {
                store.resetLocalData()
                applyCatalogReset(store.snapshot())
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setSettingsUrl(value: String) {
        _state.update { it.copy(settingsUrl = value) }
        persistSettings()
    }

    fun setSettingsUser(value: String) {
        _state.update { it.copy(settingsUser = value) }
        persistSettings()
    }

    fun setSettingsPass(value: String) {
        _state.update { it.copy(settingsPass = value) }
        persistSettings()
    }

    fun setSettingsInsecureTls(value: Boolean) {
        _state.update { it.copy(settingsInsecureTls = value) }
        persistSettings()
    }

    fun backup() {
        val current = _state.value
        if (current.davInFlight) return
        if (!backupEnabled(current.load, current.davInFlight)) return
        if (validateDavConfig(current.settingsUrl, current.settingsUser) != null) return
        _state.update { it.copy(davInFlight = true) }
        viewModelScope.launch(io) {
            try {
                val bytes = JsonCodec.encode(store.dump()).toByteArray(Charsets.UTF_8)
                webDav.put(davConfig(), bytes)
            } catch (e: Exception) {
                failDav(formatDavError(e))
                return@launch
            }
            val now = clock.now()
            _state.update {
                it.copy(lastBackupAt = now, lastError = "", davInFlight = false)
            }
            try {
                persistConfig { it.copy(lastBackupAt = now, lastError = "") }
            } catch (e: Exception) {
                _state.update { it.copy(lastError = formatDavError(e)) }
            }
        }
    }

    fun restore() {
        val current = _state.value
        if (current.davInFlight) return
        if (validateDavConfig(current.settingsUrl, current.settingsUser) != null) return
        _state.update { it.copy(davInFlight = true, restoreNeedsExtraConfirm = false) }
        viewModelScope.launch(io) {
            runRestore()
        }
    }

    fun confirmEmptyRestore() {
        val snapshot = synchronized(davLock) {
            if (!_state.value.restoreNeedsExtraConfirm) return
            val pending = pendingRestore ?: return
            pendingRestore = null
            _state.update { it.copy(restoreNeedsExtraConfirm = false) }
            pending
        }
        viewModelScope.launch(io) {
            commitRestore(snapshot)
        }
    }

    fun cancelEmptyRestore() {
        synchronized(davLock) {
            if (!_state.value.restoreNeedsExtraConfirm) return
            pendingRestore = null
            _state.update { it.copy(davInFlight = false, restoreNeedsExtraConfirm = false) }
        }
    }

    private fun runRestore() {
        try {
            val bytes = webDav.get(davConfig())
            if (bytes == null) {
                failDav("No backup at that URL")
                return
            }
            val decoded = try {
                JsonCodec.decode(String(bytes, Charsets.UTF_8))
            } catch (_: Exception) {
                failDav("Server file is not a valid dump")
                return
            }
            val localCount = store.snapshot().samples.size
            if (shouldExtraConfirm(localCount, decoded.samples.size)) {
                synchronized(davLock) {
                    pendingRestore = decoded
                    _state.update { it.copy(restoreNeedsExtraConfirm = true) }
                }
                return
            }
            commitRestore(decoded)
        } catch (e: Exception) {
            failDav(formatDavError(e))
        }
    }

    private fun commitRestore(snapshot: Snapshot) {
        try {
            if (_state.value.load is LoadState.Corrupt) {
                store.copyUnreadableToCorrupt()
            }
            store.replaceAll(snapshot)
        } catch (e: Exception) {
            failDav(formatDavError(e))
            return
        }
        val now = clock.now()
        synchronized(davLock) {
            pendingRestore = null
        }
        _state.update {
            it.withCatalogReset(snapshot).copy(
                lastRestoreAt = now,
                lastError = "",
                davInFlight = false,
                restoreNeedsExtraConfirm = false,
            )
        }
        try {
            persistConfig { it.copy(lastRestoreAt = now, lastError = "") }
        } catch (e: Exception) {
            _state.update { it.copy(lastError = formatDavError(e)) }
        }
    }

    private fun persistSettings() {
        viewModelScope.launch(io) {
            try {
                persistConfig { it }
            } catch (e: Exception) {
                _state.update { it.copy(lastError = formatDavError(e)) }
            }
        }
    }

    private fun persistConfig(transform: (ConfigStore.State) -> ConfigStore.State): ConfigStore.State {
        return config.update { prev ->
            val s = _state.value
            transform(
                prev.copy(
                    url = s.settingsUrl,
                    username = s.settingsUser,
                    password = s.settingsPass,
                    insecureTls = s.settingsInsecureTls,
                ),
            )
        }
    }

    private fun failDav(message: String) {
        synchronized(davLock) {
            pendingRestore = null
        }
        try {
            persistConfig { it.copy(lastError = message) }
        } catch (_: Exception) {
            // lastError still published on the VM below
        }
        _state.update {
            it.copy(lastError = message, davInFlight = false, restoreNeedsExtraConfirm = false)
        }
    }

    private fun davConfig(): WebDavClient.Config {
        val s = _state.value
        return WebDavClient.Config(
            url = s.settingsUrl.trim(),
            username = s.settingsUser.trim(),
            password = s.settingsPass,
            insecureTls = s.settingsInsecureTls,
        )
    }

    private fun formatDavError(e: Throwable): String = when (e) {
        is WebDavClient.HttpException -> "HTTP ${e.code}"
        is InterruptedIOException -> "timeout"
        is IOException -> "IO ${e.message ?: e.javaClass.simpleName}"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun applyCatalogReset(snap: Snapshot) {
        synchronized(davLock) {
            pendingRestore = null
        }
        _state.update { it.withCatalogReset(snap) }
    }

    private fun todayBuffers(snap: Snapshot): Pair<Map<String, String>, Map<String, String>> {
        val date = localDateOf(clock.now(), zone)
        val texts = linkedMapOf<String, String>()
        val ids = linkedMapOf<String, String>()
        for (metric in snap.metrics) {
            val sample = sampleOnLocalDate(snap.samples, metric.id, date, zone) ?: continue
            ids[metric.id] = sample.id
            for (field in metric.fields) {
                val raw = sample.values[field.id]
                texts[todayFieldKey(metric.id, field.id)] =
                    if (raw != null) formatLocaleNumber(raw, locale) else ""
            }
        }
        return texts to ids
    }

    private fun AppUiState.withCatalogReset(snap: Snapshot): AppUiState = copy(
        load = LoadState.Ready,
        snapshot = snap,
        storeError = null,
        formError = null,
        metricsSub = MetricsSub.List,
        entryMetricId = null,
        entrySampleId = null,
        entryFieldText = emptyMap(),
        entryRecordedAt = clock.now(),
        entryError = null,
        todayFieldText = emptyMap(),
        todayExistingIds = emptyMap(),
        todayError = null,
        addLabel = "",
        addFields = listOf(FieldForm()),
        graphSelectedIds = defaultSelectedMetricIds(snap.metrics, snap.samples).toSet(),
    )
}

class AppViewModelFactory(
    private val app: TrackerApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val clock = app.clock
        val store = Store(File(app.filesDir, "store.json"), clock)
        val config = ConfigStore(File(app.filesDir, "webdav.json"))
        return AppViewModel(
            store = store,
            config = config,
            webDav = WebDavClient(),
            clock = clock,
            zone = ZoneId.systemDefault(),
        ) as T
    }
}
