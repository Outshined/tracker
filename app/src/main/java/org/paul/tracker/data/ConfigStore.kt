package org.paul.tracker.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class ConfigStore(
    private val file: File,
    /** Invoked after tmp+sync, before ATOMIC_MOVE. Tests throw here to prove rollback. */
    private val onBeforeCommitFile: () -> Unit = {},
    /** Invoked under lock before transform so tests can mutate VM credentials. */
    private val onBeforeTransform: () -> Unit = {},
) {
    data class State(
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val insecureTls: Boolean = false,
        val lastBackupAt: Instant? = null,
        val lastRestoreAt: Instant? = null,
        val lastError: String = "",
    ) {
        // Password must never appear in logs (DESIGN logcat rule).
        override fun toString(): String =
            "State(url=$url, username=$username, password=<redacted>, insecureTls=$insecureTls, " +
                "lastBackupAt=$lastBackupAt, lastRestoreAt=$lastRestoreAt, lastError=$lastError)"
    }

    private val lock = Any()
    private val tmp = File(file.path + ".tmp")
    private var state: State = State()

    fun load(): State = synchronized(lock) {
        if (tmp.exists()) {
            tmp.delete()
        }
        if (!file.exists()) {
            state = State()
            return state
        }
        state = try {
            decode(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            State()
        }
        state
    }

    fun snapshot(): State = synchronized(lock) { state }

    fun persist(next: State): State = update { next }

    fun update(transform: (State) -> State): State = synchronized(lock) {
        onBeforeTransform()
        val next = transform(state)
        writeFile(next)
        state = next
        next
    }

    private fun writeFile(next: State) {
        val bytes = encode(next).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        onBeforeCommitFile()
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            if (file.exists() && !file.delete()) {
                throw IOException("cannot replace ${file.path}")
            }
            if (!tmp.renameTo(file)) {
                throw IOException("cannot rename tmp to ${file.path}")
            }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        internal fun encode(state: State): String {
            val obj = JsonObject(
                linkedMapOf(
                    "url" to JsonPrimitive(state.url),
                    "username" to JsonPrimitive(state.username),
                    "password" to JsonPrimitive(state.password),
                    "insecureTls" to JsonPrimitive(state.insecureTls),
                    "lastBackupAt" to instantElement(state.lastBackupAt),
                    "lastRestoreAt" to instantElement(state.lastRestoreAt),
                    "lastError" to JsonPrimitive(state.lastError),
                ),
            )
            return json.encodeToString(JsonElement.serializer(), obj)
        }

        internal fun decode(text: String): State {
            val obj = json.parseToJsonElement(text) as? JsonObject
                ?: return State()
            return State(
                url = stringOrEmpty(obj["url"]),
                username = stringOrEmpty(obj["username"]),
                password = stringOrEmpty(obj["password"]),
                insecureTls = (obj["insecureTls"] as? JsonPrimitive)?.booleanOrNull ?: false,
                lastBackupAt = parseInstant(obj["lastBackupAt"]),
                lastRestoreAt = parseInstant(obj["lastRestoreAt"]),
                lastError = stringOrEmpty(obj["lastError"]),
            )
        }

        private fun instantElement(value: Instant?): JsonElement =
            if (value == null) JsonNull else JsonPrimitive(value.toString())

        private fun parseInstant(el: JsonElement?): Instant? {
            val text = stringOrNull(el) ?: return null
            return try {
                Instant.parse(text)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        private fun stringOrEmpty(el: JsonElement?): String = stringOrNull(el) ?: ""

        private fun stringOrNull(el: JsonElement?): String? {
            val p = el as? JsonPrimitive ?: return null
            if (p is JsonNull || !p.isString) return null
            return p.content
        }
    }
}
