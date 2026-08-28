package org.bohme.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.bohme.tracker.data.BuiltInMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typeUrlUserPassAndToggleInsecure() {
        var url by mutableStateOf("")
        var user by mutableStateOf("")
        var pass by mutableStateOf("")
        var insecure by mutableStateOf(false)
        composeRule.setContent {
            TrackerTheme {
                SettingsScreen(
                    url = url,
                    username = user,
                    password = pass,
                    insecureTls = insecure,
                    lastBackupAt = null,
                    lastRestoreAt = null,
                    lastError = "",
                    backupEnabled = true,
                    restoreEnabled = true,
                    davInFlight = false,
                    restoreNeedsExtraConfirm = false,
                    showReset = false,
                    metrics = BuiltInMetrics.ALL,
                    samples = emptyList(),
                    onAddMetric = {},
                    onEditMetric = {},
                    onDeleteMetric = {},
                    onUrlChange = { url = it },
                    onUserChange = { user = it },
                    onPassChange = { pass = it },
                    onInsecureChange = { insecure = it },
                    onBackup = {},
                    onRestore = {},
                    onConfirmEmptyRestore = {},
                    onCancelEmptyRestore = {},
                    onReset = {},
                )
            }
        }
        composeRule.onNodeWithTag("field-dav-url")
            .performTextInput("https://example.com/tracker.json")
        composeRule.onNodeWithTag("field-dav-user").performTextInput("paul")
        composeRule.onNodeWithTag("field-dav-pass").performTextInput("secret")
        composeRule.onNodeWithTag("check-insecure-tls").performClick()
        assertEquals("https://example.com/tracker.json", url)
        assertEquals("paul", user)
        assertEquals("secret", pass)
        assertTrue(insecure)
    }

    @Test
    fun backupOpensOverwriteConfirmCancelAndConfirm() {
        var backups = 0
        composeRule.setContent {
            TrackerTheme {
                defaultSettings(onBackup = { backups++ })
            }
        }
        composeRule.onNodeWithTag("btn-backup").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Overwrite the server copy with local data? The current file at this URL will be lost.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, backups)
        composeRule.onNodeWithTag("btn-backup").performScrollTo().performClick()
        composeRule.onNodeWithText("Backup").performClick()
        assertEquals(1, backups)
    }

    @Test
    fun restoreOpensReplaceConfirmCancelAndConfirm() {
        var restores = 0
        composeRule.setContent {
            TrackerTheme {
                defaultSettings(onRestore = { restores++ })
            }
        }
        composeRule.onNodeWithTag("btn-restore").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Replace all local metrics and samples with the server copy? Samples only on this phone will be lost.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, restores)
        composeRule.onNodeWithTag("btn-restore").performScrollTo().performClick()
        composeRule.onNodeWithText("Restore").performClick()
        assertEquals(1, restores)
    }

    @Test
    fun extraConfirmReplaceVersusCancel() {
        var extra by mutableStateOf(true)
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            TrackerTheme {
                defaultSettings(
                    restoreNeedsExtraConfirm = extra,
                    onConfirmEmptyRestore = {
                        confirms++
                        extra = false
                    },
                    onCancelEmptyRestore = {
                        cancels++
                        extra = false
                    },
                )
            }
        }
        composeRule.onNodeWithText("Server copy has 0 samples. Replace local data anyway?")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancels)
        assertEquals(0, confirms)
        extra = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Replace").performClick()
        assertEquals(1, confirms)
    }

    @Test
    fun resetVisibleOnlyWhenShowResetConfirmAndCancel() {
        var showReset by mutableStateOf(false)
        var resets = 0
        composeRule.setContent {
            TrackerTheme {
                defaultSettings(showReset = showReset, onReset = { resets++ })
            }
        }
        composeRule.onAllNodesWithText("Reset local data").assertCountEquals(0)
        showReset = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reset local data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset local data").performClick()
        composeRule.onNodeWithText(
            "Discard unreadable local files (kept as store.json.corrupt) and start empty with built-in metrics?",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, resets)
        composeRule.onNodeWithText("Reset local data").performClick()
        composeRule.onNodeWithText("Reset").performClick()
        assertEquals(1, resets)
    }
}

@Composable
private fun defaultSettings(
    restoreNeedsExtraConfirm: Boolean = false,
    showReset: Boolean = false,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onConfirmEmptyRestore: () -> Unit = {},
    onCancelEmptyRestore: () -> Unit = {},
    onReset: () -> Unit = {},
) {
    SettingsScreen(
        url = "https://example.com/tracker.json",
        username = "paul",
        password = "",
        insecureTls = false,
        lastBackupAt = null,
        lastRestoreAt = null,
        lastError = "",
        backupEnabled = true,
        restoreEnabled = true,
        davInFlight = false,
        restoreNeedsExtraConfirm = restoreNeedsExtraConfirm,
        showReset = showReset,
        metrics = BuiltInMetrics.ALL,
        samples = emptyList(),
        onAddMetric = {},
        onEditMetric = {},
        onDeleteMetric = {},
        onUrlChange = {},
        onUserChange = {},
        onPassChange = {},
        onInsecureChange = {},
        onBackup = onBackup,
        onRestore = onRestore,
        onConfirmEmptyRestore = onConfirmEmptyRestore,
        onCancelEmptyRestore = onCancelEmptyRestore,
        onReset = onReset,
    )
}
