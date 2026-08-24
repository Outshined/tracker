package org.bohme.tracker

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Seed is first-launch only; leftover store/webdav files would skip built-ins and keep DAV fields. */
internal class ClearAppFilesRule : TestRule {
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

/** Writes unreadable live+bak before the activity starts so load is Corrupt. */
internal class SeedCorruptStoreRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                clearAppFiles()
                val dir = appFilesDir()
                File(dir, "store.json").writeText("NOT JSON")
                File(dir, "store.json.bak").writeText("ALSO BAD")
                try {
                    base.evaluate()
                } finally {
                    clearAppFiles()
                }
            }
        }
    }
}

internal fun appFilesDir(): File =
    InstrumentationRegistry.getInstrumentation().targetContext.filesDir

internal fun clearAppFiles() {
    val dir = appFilesDir()
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
