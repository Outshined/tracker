package org.bohme.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.MetricDef
import org.bohme.tracker.stats.todayFieldKey

@Composable
fun TodayScreen(
    metrics: List<MetricDef>,
    fieldText: Map<String, String>,
    error: String?,
    onFieldChange: (metricId: String, fieldId: String, value: String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        metrics.forEach { metric ->
            Text(metric.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val width = if (metric.fields.size <= 1) 96.dp else 80.dp
            metric.fields.chunked(3).forEach { rowFields ->
                TodayFieldRow(
                    metricId = metric.id,
                    fields = rowFields,
                    fieldText = fieldText,
                    fieldWidth = width,
                    onFieldChange = onFieldChange,
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().testTag("btn-save-today"),
        ) { Text("Save") }
    }
}

@Composable
private fun TodayFieldRow(
    metricId: String,
    fields: List<FieldDef>,
    fieldText: Map<String, String>,
    fieldWidth: Dp,
    onFieldChange: (String, String, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { field ->
            OutlinedTextField(
                value = fieldText[todayFieldKey(metricId, field.id)].orEmpty(),
                onValueChange = { onFieldChange(metricId, field.id, it) },
                label = { Text(field.label) },
                suffix = if (field.unit.isEmpty()) {
                    null
                } else {
                    { Text(field.unit) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(fieldWidth)
                    .testTag("today-field-$metricId-${field.id}"),
            )
        }
    }
}
