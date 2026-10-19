package ai.zetic.realtimetranslate

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SavedTurn(val id: String, val speaker: String, val original: String, val translation: String?,
                     val sourceLanguage: String, val targetLanguage: String)
data class SavedSession(val schemaVersion: Int = 1, val id: String = UUID.randomUUID().toString(), val title: String,
    val startedAt: Long = System.currentTimeMillis(), val endedAt: Long? = null, val turns: List<SavedTurn>) {
    val durationText: String get() {
        val end = endedAt ?: return ""
        val seconds = ((end - startedAt) / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

/** Private no-backup storage. Atomic replacement retains the prior version on write failure. */
class SessionFileStore(private val directory: File) {
    private val gson = Gson()
    // Gson can assign null to non-null Kotlin fields when disk data is malformed.
    @Suppress("SENSELESS_COMPARISON")
    fun load(): List<SavedSession> = directory.listFiles().orEmpty().filter { it.extension == "json" }.map {
        gson.fromJson(it.readText(), SavedSession::class.java).also { session ->
            require(session.schemaVersion == 1)
            UUID.fromString(session.id)
            require(session.title != null && session.turns != null)
            session.turns.forEach { turn ->
                requireNotNull(turn)
                require(turn.id != null && turn.speaker != null && turn.original != null && turn.sourceLanguage != null && turn.targetLanguage != null)
            }

        }
    }.sortedByDescending { it.startedAt }
    fun save(session: SavedSession) {
        val destination = file(session.id)
        check(directory.isDirectory || directory.mkdirs())
        val temp = File(directory, "${session.id}.tmp")
        try {
            FileOutputStream(temp).use { out -> out.write(gson.toJson(session).toByteArray(Charsets.UTF_8)); out.fd.sync() }
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    fun delete(id: String) { check(file(id).delete()) }
    private fun file(id: String): File { require(id.matches(Regex("[a-zA-Z0-9-]+"))); return File(directory, "$id.json") }
}

data class HistoryState(val sessions: List<SavedSession> = emptyList(), val storageFailed: Boolean = false)

class SessionHistory(private val store: SessionFileStore? = null) {
    private val mutable = MutableStateFlow(HistoryState())
    val state = mutable.asStateFlow()
    val savedSessions get() = mutable.value.sessions.filterNot { it.id == activeId }
    private var activeId: String? = null
    private var captureStartedAt: Long? = null
    fun beginCapture() { if (activeId == null) captureStartedAt = System.currentTimeMillis() }
    init {
        try { mutable.value = HistoryState(sessions = store?.load().orEmpty()) }
        catch (_: Exception) { mutable.value = HistoryState(storageFailed = true) }
    }
    fun checkpoint(turns: List<SavedTurn>, ending: Boolean = false): String? {
        if (turns.isEmpty() || mutable.value.storageFailed) return null
        val old = mutable.value.sessions.firstOrNull { it.id == activeId }
        val session = (old ?: SavedSession(title = turns.first().original.prefixCodePoints(60), turns = emptyList(), startedAt = captureStartedAt ?: System.currentTimeMillis())).copy(
            turns = turns, endedAt = if (ending) System.currentTimeMillis() else null)
        if (!ending && old?.turns == turns) return old.id
        if (!persist(session)) return null
        activeId = if (ending) null else session.id
        if (ending) captureStartedAt = null
        return session.id
    }
    private fun persist(session: SavedSession): Boolean = try {
        store?.save(session)
        mutable.value = mutable.value.copy(sessions = (mutable.value.sessions.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.startedAt })
        true
    } catch (_: Exception) { mutable.value = mutable.value.copy(storageFailed = true); false }
    fun rename(id: String, title: String) {
        val clean = title.trim().prefixCodePoints(80)
        val session = mutable.value.sessions.firstOrNull { it.id == id } ?: return
        if (clean.isNotEmpty()) persist(session.copy(title = clean))
    }
    fun delete(id: String) {
        if (id == activeId) return
        try { store?.delete(id); mutable.value = mutable.value.copy(sessions = mutable.value.sessions.filterNot { it.id == id }) }
        catch (_: Exception) { mutable.value = mutable.value.copy(storageFailed = true) }
    }
    fun retryStorage() {
        try {
            val loaded = store?.load().orEmpty()
            mutable.value = mutable.value.copy(storageFailed = false,
                sessions = (mutable.value.sessions + loaded.filter { disk -> mutable.value.sessions.none { it.id == disk.id } }))
        } catch (_: Exception) { mutable.value = mutable.value.copy(storageFailed = true) }
    }
}

private fun String.prefixCodePoints(limit: Int): String = substring(0, offsetByCodePoints(0, codePointCount(0, length).coerceAtMost(limit)))
