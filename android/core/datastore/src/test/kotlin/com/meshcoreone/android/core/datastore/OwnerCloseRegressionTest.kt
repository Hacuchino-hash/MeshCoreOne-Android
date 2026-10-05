// AndroidOnly: WP-204 Deterministic close-resumption ordering around real DataStore shutdown and registry release.
package com.meshcoreone.android.core.datastore

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OwnerCloseRegressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun losingCloseCallerCannotCompleteBeforeReleaseAndRemoveTheReplacementOwner() = runTest {
        val noBackup = temporary.newFolder()
        val directory = File(noBackup, "meshcoreone-datastore")
        val app: Context = ApplicationProvider.getApplicationContext()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = noBackup
        }
        val keys = TestKeystoreAccess()
        val reporter = RecordingReporter()
        val crypto = AndroidKeystoreCryptography(keys, StorageAccess {})
        val owner = MeshCoreStorage.createOwned(
            directory, crypto, reporter, StorageAccess {}, StorageAccess {},
            StandardTestDispatcher(testScheduler), "close.order",
        )
        owner.preferences.set(AppStorageKey.replyWithQuote, true)
        owner.notificationPreferences()
        val file = File(directory, MeshCoreStorage.PREFERENCE_FILENAME)
        val committed = file.readBytes()
        val callbacks = ReverseResumeDispatcher()
        var replacement: MeshCoreStorage? = null
        var reopenFailure: StorageFailure? = null
        val first = launch(callbacks, start = CoroutineStart.UNDISPATCHED) { owner.close() }
        val second = launch(callbacks, start = CoroutineStart.UNDISPATCHED) { owner.close() }
        assertTrue(first.isActive && second.isActive, "The real process observer must keep shutdown suspended")
        second.invokeOnCompletion { failure ->
            if (failure == null) {
                try {
                    replacement = MeshCoreStorage.get(context, reporter)
                } catch (storage: StorageFailure) {
                    reopenFailure = storage
                }
            }
        }
        try {
            testScheduler.runCurrent()
            assertTrue(callbacks.runLast(), "At least one shutdown waiter must resume")
            callbacks.runAll()
            first.join()
            second.join()
            assertNull(reopenFailure, "A completed close must have released the old owner")
            val next = assertNotNull(replacement)
            assertNotSame(owner, next, "Immediate get after close must never return the closed instance")
            assertSame(next, MeshCoreStorage.get(context, reporter))
            assertTrue(next.preferences.get(AppStorageKey.replyWithQuote))
            assertContentEquals(committed, file.readBytes())
            owner.close()
            expectStorageFailure(StorageProblem.DuplicateOwner) {
                MeshCoreStorage.createOwned(
                    directory, crypto, reporter, StorageAccess {}, StorageAccess {}, Dispatchers.IO, "foreign-owner",
                )
            }
            assertTrue(next.preferences.get(AppStorageKey.replyWithQuote))
        } finally {
            callbacks.runAll()
            first.cancelAndJoin()
            second.cancelAndJoin()
            owner.close()
            replacement?.close()
        }
    }

    @Test
    fun cancelledConcurrentCloseStillWaitsForRegistryReleaseBeforeReportingCancellation() = runTest {
        val directory = temporary.newFolder()
        val keys = TestKeystoreAccess()
        val reporter = RecordingReporter()
        val crypto = AndroidKeystoreCryptography(keys, StorageAccess {})
        val owner = MeshCoreStorage.createOwned(
            directory, crypto, reporter, StorageAccess {}, StorageAccess {},
            StandardTestDispatcher(testScheduler), "close.cancel",
        )
        owner.preferences.set(AppStorageKey.hasCompletedOnboarding, true)
        owner.notificationPreferences()
        val callbacks = ReverseResumeDispatcher()
        val first = launch(callbacks, start = CoroutineStart.UNDISPATCHED) { owner.close() }
        val second = launch(callbacks, start = CoroutineStart.UNDISPATCHED) { owner.close() }
        assertTrue(first.isActive && second.isActive, "Both close callers must await the real observer shutdown")
        second.cancel()
        try {
            testScheduler.runCurrent()
            callbacks.runAll()
            first.join()
            second.join()
            assertTrue(second.isCancelled)
            val replacement = MeshCoreStorage.createOwned(
                directory, crypto, reporter, StorageAccess {}, StorageAccess {}, Dispatchers.IO, "close.cancel",
            )
            try {
                assertTrue(replacement.preferences.get(AppStorageKey.hasCompletedOnboarding))
            } finally {
                replacement.close()
            }
        } finally {
            callbacks.runAll()
            first.cancelAndJoin()
            second.cancelAndJoin()
            owner.close()
        }
    }
}

private class ReverseResumeDispatcher : CoroutineDispatcher() {
    private val queued = ConcurrentLinkedDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queued.addLast(block)
    }

    fun runLast(): Boolean = queued.pollLast()?.let { it.run(); true } ?: false
    fun runAll() {
        while (runLast()) Unit
    }
}
