package org.bohme.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.bohme.tracker.FieldForm

@Composable
fun AddMetricScreen(
    label: String,
    fields: List<FieldForm>,
    graphMin: String,
    graphMax: String,
    canChangeFieldCount: Boolean,
    formError: String?,
    onLabelChange: (String) -> Unit,
    onGraphMinChange: (String) -> Unit,
    onGraphMaxChange: (String) -> Unit,
    onFieldLabelChange: (Int, String) -> Unit,
    onFieldUnitChange: (Int, String) -> Unit,
    onFieldColorChange: (Int, Int) -> Unit,
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
            modifier = Modifier.fillMaxWidth().testTag("field-metric-label"),
        )
        Spacer(Modifier.height(16.dp))
        Text("All fields share one graph axis; use the same unit.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = graphMin,
            onValueChange = onGraphMinChange,
            label = { Text("Graph min") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-graph-min"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = graphMax,
            onValueChange = onGraphMaxChange,
            label = { Text("Graph max") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-graph-max"),
        )
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag("field-field-label-$index"),
                )
                OutlinedTextField(
                    value = field.unit,
                    onValueChange = { onFieldUnitChange(index, it) },
                    label = { Text("Unit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("field-field-unit-$index"),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Color")
                SERIES_COLORS.forEachIndexed { paletteIndex, swatch ->
                    val selected = field.color == swatch
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .clickable { onFieldColorChange(index, swatch) }
                            .testTag("chip-field-color-$index-$paletteIndex"),
                    )
                }
            }
            if (canChangeFieldCount) {
                TextButton(
                    onClick = { onRemoveField(index) },
                    enabled = fields.size > 1,
                    modifier = Modifier.testTag("btn-remove-field-$index"),
                ) { Text("Remove field") }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (canChangeFieldCount) {
            TextButton(onClick = onAddField, modifier = Modifier.testTag("btn-add-field")) {
                Text("Add field")
            }
        }
        if (formError != null) {
            Text(formError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().testTag("btn-save-metric"),
        ) { Text("Save") }
    }
}
