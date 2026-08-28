package org.bohme.tracker.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.bohme.tracker.data.BuiltInMetrics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val weight = BuiltInMetrics.ALL.first { it.id == "weight" }
    private val bhb = BuiltInMetrics.ALL.first { it.id == "bhb" }

    @Test
    fun tapRowCallsOnOpenEntry() {
        val opens = mutableListOf<String>()
        setList(onOpenEntry = { opens.add(it) })
        composeRule.onNodeWithTag("metric-row-weight").performClick()
        assertEquals(listOf("weight"), opens)
    }

    private fun setList(
        onOpenEntry: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            TrackerTheme {
                MetricListScreen(
                    metrics = listOf(weight, bhb),
                    samples = emptyList(),
                    zone = java.time.ZoneOffset.UTC,
                    onOpenEntry = onOpenEntry,
                )
            }
        }
    }
}
