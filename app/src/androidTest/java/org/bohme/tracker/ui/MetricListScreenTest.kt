package org.bohme.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class MetricListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bhb = BuiltInMetrics.ALL.first { it.id == "bhb" }
    private val t0 = Instant.parse("2026-08-24T08:00:00Z")

    @Test
    fun tapRowCallsOnOpenEntry() {
        val opens = mutableListOf<String>()
        setList(onOpenEntry = { opens.add(it) })
        composeRule.onNodeWithTag("metric-row-weight").performClick()
        assertEquals(listOf("weight"), opens)
    }

    @Test
    fun editCallsOnEdit() {
        val edits = mutableListOf<String>()
        setList(onEdit = { edits.add(it) })
        composeRule.onNodeWithTag("metric-edit-weight").performClick()
        assertEquals(listOf("weight"), edits)
    }

    @Test
    fun deleteOpensConfirmCancelDoesNotCallOnDelete() {
        val deletes = mutableListOf<String>()
        setList(onDelete = { deletes.add(it) })
        composeRule.onNodeWithTag("metric-delete-weight").performClick()
        composeRule.onNodeWithText("Delete Weight and 0 sample(s)? This cannot be undone.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(deletes.isEmpty())
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmCallsOnDelete() {
        val deletes = mutableListOf<String>()
        val sample = Sample(
            id = "s1",
            metricId = "weight",
            recordedAt = t0,
            modifiedAt = t0,
            source = Sources.MANUAL,
            values = mapOf("lb" to 180.0),
        )
        setList(samples = listOf(sample), onDelete = { deletes.add(it) })
        composeRule.onNodeWithTag("metric-delete-weight").performClick()
        composeRule.onNodeWithText("Delete Weight and 1 sample(s)? This cannot be undone.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        assertEquals(listOf("weight"), deletes)
    }

    private fun setList(
        samples: List<Sample> = emptyList(),
        onOpenEntry: (String) -> Unit = {},
        onEdit: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            TrackerTheme {
                MetricListScreen(
                    metrics = listOf(weight, bhb),
                    samples = samples,
                    zone = zone,
                    onOpenEntry = onOpenEntry,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}
