package org.bohme.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneOffset
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bp = BuiltInMetrics.ALL.first { it.id == "blood_pressure" }
    private val sample = Sample(
        id = "s1",
        metricId = "weight",
        recordedAt = t0,
        modifiedAt = t0,
        source = Sources.MANUAL,
        values = mapOf("lb" to 180.0),
    )

    @Test
    fun typeEachFieldSaveAndNewSample() {
        var fieldText by mutableStateOf(mapOf<String, String>())
        var saves = 0
        var news = 0
        composeRule.setContent {
            TrackerTheme {
                EntryScreen(
                    metric = bp,
                    samples = emptyList(),
                    fieldText = fieldText,
                    recordedAt = t0,
                    zone = zone,
                    error = null,
                    onFieldChange = { id, v -> fieldText = fieldText + (id to v) },
                    onRecordedAtChange = {},
                    onSave = { saves++ },
                    onNewSample = {
                        news++
                        fieldText = emptyMap()
                    },
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("entry-field-systolic").performTextInput("118")
        composeRule.onNodeWithTag("entry-field-diastolic").performTextInput("76")
        composeRule.onNodeWithTag("entry-field-pulse").performTextInput("72")
        assertEquals("118", fieldText["systolic"])
        assertEquals("76", fieldText["diastolic"])
        assertEquals("72", fieldText["pulse"])
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        assertEquals(1, saves)
        composeRule.onNodeWithTag("btn-new-sample").performClick()
        assertEquals(1, news)
        assertTrue(fieldText.isEmpty())
    }

    @Test
    fun dateAndTimeButtonsOpenDialogsCancel() {
        composeRule.setContent {
            TrackerTheme {
                EntryScreen(
                    metric = weight,
                    samples = emptyList(),
                    fieldText = emptyMap(),
                    recordedAt = t0,
                    zone = zone,
                    error = null,
                    onFieldChange = { _, _ -> },
                    onRecordedAtChange = {},
                    onSave = {},
                    onNewSample = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("btn-entry-date").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("btn-entry-date").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-entry-time").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("btn-entry-time").assertIsDisplayed()
    }

    @Test
    fun tapSampleRowOnEdit() {
        val edits = mutableListOf<String>()
        composeRule.setContent {
            TrackerTheme {
                EntryScreen(
                    metric = weight,
                    samples = listOf(sample),
                    fieldText = emptyMap(),
                    recordedAt = t0,
                    zone = zone,
                    error = null,
                    onFieldChange = { _, _ -> },
                    onRecordedAtChange = {},
                    onSave = {},
                    onNewSample = {},
                    onEdit = { edits.add(it) },
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("sample-row-s1").performClick()
        assertEquals(listOf("s1"), edits)
    }

    @Test
    fun deleteOpensConfirmCancelVersusConfirm() {
        val deletes = mutableListOf<String>()
        composeRule.setContent {
            TrackerTheme {
                EntryScreen(
                    metric = weight,
                    samples = listOf(sample),
                    fieldText = emptyMap(),
                    recordedAt = t0,
                    zone = zone,
                    error = null,
                    onFieldChange = { _, _ -> },
                    onRecordedAtChange = {},
                    onSave = {},
                    onNewSample = {},
                    onEdit = {},
                    onDelete = { deletes.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("sample-delete-s1").performClick()
        composeRule.onNodeWithText("Delete this sample? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(deletes.isEmpty())
        composeRule.onNodeWithTag("sample-delete-s1").performClick()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        assertEquals(listOf("s1"), deletes)
    }
}
