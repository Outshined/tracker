package org.paul.tracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(ClearAppFilesRule()).around(composeRule)

    @Test
    fun `T-metrics-list-shows-Weight-BHB-Blood-glucose-Blood-pressure`() {
        awaitReady()
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
        composeRule.onNodeWithText("BHB").assertIsDisplayed()
        composeRule.onNodeWithText("Blood glucose").assertIsDisplayed()
        composeRule.onNodeWithText("Blood pressure").assertIsDisplayed()
    }

    @Test
    fun `T-entry-type-weight-save-row-appears`() {
        awaitReady()
        composeRule.onNodeWithText("Weight").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("180")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("180 lb").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("180 lb").assertIsDisplayed()
    }

    @Test
    fun `T-graphs-chart-weight-after-sample`() {
        awaitReady()
        composeRule.onNodeWithText("Weight").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("180")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("180 lb").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Graphs").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("chart-weight").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("chart-weight").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `T-settings-backup-and-restore-open-confirm-dialogs`() {
        awaitReady()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Backup now").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Backup now").assertIsDisplayed()
        composeRule.onNodeWithText("Restore now").assertIsDisplayed()
        composeRule.onNode(hasText("WebDAV URL") and hasSetTextAction())
            .performTextInput("https://example.com/remote.php/dav/files/paul/tracker.json")
        composeRule.onNode(hasText("Username") and hasSetTextAction()).performTextInput("paul")
        composeRule.onNodeWithText("Backup now").performClick()
        composeRule.onNodeWithText(
            "Overwrite the server copy with local data? The current file at this URL will be lost.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Restore now").performClick()
        composeRule.onNodeWithText(
            "Replace all local metrics and samples with the server copy? Samples only on this phone will be lost.",
        ).assertIsDisplayed()
    }

    private fun awaitReady() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Weight").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

/** Seed is first-launch only; leftover store/webdav files would skip built-ins and keep DAV fields. */
private class ClearAppFilesRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                clearAppFiles()
                try {
                    base.evaluate()
                } finally {
                    clearAppFiles()
                }
            }
        }
    }
}

private fun clearAppFiles() {
    val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
    listOf(
        "store.json",
        "store.json.bak",
        "store.json.tmp",
        "store.json.corrupt",
        "store.json.bak.corrupt",
        "webdav.json",
        "webdav.json.tmp",
    ).forEach { name -> File(dir, name).delete() }
}
