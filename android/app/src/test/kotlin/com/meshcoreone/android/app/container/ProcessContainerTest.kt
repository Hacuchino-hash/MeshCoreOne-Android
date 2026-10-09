// AndroidOnly: WP-303 Native proof: process start, connection loss, foreground reconciliation and idempotent wiring over the real graph.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.PersistenceKeys
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.canonicalString
import com.meshcoreone.android.core.runtime.RuntimePreferenceValue
import com.meshcoreone.android.core.ui.StatusPillState
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProcessContainerTest : RoomProcessTest() {
    private suspend fun ContainerHarness.connect(id: UUID = UUID.randomUUID()): UUID {
        manager.connect(target(id))
        settle()
        assertReady()
        return id
    }

    @Test
    fun processStartRestoresTheLastConnectionThroughTheRealGraph() = runTest {
        val id = UUID.randomUUID()
        val radio = RadioId(UUID.randomUUID())
        store.saveDevice(DeviceDTO(id = id, radioId = radio, publicKey = key(7), nodeName = "Restored", isActive = true))
        val h = ContainerHarness(this, store)
        try {
            h.preferences.values[PersistenceKeys.LAST_CONNECTED_DEVICE_ID] = RuntimePreferenceValue.Text(id.canonicalString())
            h.preferences.values[PersistenceKeys.LAST_CONNECTED_RADIO_ID] = RuntimePreferenceValue.Text(radio.canonicalString)
            assertNull(h.sessions.current, "no service graph exists before the process connects")
            h.container.start().join()
            h.settle()
            h.assertReady()
            val live = assertNotNull(h.sessions.current)
            assertEquals(radio, live.token.radioId)
            assertEquals(id, h.container.connectionPort.lastConnectedDeviceId)
            assertNotNull(h.appState.offlineDataStore)
        } finally { h.container.close() }
    }

    @Test
    fun connectionLossTearsTheGraphDownAndTheReconnectBuildsExactlyOneNew() = runTest {
        val h = ContainerHarness(this, store)
        try {
            h.connect()
            val first = assertNotNull(h.sessions.current)
            h.links.links.last().callbacks!!.onDisconnected(IllegalStateException("link lost"))
            h.eventually("the lost graph to be torn down", virtualBudgetMillis = 0) { h.sessions.outstanding == 0 }
            assertTrue(first.isTornDown)
            assertEquals(0, first.liveJobCount)
            assertNull(h.sessions.current)
            assertTrue(h.appState.statusPillState != StatusPillState.Ready)
            h.eventually("the watchdog reconnect", virtualBudgetMillis = 120_000, virtualStepMillis = 200) {
                h.manager.connectionState == DeviceConnectionState.READY
            }
            assertEquals(1, h.sessions.outstanding, "exactly one live graph after the reconnect")
            assertTrue(h.sessions.created >= 2)
            assertTrue(assertNotNull(h.sessions.current) !== first)
        } finally { h.container.close() }
    }

    @Test
    fun rewiringTheSameGraphNeitherBumpsVersionsNorAccumulatesCollectors() = runTest {
        val h = ContainerHarness(this, store)
        try {
            h.connect()
            val version = h.appState.servicesVersion.value
            val sessionJobs = h.appState.activeSessionJobCount
            val dispatcherJobs = h.appState.messageEventDispatcher.activeJobCount
            repeat(3) { h.appState.wireServicesIfConnected() }
            h.settle()
            assertEquals(version, h.appState.servicesVersion.value, "a rewire of the same container is not a services change")
            assertEquals(sessionJobs, h.appState.activeSessionJobCount)
            assertEquals(dispatcherJobs, h.appState.messageEventDispatcher.activeJobCount)
        } finally { h.container.close() }
    }

    @Test
    fun readyToastFollowsASuccessfulSyncAndThePillNeverClaimsReadyEarlier() = runTest {
        val h = ContainerHarness(this, store)
        try {
            val seen = mutableListOf<Pair<DeviceConnectionState, StatusPillState>>()
            val watcher = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                h.manager.snapshot.collect { seen += h.manager.connectionState to h.appState.statusPillState }
            }
            h.connect()
            watcher.cancel()
            assertTrue(seen.none { (state, pill) -> pill == StatusPillState.Ready && state != DeviceConnectionState.READY },
                "Ready is never shown while the connection is not READY: $seen")
            assertTrue(h.appState.connectionUI.showReadyToast, "a clean sync ends with the ready toast")
            assertEquals(StatusPillState.Ready, h.appState.statusPillState)
            h.eventually("the toast to expire", virtualBudgetMillis = 3_000, virtualStepMillis = 100) { !h.appState.connectionUI.showReadyToast }
            assertEquals(StatusPillState.Hidden, h.appState.statusPillState)
        } finally { h.container.close() }
    }

    @Test
    fun foregroundReconciliationRunsInOrderOverTheRealRuntime() = runTest {
        val h = ContainerHarness(this, store)
        try {
            h.connect()
            assertTrue(h.appState.batteryMonitor.isRefreshLoopActive || h.appState.batteryMonitor.isBootstrapActive)
            h.appState.handleEnterBackground()
            h.settle()
            assertEquals(listOf(false), h.foregroundCalls.filter { !it }.distinct(), "the platform learned about the background")
            assertFalse(h.appState.batteryMonitor.isRefreshLoopActive, "polling stops in the background")
            assertFalse(h.appState.batteryMonitor.isBootstrapActive)

            h.appState.handleReturnToForeground()
            h.settle()
            assertEquals(true, h.foregroundCalls.last(), "the platform learned about the foreground last")
            assertTrue(h.foregroundCalls.indexOf(false) < h.foregroundCalls.lastIndexOf(true))
            assertTrue(h.appState.batteryMonitor.isRefreshLoopActive, "polling resumes on return")
            assertEquals(DeviceConnectionState.READY, h.manager.connectionState)
        } finally { h.container.close() }
    }

    @Test
    fun aBackgroundedAuthFailureDoesNotLatchAnAlert() = runTest {
        val h = ContainerHarness(this, store)
        try {
            h.container.foreground.activityStarted()
            h.container.foreground.activityStopped()
            assertFalse(h.container.foreground.isForeground)
            h.appState.handleAuthenticationFailure(UUID.randomUUID(), isAppActive = h.container.foreground.isForeground)
            assertFalse(h.appState.connectionUI.showingConnectionFailedAlert)
            h.container.foreground.activityStarted()
            h.appState.handleAuthenticationFailure(UUID.randomUUID(), isAppActive = h.container.foreground.isForeground)
            assertTrue(h.appState.connectionUI.showingConnectionFailedAlert)
        } finally { h.container.close() }
    }

    @Test
    fun closingTheProcessTearsDownTheLiveGraphAndLeavesNothingRunning() = runTest {
        val h = ContainerHarness(this, store)
        h.connect()
        val live = assertNotNull(h.sessions.current)
        h.container.close()
        assertTrue(live.isTornDown)
        assertEquals(0, h.sessions.outstanding)
        assertEquals(0, live.liveJobCount)
        assertTrue(h.links.radios.all { it.closes == 1 && it.readers == 0 })
    }
}
