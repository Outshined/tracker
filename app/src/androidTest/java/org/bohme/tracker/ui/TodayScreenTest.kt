package org.bohme.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.FieldDef
import org.bohme.tracker.data.MetricDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bp = BuiltInMetrics.ALL.first { it.id == "blood_pressure" }
    private val quad = MetricDef(
        id = "quad",
        label = "Quad",
        fields = listOf(
            FieldDef(id = "a", label = "A", unit = ""),
            FieldDef(id = "b", label = "B", unit = ""),
            FieldDef(id = "c", label = "C", unit = ""),
            FieldDef(id = "d", label = "D", unit = ""),
        ),
    )

    @Test
    fun threeFieldRowIsOneRow() {
        composeRule.setContent {
            TrackerTheme {
                TodayScreen(
                    metrics = listOf(bp),
                    fieldText = emptyMap(),
                    error = null,
                    onFieldChange = { _, _, _ -> },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("today-field-blood_pressure-systolic").assertIsDisplayed()
        composeRule.onNodeWithTag("today-field-blood_pressure-diastolic").assertIsDisplayed()
        composeRule.onNodeWithTag("today-field-blood_pressure-pulse").assertIsDisplayed()
        val sys = composeRule.onNodeWithTag("today-field-blood_pressure-systolic").fetchSemanticsNode()
        val dia = composeRule.onNodeWithTag("today-field-blood_pressure-diastolic").fetchSemanticsNode()
        val pulse = composeRule.onNodeWithTag("today-field-blood_pressure-pulse").fetchSemanticsNode()
        assertEquals(sys.positionInRoot.y, dia.positionInRoot.y, 1f)
        assertEquals(sys.positionInRoot.y, pulse.positionInRoot.y, 1f)
    }

    @Test
    fun fourFieldsWrapToSecondRow() {
        composeRule.setContent {
            TrackerTheme {
                TodayScreen(
                    metrics = listOf(quad),
                    fieldText = emptyMap(),
                    error = null,
                    onFieldChange = { _, _, _ -> },
                    onSave = {},
                )
            }
        }
        val a = composeRule.onNodeWithTag("today-field-quad-a").fetchSemanticsNode()
        val b = composeRule.onNodeWithTag("today-field-quad-b").fetchSemanticsNode()
        val c = composeRule.onNodeWithTag("today-field-quad-c").fetchSemanticsNode()
        val d = composeRule.onNodeWithTag("today-field-quad-d").fetchSemanticsNode()
        assertEquals(a.positionInRoot.y, b.positionInRoot.y, 1f)
        assertEquals(a.positionInRoot.y, c.positionInRoot.y, 1f)
        assertTrue(d.positionInRoot.y > a.positionInRoot.y)
    }

    @Test
    fun typeFieldsAndSaveCallback() {
        var fieldText by mutableStateOf(mapOf<String, String>())
        var saves = 0
        composeRule.setContent {
            TrackerTheme {
                TodayScreen(
                    metrics = listOf(weight, bp),
                    fieldText = fieldText,
                    error = null,
                    onFieldChange = { metricId, fieldId, value ->
                        fieldText = fieldText + ("$metricId/$fieldId" to value)
                    },
                    onSave = { saves++ },
                )
            }
        }
        composeRule.onNodeWithTag("today-field-weight-lb").performTextInput("180")
        composeRule.onNodeWithTag("today-field-blood_pressure-systolic").performTextInput("118")
        composeRule.onNodeWithTag("today-field-blood_pressure-diastolic").performTextInput("76")
        composeRule.onNodeWithTag("today-field-blood_pressure-pulse").performTextInput("72")
        assertEquals("180", fieldText["weight/lb"])
        assertEquals("118", fieldText["blood_pressure/systolic"])
        assertEquals("76", fieldText["blood_pressure/diastolic"])
        assertEquals("72", fieldText["blood_pressure/pulse"])
        composeRule.onNodeWithTag("btn-save-today").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun errorShown() {
        composeRule.setContent {
            TrackerTheme {
                TodayScreen(
                    metrics = listOf(bp),
                    fieldText = emptyMap(),
                    error = "Every field is required for Blood pressure.",
                    onFieldChange = { _, _, _ -> },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("Every field is required for Blood pressure.").assertIsDisplayed()
    }
}
