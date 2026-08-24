package org.paul.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.paul.tracker.FieldForm

@Composable
fun AddMetricScreen(
    label: String,
    fields: List<FieldForm>,
    canChangeFieldCount: Boolean,
    formError: String?,
    onLabelChange: (String) -> Unit,
    onFieldLabelChange: (Int, String) -> Unit,
    onFieldUnitChange: (Int, String) -> Unit,
    onAddField: () -> Unit,
    onRemoveField: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("All fields share one graph axis; use the same unit.")
        Spacer(Modifier.height(16.dp))
        fields.forEachIndexed { index, field ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = { onFieldLabelChange(index, it) },
                    label = { Text("Field label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                OutlinedTextField(
                    value = field.unit,
                    onValueChange = { onFieldUnitChange(index, it) },
                    label = { Text("Unit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (canChangeFieldCount) {
                TextButton(
                    onClick = { onRemoveField(index) },
                    enabled = fields.size > 1,
                ) { Text("Remove field") }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (canChangeFieldCount) {
            TextButton(onClick = onAddField) { Text("Add field") }
        }
        if (formError != null) {
            Text(formError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}
