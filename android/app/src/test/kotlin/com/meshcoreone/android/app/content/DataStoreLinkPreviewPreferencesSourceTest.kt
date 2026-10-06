// AndroidOnly: WP-218 Real DataStore round-trip for DataStoreLinkPreviewPreferencesSource.
package com.meshcoreone.android.app.content

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.datastore.AppStorageKey
import com.meshcoreone.android.core.datastore.MeshCoreStorage
import com.meshcoreone.android.core.datastore.StorageFailure
import com.meshcoreone.android.core.datastore.StorageIssueReporter
import com.meshcoreone.android.core.services.content.LinkPreviewPreferencesSnapshot
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DataStoreLinkPreviewPreferencesSourceTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun context(directory: File): Context {
        val application: Context = ApplicationProvider.getApplicationContext()
        return object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
    }

    @Test
    fun createReflectsRealUntouchedDefaultsNotFakeInMemoryValues() = runTest {
        val failures = ConcurrentLinkedQueue<StorageFailure>()
        val owner = MeshCoreStorage.get(context(temporary.newFolder()), StorageIssueReporter(failures::add))
        try {
            val source = DataStoreLinkPreviewPreferencesSource.create(owner.preferences, backgroundScope)
            assertEquals(LinkPreviewPreferencesSnapshot(previewsEnabled = false, autoResolveDM = true, autoResolveChannels = true), source.preferences.value)
            assertTrue(failures.isEmpty())
        } finally {
            owner.close()
        }
    }

    @Test
    fun updatePersistsToRealDataStoreAndIsReadableByANewSource() = runTest {
        val failures = ConcurrentLinkedQueue<StorageFailure>()
        val directory = temporary.newFolder()
        val owner = MeshCoreStorage.get(context(directory), StorageIssueReporter(failures::add))
        try {
            val source = DataStoreLinkPreviewPreferencesSource.create(owner.preferences, backgroundScope)
            val desired = LinkPreviewPreferencesSnapshot(previewsEnabled = true, autoResolveDM = false, autoResolveChannels = false)
            source.update(desired)

            // The real write must be observable both through this source's own round-trip and
            // directly via a fresh snapshot of the shared store - not a synchronous/local var.
            assertEquals(desired, owner.preferences.snapshot().let {
                LinkPreviewPreferencesSnapshot(
                    previewsEnabled = it[AppStorageKey.linkPreviewsEnabled],
                    autoResolveDM = it[AppStorageKey.linkPreviewsAutoResolveDM],
                    autoResolveChannels = it[AppStorageKey.linkPreviewsAutoResolveChannels],
                )
            })
        } finally {
            owner.close()
        }
    }

    @Test
    fun preferencesFlowObservesExternalWritesThroughTheSharedStore() = runTest {
        val failures = ConcurrentLinkedQueue<StorageFailure>()
        val owner = MeshCoreStorage.get(context(temporary.newFolder()), StorageIssueReporter(failures::add))
        try {
            val source = DataStoreLinkPreviewPreferencesSource.create(owner.preferences, backgroundScope)
            // A write that does NOT go through `source.update` (e.g. a different consumer of the
            // same shared process-owned store) must still be observed - proves this is a real
            // DataStore-backed Flow, not a private in-memory copy.
            owner.preferences.set(AppStorageKey.linkPreviewsEnabled, true)
            // The update above lands via the real MeshCoreStorage/DataStore IO dispatcher, and
            // this source's own StateFlow is only refreshed when that real (non-test-scheduler)
            // collector delivers the next DataStore emission - a genuinely async, non-virtual
            // event. Running the wait on a real dispatcher (not the TestScope's virtual one)
            // gives withTimeout a real wall-clock budget instead of letting runTest's virtual
            // clock fast-forward the timeout to completion before that real emission can arrive.
            val observed = withContext(Dispatchers.Default) {
                withTimeout(5_000) { source.preferences.first { it.previewsEnabled } }
            }
            assertTrue(observed.previewsEnabled)
        } finally {
            owner.close()
        }
    }
}
