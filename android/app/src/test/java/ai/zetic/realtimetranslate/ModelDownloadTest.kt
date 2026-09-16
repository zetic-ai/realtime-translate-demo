package ai.zetic.realtimetranslate

import android.content.Context
import android.content.ContextWrapper
import com.zeticai.mlange.core.background.BackgroundDownloadHandle
import com.zeticai.mlange.core.background.BackgroundDownloadStatus
import com.zeticai.mlange.core.background.ModelRemovalResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadTest {
    @Test fun `cancellation after handle creation stops the owned transfer without orphaning it`() = runTest {
        val preferences = MemoryModelDownloadPreferences(modelDownloadConsent = false)
        val sdk = FakeModelDownloadSdk()
        val download = HyMt2BackgroundDownload(
            context = TestContext(),
            preferences = preferences,
            personalKey = "key",
            sdk = sdk,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        lateinit var operation: Deferred<ModelDownloadUiState>
        sdk.afterHandleCreated = { operation.cancel() }

        operation = async(start = CoroutineStart.LAZY) { download.grantConsentAndSchedule() }
        operation.start()
        runCurrent()

        assertTrue(operation.isCancelled)
        assertEquals(listOf("created"), sdk.stoppedHandles)
        assertNull(preferences.backgroundDownloadHandleId)
        assertEquals(0, sdk.statusLookups)
    }

    @Test fun `explicit cancellation clears a successfully stopped persisted handle`() = runTest {
        val preferences = MemoryModelDownloadPreferences(
            modelDownloadConsent = true,
            backgroundDownloadHandleId = "existing",
        )
        val sdk = FakeModelDownloadSdk()
        val download = HyMt2BackgroundDownload(
            context = TestContext(),
            preferences = preferences,
            personalKey = "key",
            sdk = sdk,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        download.cancel()

        assertEquals(listOf("existing"), sdk.stoppedHandles)
        assertNull(preferences.backgroundDownloadHandleId)
    }

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private data class MemoryModelDownloadPreferences(
        override var hasEverLoadedModel: Boolean = false,
        override var modelDownloadConsent: Boolean = false,
        override var backgroundDownloadHandleId: String? = null,
    ) : ModelDownloadPreferenceStore

    private class FakeModelDownloadSdk : ModelDownloadSdk {
        var afterHandleCreated: () -> Unit = {}
        var statusLookups = 0
        val stoppedHandles = mutableListOf<String>()

        override fun download(context: Context, personalKey: String): BackgroundDownloadHandle =
            BackgroundDownloadHandle("created").also { afterHandleCreated() }

        override fun status(context: Context, handle: BackgroundDownloadHandle): BackgroundDownloadStatus {
            statusLookups += 1
            error("status should not be reached after cancellation")
        }

        override fun stop(context: Context, handle: BackgroundDownloadHandle) {
            stoppedHandles += handle.id
        }

        override fun remove(context: Context): ModelRemovalResult = error("not used")
    }
}
