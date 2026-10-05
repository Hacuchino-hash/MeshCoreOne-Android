// PortedFrom: MC1/Tips/DeviceMenuTip.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Tips/LiveActivityTip.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: actual DataStore concurrency, persistence, cancellation and process-lifetime assertions.
package com.meshcoreone.android.core.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.datastore.MeshCoreStorage
import com.meshcoreone.android.core.datastore.PreferenceKey
import com.meshcoreone.android.core.datastore.StorageFailure
import com.meshcoreone.android.core.datastore.StorageIssueReporter
import com.meshcoreone.android.core.datastore.StorageProblem
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShowOnceTipsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val connected = TipHostState(true, true, false)
    private val status = TipHostState(false, true, true)

    private class Harness(val directory: File) {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
        val reported = ConcurrentLinkedQueue<StorageFailure>()
        val storage = MeshCoreStorage.get(context, StorageIssueReporter(reported::add))
        val tips = ShowOnceTips(storage.preferences)
        suspend fun close() = storage.close()
    }

    @Test fun missingDonationsAndDisconnectedOrInvisibleHostsDoNotConsumeOrCreateAShownDefault() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            assertFalse(h.tips.claim(SharedTip.DEVICE_MENU) { connected })
            assertFalse(h.storage.preferences.snapshot().contains(SharedTip.DEVICE_MENU.displayedKey))
            h.tips.donateCompletedOnboarding()
            assertFalse(h.tips.claim(SharedTip.DEVICE_MENU) { connected.copy(connectedDevicePresent = false) })
            assertFalse(h.tips.claim(SharedTip.DEVICE_MENU) { connected.copy(deviceMenuVisible = false) })
            assertFalse(h.storage.preferences.snapshot().contains(SharedTip.DEVICE_MENU.displayedKey))
            assertTrue(h.tips.claim(SharedTip.DEVICE_MENU) { connected })
        } finally { h.close() }
    }

    @Test fun thirtyTwoIndependentConsumersHaveExactlyOneRealAtomicDurableClaim() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            h.tips.donateCompletedOnboarding()
            val claims = coroutineScope {
                List(32) { async(Dispatchers.Default) {
                    ShowOnceTips(h.storage.preferences).claim(SharedTip.DEVICE_MENU) { connected }
                } }.awaitAll()
            }
            assertEquals(1, claims.count { it })
            assertTrue(h.storage.preferences.snapshot().contains(SharedTip.DEVICE_MENU.displayedKey))
            assertTrue(h.tips.hasDisplayed(SharedTip.DEVICE_MENU))
        } finally { h.close() }
    }

    @Test fun claimsPersistAcrossRealOwnerCloseReopenAndAnotherRadioWithoutClosingProcessPreferencesFromUi() = runBlocking {
        val directory = temporary.newFolder()
        val first = Harness(directory)
        first.tips.donateCompletedOnboarding()
        assertTrue(first.tips.claim(SharedTip.DEVICE_MENU) { connected })
        first.close()
        val reopened = Harness(directory)
        try {
            assertTrue(reopened.tips.hasDisplayed(SharedTip.DEVICE_MENU))
            assertFalse(reopened.tips.claim(SharedTip.DEVICE_MENU) { connected })
            reopened.storage.preferences.set(PreferenceKey.StringKey("wp304.persistence.probe", null), "survived")
            assertEquals("survived", reopened.storage.preferences.get(PreferenceKey.StringKey("wp304.persistence.probe", null)))
        } finally { reopened.close() }
    }

    @Test fun liveStatusRequiresItsActualPresentedHostAndDoesNotInferPromotedLiveUpdateEligibility() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            assertFalse(h.tips.claim(SharedTip.LIVE_STATUS) { status })
            h.tips.donateRadioConnected()
            assertFalse(h.tips.claim(SharedTip.LIVE_STATUS) { status.copy(liveStatusPresented = false) })
            assertTrue(h.tips.claim(SharedTip.LIVE_STATUS) { status })
            assertFalse(h.tips.claim(SharedTip.LIVE_STATUS) { status })
        } finally { h.close() }
    }

    @Test fun anActiveTaskOrAnotherOverlayDoesNotConsumeTheTip() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            h.tips.donateCompletedOnboarding()
            assertFalse(h.tips.claim(SharedTip.DEVICE_MENU) { connected.copy(taskInProgress = true) })
            assertFalse(h.tips.claim(SharedTip.DEVICE_MENU) { connected.copy(overlayQuiescent = false) })
            assertFalse(h.tips.hasDisplayed(SharedTip.DEVICE_MENU))
        } finally { h.close() }
    }

    @Test fun cancellationPropagatesAndDoesNotTurnAFailedClaimIntoAShownDefault() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            h.tips.donateCompletedOnboarding()
            assertFailsWith<CancellationException> {
                h.tips.claim(SharedTip.DEVICE_MENU) { throw CancellationException("cancelled synthetic host") }
            }
            assertFalse(h.storage.preferences.snapshot().contains(SharedTip.DEVICE_MENU.displayedKey))
            assertTrue(h.tips.claim(SharedTip.DEVICE_MENU) { connected })
        } finally { h.close() }
    }

    @Test fun wrongPersistedFlagTypeIsReportedAndNeverRecreatedAsFalse() = runBlocking {
        val h = Harness(temporary.newFolder())
        try {
            h.tips.donateCompletedOnboarding()
            h.storage.preferences.set(PreferenceKey.IntegerKey(SharedTip.DEVICE_MENU.displayedKey.rawValue, 0), 1)
            val failure = assertFailsWith<StorageFailure> { h.tips.claim(SharedTip.DEVICE_MENU) { connected } }
            assertIs<StorageProblem.PreferenceTypeMismatch>(failure.problem)
            assertTrue(h.reported.isNotEmpty())
            assertEquals(1L, h.storage.preferences.get(PreferenceKey.IntegerKey(SharedTip.DEVICE_MENU.displayedKey.rawValue, 0)))
        } finally { h.close() }
    }

    @Test fun closedOwnerHasATypedFailureRatherThanAnInMemoryTipStore() = runBlocking {
        val h = Harness(temporary.newFolder())
        h.close()
        val failure = assertFailsWith<StorageFailure> { h.tips.hasDisplayed(SharedTip.DEVICE_MENU) }
        assertEquals(StorageProblem.OwnerClosed, failure.problem)
        assertTrue(h.reported.isNotEmpty())
    }
}
