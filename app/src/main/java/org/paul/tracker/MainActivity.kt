package org.paul.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.paul.tracker.data.LoadState
import org.paul.tracker.ui.AddMetricScreen
import org.paul.tracker.ui.EntryScreen
import org.paul.tracker.ui.GraphScreen
import org.paul.tracker.ui.MetricListScreen
import org.paul.tracker.ui.SettingsScreen
import org.paul.tracker.ui.TrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = AppViewModelFactory(application as TrackerApp)
        val vm = ViewModelProvider(this, factory)[AppViewModel::class.java]
        setContent {
            TrackerTheme {
                TrackerScaffold(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerScaffold(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tracker") },
                navigationIcon = {
                    if (state.tab == Tab.Metrics && state.metricsSub != MetricsSub.List) {
                        TextButton(onClick = { vm.backToMetricList() }) { Text("Back") }
                    }
                },
                actions = {
                    if (
                        state.tab == Tab.Metrics &&
                        state.metricsSub == MetricsSub.List &&
                        state.load is LoadState.Ready
                    ) {
                        TextButton(onClick = { vm.openAddMetric() }) { Text("Add metric") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        icon = { Text(tab.name.take(1)) },
                        label = { Text(tab.name) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state.load) {
                LoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    if (state.load is LoadState.Corrupt) {
                        CorruptBanner(onReset = { confirmReset = true })
                    }
                    val storeError = state.storeError
                    if (storeError != null) {
                        Text(
                            storeError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (state.tab) {
                            Tab.Metrics -> MetricsTab(vm, state)
                            Tab.Graphs -> GraphScreen(
                                metrics = state.snapshot.metrics,
                                samples = state.snapshot.samples,
                                selectedIds = state.graphSelectedIds,
                                rangePreset = state.rangePreset,
                                customFrom = state.customFrom,
                                customTo = state.customTo,
                                averageMode = state.averageMode,
                                now = vm.now(),
                                zone = vm.zone,
                                onToggleMetric = vm::toggleGraphMetric,
                                onRangePreset = vm::setRangePreset,
                                onCustomFrom = vm::setCustomFrom,
                                onCustomTo = vm::setCustomTo,
                                onAverageMode = vm::setAverageMode,
                            )
                            Tab.Settings -> SettingsTab(vm, state)
                        }
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = {
                Text(
                    "Discard unreadable local files (kept as store.json.corrupt) and start empty with built-in metrics?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.resetLocalData()
                        confirmReset = false
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MetricsTab(vm: AppViewModel, state: AppUiState) {
    when (state.metricsSub) {
        MetricsSub.List -> MetricListScreen(
            metrics = state.snapshot.metrics,
            samples = state.snapshot.samples,
            zone = vm.zone,
            onOpenEntry = { vm.openEntry(it) },
            onEdit = { vm.openEditMetric(it) },
            onDelete = { vm.deleteMetric(it) },
        )
        MetricsSub.Add, MetricsSub.Edit -> AddMetricScreen(
            label = state.addLabel,
            fields = state.addFields,
            canChangeFieldCount = state.metricsSub == MetricsSub.Add,
            formError = state.formError,
            onLabelChange = { vm.setAddLabel(it) },
            onFieldLabelChange = { index, value -> vm.setFieldLabel(index, value) },
            onFieldUnitChange = { index, value -> vm.setFieldUnit(index, value) },
            onAddField = { vm.addField() },
            onRemoveField = { vm.removeField(it) },
            onSave = { vm.saveMetric() },
        )
        MetricsSub.Entry -> {
            val metric = state.snapshot.metrics.find { it.id == state.entryMetricId }
            if (metric != null) {
                EntryScreen(
                    metric = metric,
                    samples = state.snapshot.samples,
                    fieldText = state.entryFieldText,
                    recordedAt = state.entryRecordedAt,
                    zone = vm.zone,
                    error = state.entryError,
                    onFieldChange = { fieldId, value -> vm.setEntryFieldText(fieldId, value) },
                    onRecordedAtChange = { vm.setEntryRecordedAt(it) },
                    onSave = { vm.saveSample() },
                    onNewSample = { vm.newSample() },
                    onEdit = { vm.editSample(it) },
                    onDelete = { vm.deleteSample(it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(vm: AppViewModel, state: AppUiState) {
    val davValid = validateDavConfig(state.settingsUrl, state.settingsUser) == null
    SettingsScreen(
        url = state.settingsUrl,
        username = state.settingsUser,
        password = state.settingsPass,
        insecureTls = state.settingsInsecureTls,
        lastBackupAt = state.lastBackupAt,
        lastRestoreAt = state.lastRestoreAt,
        lastError = state.lastError,
        backupEnabled = backupEnabled(state.load, state.davInFlight) && davValid,
        restoreEnabled = !state.davInFlight && davValid,
        davInFlight = state.davInFlight,
        restoreNeedsExtraConfirm = state.restoreNeedsExtraConfirm,
        showReset = state.load is LoadState.Corrupt,
        onUrlChange = { vm.setSettingsUrl(it) },
        onUserChange = { vm.setSettingsUser(it) },
        onPassChange = { vm.setSettingsPass(it) },
        onInsecureChange = { vm.setSettingsInsecureTls(it) },
        onBackup = { vm.backup() },
        onRestore = { vm.restore() },
        onConfirmEmptyRestore = { vm.confirmEmptyRestore() },
        onCancelEmptyRestore = { vm.cancelEmptyRestore() },
        onReset = { vm.resetLocalData() },
    )
}

@Composable
private fun CorruptBanner(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Local data file is unreadable. Restore from WebDAV or Reset local data.")
        TextButton(onClick = onReset) { Text("Reset local data") }
    }
}
