package org.bohme.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.bohme.tracker.FieldForm
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddMetricScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typeLabelAndFieldThenSave() {
        var label by mutableStateOf("")
        var fields by mutableStateOf(listOf(FieldForm()))
        var saves = 0
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = label,
                    fields = fields,
                    graphMin = "",
                    graphMax = "",
                    canChangeFieldCount = true,
                    formError = null,
                    onLabelChange = { label = it },
                    onGraphMinChange = {},
                    onGraphMaxChange = {},
                    onFieldLabelChange = { i, v ->
                        fields = fields.mapIndexed { idx, f -> if (idx == i) f.copy(label = v) else f }
                    },
                    onFieldUnitChange = { i, v ->
                        fields = fields.mapIndexed { idx, f -> if (idx == i) f.copy(unit = v) else f }
                    },
                    onFieldColorChange = { _, _ -> },
                    onAddField = {},
                    onRemoveField = {},
                    onSave = { saves++ },
                )
            }
        }
        composeRule.onNodeWithTag("field-metric-label").performTextInput("Steps")
        composeRule.onNodeWithTag("field-field-label-0").performTextInput("Count")
        composeRule.onNodeWithTag("field-field-unit-0").performTextInput("steps")
        assertEquals("Steps", label)
        assertEquals("Count", fields[0].label)
        assertEquals("steps", fields[0].unit)
        composeRule.onNodeWithTag("btn-save-metric").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun addFieldAndRemoveDisabledAtOneField() {
        var fields by mutableStateOf(listOf(FieldForm(label = "A")))
        val removed = mutableListOf<Int>()
        var adds = 0
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = "M",
                    fields = fields,
                    graphMin = "0",
                    graphMax = "100",
                    canChangeFieldCount = true,
                    formError = null,
                    onLabelChange = {},
                    onGraphMinChange = {},
                    onGraphMaxChange = {},
                    onFieldLabelChange = { _, _ -> },
                    onFieldUnitChange = { _, _ -> },
                    onFieldColorChange = { _, _ -> },
                    onAddField = {
                        adds++
                        fields = fields + FieldForm(label = "B")
                    },
                    onRemoveField = { i ->
                        removed.add(i)
                        if (fields.size > 1) {
                            fields = fields.filterIndexed { idx, _ -> idx != i }
                        }
                    },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("btn-remove-field-0").assertIsNotEnabled()
        composeRule.onNodeWithTag("btn-add-field").performClick()
        assertEquals(1, adds)
        assertEquals(2, fields.size)
        composeRule.onNodeWithTag("btn-remove-field-0").assertIsEnabled()
        composeRule.onNodeWithTag("btn-remove-field-1").assertIsEnabled()
        composeRule.onNodeWithTag("btn-remove-field-1").performClick()
        assertEquals(listOf(1), removed)
        assertEquals(1, fields.size)
        composeRule.onNodeWithTag("btn-remove-field-0").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("btn-remove-field-1").assertCountEquals(0)
    }

    @Test
    fun formErrorDisplayed() {
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = "",
                    fields = listOf(FieldForm()),
                    graphMin = "",
                    graphMax = "",
                    canChangeFieldCount = true,
                    formError = "Label is required.",
                    onLabelChange = {},
                    onGraphMinChange = {},
                    onGraphMaxChange = {},
                    onFieldLabelChange = { _, _ -> },
                    onFieldUnitChange = { _, _ -> },
                    onFieldColorChange = { _, _ -> },
                    onAddField = {},
                    onRemoveField = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("Label is required.").assertIsDisplayed()
    }

    @Test
    fun editModeHidesAddAndRemove() {
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = "Weight",
                    fields = listOf(FieldForm(id = "lb", label = "Weight", unit = "lb")),
                    graphMin = "100",
                    graphMax = "300",
                    canChangeFieldCount = false,
                    formError = null,
                    onLabelChange = {},
                    onGraphMinChange = {},
                    onGraphMaxChange = {},
                    onFieldLabelChange = { _, _ -> },
                    onFieldUnitChange = { _, _ -> },
                    onFieldColorChange = { _, _ -> },
                    onAddField = {},
                    onRemoveField = {},
                    onSave = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("btn-add-field").assertCountEquals(0)
        composeRule.onAllNodesWithTag("btn-remove-field-0").assertCountEquals(0)
        composeRule.onNodeWithTag("field-metric-label").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-save-metric").assertIsDisplayed()
    }

    @Test
    fun typeGraphMinAndMaxUpdatesState() {
        var min by mutableStateOf("")
        var max by mutableStateOf("")
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = "BHB",
                    fields = listOf(FieldForm(label = "BHB", unit = "mmol/L")),
                    graphMin = min,
                    graphMax = max,
                    canChangeFieldCount = true,
                    formError = null,
                    onLabelChange = {},
                    onGraphMinChange = { min = it },
                    onGraphMaxChange = { max = it },
                    onFieldLabelChange = { _, _ -> },
                    onFieldUnitChange = { _, _ -> },
                    onFieldColorChange = { _, _ -> },
                    onAddField = {},
                    onRemoveField = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("field-graph-min").performTextInput("0")
        composeRule.onNodeWithTag("field-graph-max").performTextInput("5")
        assertEquals("0", min)
        assertEquals("5", max)
    }

    @Test
    fun tapColorChipSetsFieldColor() {
        var fields by mutableStateOf(listOf(FieldForm()))
        composeRule.setContent {
            TrackerTheme {
                AddMetricScreen(
                    label = "Weight",
                    fields = fields,
                    graphMin = "100",
                    graphMax = "300",
                    canChangeFieldCount = true,
                    formError = null,
                    onLabelChange = {},
                    onGraphMinChange = {},
                    onGraphMaxChange = {},
                    onFieldLabelChange = { _, _ -> },
                    onFieldUnitChange = { _, _ -> },
                    onFieldColorChange = { i, c ->
                        fields = fields.mapIndexed { idx, f ->
                            if (idx == i) f.copy(color = c) else f
                        }
                    },
                    onAddField = {},
                    onRemoveField = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("chip-field-color-0-3").performScrollTo().performClick()
        assertEquals(SERIES_COLORS[3], fields[0].color)
    }
}
