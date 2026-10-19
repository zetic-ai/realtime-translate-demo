package ai.zetic.realtimetranslate

import java.nio.file.Files
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionHistoryTest {
    private fun turn(text: String = "We agreed to meet on Friday.") = SavedTurn("turn-1", "A", text, "Vendredi.", "en", "fr")
    @Test fun `history round trips original and translation and supports rename and deletion`() = runTest {
        val dir = Files.createTempDirectory("relais-history").toFile()
        try {
            val store = SessionFileStore(dir)
            val history = SessionHistory(store)
            history.checkpoint(listOf(turn()))
            assertTrue(history.savedSessions.isEmpty())
            assertEquals(listOf(turn()), store.load().single().turns)
            val id = requireNotNull(history.checkpoint(listOf(turn()), ending = true))
            assertEquals(1, history.savedSessions.size)
            history.rename(id, "Meeting")
            val restored = SessionHistory(store).state.value.sessions.single()
            assertEquals("Meeting", restored.title)
            assertEquals(listOf(turn()), restored.turns)
            assertNotNull(restored.endedAt)
            history.delete(id)
            assertTrue(store.load().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `unfinished sessions recover with their transcript`() = runTest {
        val dir = Files.createTempDirectory("relais-recovery").toFile()
        try {
            val store = SessionFileStore(dir)
            store.save(SavedSession(title = "Interrupted", turns = listOf(turn())))
            val recovered = SessionHistory(store).state.value.sessions.single()
            assertNull(recovered.endedAt)
            assertEquals(1, SessionHistory(store).savedSessions.size)
            assertEquals(turn().original, recovered.turns.single().original)
        } finally { dir.deleteRecursively() }
    }
    @Test fun `write failure does not pretend session was saved`() = runTest {
        val file = File.createTempFile("relais-blocked", ".tmp")
        try {
            val history = SessionHistory(SessionFileStore(File(file, "impossible")))
            assertNull(history.checkpoint(listOf(turn()), ending = true))
            assertTrue(history.state.value.storageFailed)
            assertTrue(history.state.value.sessions.isEmpty())
        } finally { file.delete() }
    }
    @Test fun `malformed storage is retained and reported`() = runTest {
        val dir = Files.createTempDirectory("relais-corrupt").toFile()
        try {
            File(dir, "broken.json").writeText("broken")
            val history = SessionHistory(SessionFileStore(dir))
            assertTrue(history.state.value.storageFailed)
            assertEquals("broken", File(dir, "broken.json").readText())
        } finally { dir.deleteRecursively() }
    }
}
