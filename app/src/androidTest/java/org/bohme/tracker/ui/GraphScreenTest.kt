package org.bohme.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.bohme.tracker.data.BuiltInMetrics
import org.bohme.tracker.data.Sample
import org.bohme.tracker.data.Sources
import org.bohme.tracker.stats.AverageMode
import org.bohme.tracker.stats.RangePreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-08-24T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private val metrics = BuiltInMetrics.ALL
    private val samples = listOf(
        Sample(
            id = "s1",
            metricId = "weight",
            recordedAt = now,
            modifiedAt = now,
            source = Sources.MANUAL,
            values = mapOf("lb" to 180.0),
        ),
    )

    @Test
    fun chipsRangesAveragesAndChart() {
        var selected by mutableStateOf(setOf("weight"))
        var range by mutableStateOf(RangePreset.D7)
        var from by mutableStateOf<LocalDate?>(null)
        var to by mutableStateOf<LocalDate?>(null)
        var average by mutableStateOf(AverageMode.Off)
        val toggles = mutableListOf<String>()
        val ranges = mutableListOf<RangePreset>()
        val averages = mutableListOf<AverageMode>()
        composeRule.setContent {
            TrackerTheme {
                GraphScreen(
                    metrics = metrics,
                    samples = samples,
                    selectedIds = selected,
                    rangePreset = range,
                    customFrom = from,
                    customTo = to,
                    averageMode = average,
                    now = now,
                    zone = zone,
                    onToggleMetric = { id ->
                        toggles.add(id)
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onRangePreset = {
                        ranges.add(it)
                        range = it
                    },
                    onCustomFrom = { from = it },
                    onCustomTo = { to = it },
                    onAverageMode = {
                        averages.add(it)
                        average = it
                    },
                )
            }
        }
        composeRule.onNodeWithTag("chart-weight").performScrollTo().assertIsDisplayed()
        for (id in metrics.map { it.id }) {
            composeRule.onNodeWithTag("chip-metric-$id").performScrollTo().performClick()
        }
        assertEquals(metrics.map { it.id }, toggles)
        for (preset in RangePreset.entries) {
            composeRule.onNodeWithTag("chip-range-${preset.name}").performScrollTo().performClick()
        }
        assertEquals(RangePreset.entries.toList(), ranges)
        composeRule.onNodeWithTag("chip-range-Custom").performScrollTo().performClick()
        range = RangePreset.Custom
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn-custom-from").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-custom-to").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-custom-from").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("btn-custom-to").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        for (mode in AverageMode.entries) {
            composeRule.onNodeWithTag("chip-average-${mode.name}").performScrollTo().performClick()
        }
        assertEquals(AverageMode.entries.toList(), averages)
    }

    @Test
    fun weeklyAverageShowsCaptionOffHidesIt() {
        var average by mutableStateOf(AverageMode.Off)
        composeRule.setContent {
            TrackerTheme {
                GraphScreen(
                    metrics = metrics,
                    samples = samples,
                    selectedIds = setOf("weight"),
                    rangePreset = RangePreset.D7,
                    customFrom = null,
                    customTo = null,
                    averageMode = average,
                    now = now,
                    zone = zone,
                    onToggleMetric = {},
                    onRangePreset = {},
                    onCustomFrom = {},
                    onCustomTo = {},
                    onAverageMode = { average = it },
                )
            }
        }
        composeRule.onAllNodesWithTag("caption-average-overlay").assertCountEquals(0)
        composeRule.onNodeWithTag("chip-average-Weekly").performScrollTo().performClick()
        composeRule.onNodeWithTag("caption-average-overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Lighter dashed line is the weekly average.").assertIsDisplayed()
        composeRule.onNodeWithTag("chip-average-Off").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("caption-average-overlay").assertCountEquals(0)
    }
}
