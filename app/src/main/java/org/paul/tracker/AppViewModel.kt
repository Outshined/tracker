package org.paul.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
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
import org.paul.tracker.data.Clock
import org.paul.tracker.data.FieldDef
import org.paul.tracker.data.LoadState
import org.paul.tracker.data.MetricDef
import org.paul.tracker.data.Snapshot
import org.paul.tracker.data.Store
import org.paul.tracker.data.mergeEditedMetric
import org.paul.tracker.data.mergeEditedSample
import org.paul.tracker.stats.AverageMode
import org.paul.tracker.stats.RangePreset
import org.paul.tracker.stats.defaultSelectedMetricIds
import org.paul.tracker.stats.formatLocaleNumber
import org.paul.tracker.stats.parseRequiredFields
import org.paul.tracker.stats.selectionAfterDelete

enum class Tab { Metrics, Graphs, Settings }

enum class MetricsSub { List, Add, Edit, Entry }

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
    val addLabel: String = "",
    val addFields: List<FieldForm> = listOf(FieldForm()),
    val graphSelectedIds: Set<String> = emptySet(),
    val rangePreset: RangePreset = RangePreset.D7,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val averageMode: AverageMode = AverageMode.Off,
)

fun validateMetricForm(label: String, fields: List<FieldForm>): String? {
    if (label.isBlank()) return "Label is required."
    if (fields.isEmpty()) return "At least one field is required."
    if (fields.any { it.label.isBlank() }) return "Each field needs a label."
    return null
}

class AppViewModel(
    private val store: Store,
    private val clock: Clock,
    val zone: ZoneId,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(io) {
            val load = store.load()
            val snap = store.snapshot()
            _state.update { prev ->
                val selected =
                    if (load is LoadState.Ready && prev.graphSelectedIds.isEmpty()) {
                        defaultSelectedMetricIds(snap.metrics, snap.samples).toSet()
                    } else {
                        prev.graphSelectedIds
                    }
                prev.copy(load = load, snapshot = snap, graphSelectedIds = selected)
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

    private fun applyCatalogReset(snap: Snapshot) {
        _state.update {
            it.copy(
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
                addLabel = "",
                addFields = listOf(FieldForm()),
                graphSelectedIds = defaultSelectedMetricIds(snap.metrics, snap.samples).toSet(),
            )
        }
    }
}

class AppViewModelFactory(
    private val app: TrackerApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val clock = app.clock
        val store = Store(File(app.filesDir, "store.json"), clock)
        return AppViewModel(
            store = store,
            clock = clock,
            zone = ZoneId.systemDefault(),
        ) as T
    }
}
