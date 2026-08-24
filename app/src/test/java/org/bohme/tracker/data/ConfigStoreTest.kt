package org.bohme.tracker.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigStoreTest {
    private val t0 = Instant.parse("2026-08-24T12:00:00Z")
    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("config-test").toFile()
        file = File(dir, "webdav.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `T-configstore-missing-file-defaults-no-throw-no-file`() {
        val store = ConfigStore(file)
        val loaded = store.load()
        assertEquals(ConfigStore.State(), loaded)
        assertEquals("", loaded.url)
        assertEquals("", loaded.username)
        assertEquals("", loaded.password)
        assertFalse(loaded.insecureTls)
        assertNull(loaded.lastBackupAt)
        assertNull(loaded.lastRestoreAt)
        assertEquals("", loaded.lastError)
        assertFalse(file.exists())
        assertFalse(File(file.path + ".tmp").exists())
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test
    fun `T-configstore-garbage-file-defaults-no-throw`() {
        file.writeText("NOT JSON {{{")
        val store = ConfigStore(file)
        val loaded = store.load()
        assertEquals(ConfigStore.State(), loaded)
        assertEquals("NOT JSON {{{", file.readText())
        assertTrue(file.exists())
    }

    @Test
    fun `garbage array object and empty file yield defaults without throw`() {
        file.writeText("[]")
        assertEquals(ConfigStore.State(), ConfigStore(file).load())
        file.writeText("")
        assertEquals(ConfigStore.State(), ConfigStore(file).load())
        file.writeText("{]")
        assertEquals(ConfigStore.State(), ConfigStore(file).load())
        file.writeText("<html>nope</html>")
        assertEquals(ConfigStore.State(), ConfigStore(file).load())
    }

    @Test
    fun `persist roundtrip and no bak leftover tmp`() {
        val store = ConfigStore(file)
        store.load()
        val next = ConfigStore.State(
            url = "https://nas.example/tracker.json",
            username = "paul",
            password = "app-password-xyz",
            insecureTls = true,
            lastBackupAt = t0,
            lastRestoreAt = null,
            lastError = "HTTP 500",
        )
        store.persist(next)
        assertTrue(file.exists())
        assertFalse(File(file.path + ".tmp").exists())
        assertFalse(File(file.path + ".bak").exists())
        assertEquals(next, store.snapshot())
        val again = ConfigStore(file)
        assertEquals(next, again.load())
        val text = file.readText()
        assertTrue(text.contains("\"password\""))
        assertTrue(text.contains("app-password-xyz"))
        assertTrue(text.contains("lastRestoreAt"))
        assertTrue(text.contains("null"))
    }

    @Test
    fun `unknown keys skip missing keys default`() {
        file.writeText(
            """
            {
              "url": "https://nas.example/tracker.json",
              "username": "paul",
              "_extra": 1,
              "bogus": "x"
            }
            """.trimIndent(),
        )
        val loaded = ConfigStore(file).load()
        assertEquals("https://nas.example/tracker.json", loaded.url)
        assertEquals("paul", loaded.username)
        assertEquals("", loaded.password)
        assertFalse(loaded.insecureTls)
        assertNull(loaded.lastBackupAt)
        assertEquals("", loaded.lastError)
        val store = ConfigStore(file)
        store.load()
        store.persist(store.snapshot())
        val written = file.readText()
        assertFalse(written.contains("_extra"))
        assertFalse(written.contains("bogus"))
    }

    @Test
    fun `update preserves timestamps when rewriting credentials`() {
        val store = ConfigStore(file)
        store.persist(
            ConfigStore.State(
                url = "https://a.example/t.json",
                username = "a",
                password = "p",
                lastBackupAt = t0,
                lastError = "old",
            ),
        )
        val next = store.update {
            it.copy(url = "https://b.example/t.json", username = "b", lastError = "")
        }
        assertEquals(t0, next.lastBackupAt)
        assertEquals("https://b.example/t.json", next.url)
        assertEquals("b", next.username)
        assertEquals("", next.lastError)
        assertEquals(t0, ConfigStore(file).load().lastBackupAt)
    }

    @Test
    fun `toString redacts password`() {
        val state = ConfigStore.State(
            url = "https://nas.example/tracker.json",
            username = "paul",
            password = "app-password-xyz",
            insecureTls = true,
            lastError = "timeout",
        )
        val text = state.toString()
        assertFalse(text.contains("app-password-xyz"))
        assertTrue(text.contains("<redacted>"))
        assertTrue(text.contains("paul"))
        assertEquals(state, state.copy())
        assertNotEquals(state, state.copy(password = "other"))
    }

    @Test
    fun `onBeforeCommitFile throw leaves memory and destination unchanged`() {
        val store = ConfigStore(file)
        store.persist(ConfigStore.State(url = "https://ok.example/t.json", username = "paul"))
        val failing = ConfigStore(
            file,
            onBeforeCommitFile = { throw IOException("injected") },
        )
        failing.load()
        assertThrows(IOException::class.java) {
            failing.persist(failing.snapshot().copy(password = "secret"))
        }
        assertEquals("", failing.snapshot().password)
        assertEquals("https://ok.example/t.json", failing.snapshot().url)
        val onDisk = ConfigStore(file).load()
        assertEquals("https://ok.example/t.json", onDisk.url)
        assertEquals("", onDisk.password)
    }

    @Test
    fun `leftover tmp deleted on load and not parsed`() {
        File(file.path + ".tmp").writeText("""{"url":"https://from-tmp.example/x"}""")
        val loaded = ConfigStore(file).load()
        assertEquals("", loaded.url)
        assertFalse(File(file.path + ".tmp").exists())
        assertFalse(file.exists())
    }
}
