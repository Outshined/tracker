package org.bohme.tracker

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(ClearAppFilesRule()).around(composeRule)

    @Test
    fun firstLaunchShowsFourBuiltIns() {
        awaitReady()
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
        composeRule.onNodeWithText("BHB").assertIsDisplayed()
        composeRule.onNodeWithText("Blood glucose").assertIsDisplayed()
        composeRule.onNodeWithText("Blood pressure").assertIsDisplayed()
    }

    @Test
    fun addMetricSaveAppearsOnList() {
        awaitReady()
        composeRule.onNodeWithTag("btn-add-metric").performClick()
        composeRule.onNodeWithTag("field-metric-label").performTextInput("Steps")
        composeRule.onNodeWithTag("field-field-label-0").performTextInput("Count")
        composeRule.onNodeWithTag("btn-save-metric").performClick()
        waitForText("Steps")
        composeRule.onNodeWithText("Steps").assertIsDisplayed()
    }

    @Test
    fun addMetricBlankLabelShowsErrorStaysOnAdd() {
        awaitReady()
        composeRule.onNodeWithTag("btn-add-metric").performClick()
        composeRule.onNodeWithTag("btn-save-metric").performClick()
        composeRule.onNodeWithText("Label is required.").assertIsDisplayed()
        composeRule.onNodeWithTag("field-metric-label").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-save-metric").assertIsDisplayed()
    }

    @Test
    fun editWeightUnitSaveListStillWeight() {
        awaitReady()
        composeRule.onNodeWithTag("metric-edit-weight").performClick()
        waitForTag("field-field-unit-0")
        composeRule.onNodeWithTag("field-field-unit-0").performTextReplacement("kg")
        composeRule.onNodeWithTag("btn-save-metric").performClick()
        waitForTag("metric-row-weight")
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
    }

    @Test
    fun editMetricCannotAddOrRemoveFields() {
        awaitReady()
        composeRule.onNodeWithTag("metric-edit-weight").performClick()
        waitForTag("field-metric-label")
        composeRule.onAllNodesWithTag("btn-add-field").assertCountEquals(0)
        composeRule.onAllNodesWithTag("btn-remove-field-0").assertCountEquals(0)
    }

    @Test
    fun deleteMetricCancelKeepsConfirmRemoves() {
        awaitReady()
        composeRule.onNodeWithTag("metric-delete-bhb").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("BHB").assertIsDisplayed()
        composeRule.onNodeWithTag("metric-delete-bhb").performClick()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("BHB").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
    }

    @Test
    fun entryWeightSaveShowsOnEntryAndList() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("180")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("180 lb")
        composeRule.onNodeWithText("180 lb").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-back").performClick()
        waitForTag("metric-row-weight")
        composeRule.onNodeWithText("180 lb").assertIsDisplayed()
    }

    @Test
    fun entryBpRequiresAllThreeFields() {
        awaitReady()
        composeRule.onNodeWithTag("metric-row-blood_pressure").performClick()
        waitForTag("entry-field-systolic")
        composeRule.onNodeWithTag("entry-field-systolic").performTextInput("118")
        composeRule.onNodeWithTag("entry-field-diastolic").performTextInput("76")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        composeRule.onNodeWithText("Every field is required.").assertIsDisplayed()
        composeRule.onNodeWithTag("entry-field-pulse").performTextInput("72")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("118/76 mmHg, 72 bpm")
        composeRule.onNodeWithText("118/76 mmHg, 72 bpm").assertIsDisplayed()
    }

    @Test
    fun entryEmptySaveShowsError() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        composeRule.onNodeWithText("Every field is required.").assertIsDisplayed()
    }

    @Test
    fun newSampleClearsFieldsAfterSave() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("180")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("180 lb")
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("200")
        composeRule.onNodeWithTag("btn-new-sample").performClick()
        composeRule.onNodeWithTag("entry-field-lb").assertIsDisplayed()
    }

    @Test
    fun editExistingSampleByTappingRow() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("180")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("180 lb")
        composeRule.onNode(hasTestTagPrefix("sample-row-")).performClick()
        composeRule.onNodeWithTag("entry-field-lb").performTextReplacement("190")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("190 lb")
        composeRule.onNodeWithText("190 lb").assertIsDisplayed()
    }

    @Test
    fun deleteSampleConfirmAndCancel() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("180")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("180 lb")
        composeRule.onNode(hasTestTagPrefix("sample-delete-")).performClick()
        composeRule.onNodeWithText("Delete this sample? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("180 lb").assertIsDisplayed()
        composeRule.onNode(hasTestTagPrefix("sample-delete-")).performClick()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("180 lb").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun dateAndTimePickersOpenAndCancel() {
        awaitReady()
        openWeightEntry()
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
    fun tabSwitchPreservesEntryBuffers() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("123")
        composeRule.onNodeWithTag("tab-Graphs").performClick()
        composeRule.onNodeWithTag("tab-Metrics").performClick()
        waitForTag("entry-field-lb")
        composeRule.onNodeWithTag("entry-field-lb").assertIsDisplayed()
        composeRule.onNodeWithText("123").assertIsDisplayed()
    }

    @Test
    fun backFromEntryAndAddReturnsToList() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("btn-back").performClick()
        waitForTag("metric-row-weight")
        composeRule.onNodeWithTag("btn-add-metric").performClick()
        waitForTag("field-metric-label")
        composeRule.onNodeWithTag("btn-back").performClick()
        waitForTag("metric-row-weight")
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
    }

    @Test
    fun graphsAfterWeightSample() {
        awaitReady()
        openWeightEntry()
        composeRule.onNodeWithTag("entry-field-lb").performTextInput("180")
        composeRule.onNodeWithTag("btn-save-sample").performClick()
        waitForText("180 lb")
        composeRule.onNodeWithTag("tab-Graphs").performClick()
        waitForTag("chip-metric-weight")
        composeRule.onNodeWithTag("chip-metric-weight").performClick()
        composeRule.onNodeWithTag("chip-metric-weight").performClick()
        for (preset in listOf("D7", "D30", "D90", "Y1", "All", "Custom")) {
            composeRule.onNodeWithTag("chip-range-$preset").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("btn-custom-from").assertIsDisplayed()
        composeRule.onNodeWithTag("btn-custom-to").assertIsDisplayed()
        for (mode in listOf("Off", "Daily", "Weekly", "Monthly")) {
            composeRule.onNodeWithTag("chip-average-$mode").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("chip-range-D7").performScrollTo().performClick()
        composeRule.onNodeWithTag("chart-weight").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsBackupAndRestoreConfirmCancel() {
        awaitReady()
        composeRule.onNodeWithTag("tab-Settings").performClick()
        waitForTag("field-dav-url")
        composeRule.onNodeWithTag("field-dav-url")
            .performTextInput("https://example.com/remote.php/dav/files/paul/tracker.json")
        composeRule.onNodeWithTag("field-dav-user").performTextInput("paul")
        composeRule.onNodeWithTag("btn-backup").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Overwrite the server copy with local data? The current file at this URL will be lost.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("btn-restore").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Replace all local metrics and samples with the server copy? Samples only on this phone will be lost.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun settingsInsecureTlsToggle() {
        awaitReady()
        composeRule.onNodeWithTag("tab-Settings").performClick()
        waitForTag("check-insecure-tls")
        composeRule.onNodeWithTag("check-insecure-tls").performClick()
        composeRule.onNodeWithTag("check-insecure-tls").performClick()
        composeRule.onNodeWithTag("check-insecure-tls").assertIsDisplayed()
    }

    @Test
    fun settingsPasswordFieldAcceptsInput() {
        awaitReady()
        composeRule.onNodeWithTag("tab-Settings").performClick()
        waitForTag("field-dav-pass")
        composeRule.onNodeWithTag("field-dav-pass").performTextInput("secret")
        composeRule.onNodeWithTag("field-dav-pass").assertIsDisplayed()
    }

    private fun awaitReady() {
        waitForText("Weight", timeout = 10_000)
    }

    private fun openWeightEntry() {
        composeRule.onNodeWithTag("metric-row-weight").performClick()
        waitForTag("entry-field-lb")
    }

    private fun waitForTag(tag: String, timeout: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis = timeout) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeout: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis = timeout) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class TrackerCorruptFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(SeedCorruptStoreRule()).around(composeRule)

    @Test
    fun corruptBannerResetCancelThenResetSeedsBuiltIns() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(
                "Local data file is unreadable. Restore from WebDAV or Reset local data.",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            "Local data file is unreadable. Restore from WebDAV or Reset local data.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("tab-Settings").performClick()
        composeRule.onAllNodesWithText("Reset local data").onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText("Reset local data").onLast().performClick()
        composeRule.onNodeWithText(
            "Discard unreadable local files (kept as store.json.corrupt) and start empty with built-in metrics?",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(
            "Local data file is unreadable. Restore from WebDAV or Reset local data.",
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Reset local data").onLast().performClick()
        composeRule.onNodeWithText("Reset").performClick()
        composeRule.onNodeWithTag("tab-Metrics").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Weight").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
        composeRule.onNodeWithText("BHB").assertIsDisplayed()
    }
}

private fun hasTestTagPrefix(prefix: String) =
    SemanticsMatcher("TestTag starts with $prefix") { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        tag != null && tag.startsWith(prefix)
    }
