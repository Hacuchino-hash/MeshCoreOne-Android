// AndroidOnly: WP-313 Native coverage of status state-holder logic without Swift cases (OCV load/save, requests, handlers, discovery, telemetry, auth path).
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.OCVPreset
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.OwnerInfoResponse
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.resolver.NodeNameMatchKind
import com.meshcoreone.android.feature.remotenodes.resolver.NodeNameResolution
import com.meshcoreone.android.feature.remotenodes.resolver.ResolvedPathHop
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.runSuspend
import com.meshcoreone.android.feature.remotenodes.support.session
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.junit.Test

class StatusStateHolderTest {
    private val clock = VirtualClock()

    private fun helper(contacts: FakeContactOcv?) = NodeStatusStateHolder(clock, FakeFaults).apply {
        configure(contactOcv = { contacts }, nodeSnapshots = { null })
    }

    // MARK: - OCV

    @Test
    fun `loading a full custom curve selects custom and loads only once`() = runSuspend {
        val custom = "4100,4000,3900,3800,3700,3600,3500,3400,3300,3200,3000"
        val contacts = FakeContactOcv(contact(ocvPreset = "custom", customOCVArrayString = custom))
        val model = helper(contacts)
        model.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        model.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        assertEquals(OCVPreset.CUSTOM, model.state.value.selectedOCVPreset)
        assertEquals(OcvCustomCurve.parse(custom), model.state.value.ocvValues)
        assertEquals(1, contacts.getCalls)
    }

    @Test
    fun `stored presets resolve with Swift's fallbacks`() {
        val liIon = OCVPreset.LI_ION.ocvArray
        assertEquals(OCVPreset.CUSTOM to liIon, NodeStatusStateHolder.resolveOcv("custom", "1,2,3"))
        assertEquals(OCVPreset.CUSTOM to liIon, NodeStatusStateHolder.resolveOcv("custom", null))
        assertEquals(OCVPreset.LI_FE_PO4 to OCVPreset.LI_FE_PO4.ocvArray, NodeStatusStateHolder.resolveOcv("liFePO4", "1"))
        assertEquals(OCVPreset.LI_ION to liIon, NodeStatusStateHolder.resolveOcv("bogus", null))
        assertEquals(OCVPreset.LI_ION to liIon, NodeStatusStateHolder.resolveOcv(null, "4190"))
    }

    @Test
    fun `missing contact keeps defaults and a failed load sets the load error`() = runSuspend {
        val missing = helper(FakeContactOcv(null))
        missing.setSelectedOCVPreset(OCVPreset.NI_MH)
        missing.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        assertEquals(OCVPreset.NI_MH, missing.state.value.selectedOCVPreset)
        assertNull(missing.state.value.ocvError)

        val failing = helper(FakeContactOcv(contact()).apply { getError = OtherFault() })
        failing.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        assertEquals(RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesStatusOcvLoadFailed), failing.state.value.ocvError)

        val disconnected = helper(null)
        disconnected.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        assertEquals(NodeStatusState(), disconnected.state.value)
    }

    @Test
    fun `saving needs a loaded contact and writes preset or custom string`() = runSuspend {
        val noContact = helper(FakeContactOcv(null))
        noContact.saveOCVSettings(OCVPreset.LTO, OCVPreset.LTO.ocvArray)
        assertEquals(RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesStatusOcvSaveNoContact), noContact.state.value.ocvError)

        val stored = contact()
        val contacts = FakeContactOcv(stored)
        val model = helper(contacts)
        model.loadOCVSettings(TEST_PUBLIC_KEY, TEST_RADIO)
        model.saveOCVSettings(OCVPreset.LTO, OCVPreset.LTO.ocvArray)
        val customValues = listOf(4100L, 4000, 3900, 3800, 3700, 3600, 3500, 3400, 3300, 3200, 3000)
        model.saveOCVSettings(OCVPreset.CUSTOM, customValues)
        assertEquals(
            listOf(
                Triple(stored.id, "lto", null),
                Triple(stored.id, "custom", "4100,4000,3900,3800,3700,3600,3500,3400,3300,3200,3000"),
            ),
            contacts.updates,
        )
        assertEquals(OCVPreset.CUSTOM, model.state.value.selectedOCVPreset)
        assertEquals(customValues, model.state.value.ocvValues)
        assertNull(model.state.value.ocvError)

        val fault = OtherFault()
        contacts.updateError = fault
        model.saveOCVSettings(OCVPreset.LTO, OCVPreset.LTO.ocvArray)
        assertEquals(
            RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_ocvsavefailed, listOf(RemoteNodesText.Failure(fault))),
            model.state.value.ocvError,
        )
        assertEquals(OCVPreset.CUSTOM, model.state.value.selectedOCVPreset, "A failed save keeps the previous curve")
    }

    // MARK: - Requests and handlers

    @Test
    fun `repeater requests adopt the session and route responses through the helper`() = runSuspend {
        val admin = FakeStatusAdmin()
        val snapshots = SnapshotServiceFake(WindowedSnapshotPersister(clock), clock)
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.configure({ admin }, { null }, { snapshots }, { 2 })
        val node = session()

        admin.statusResults += { statusResponse() }
        model.requestStatus(node)
        assertEquals(node, model.helper.state.value.session)
        assertEquals(statusResponse(), model.helper.state.value.status)
        assertEquals(100u, model.helper.fetchHistory().single().rxAirtimeSeconds)

        admin.telemetryResults += { telemetryResponse() }
        model.requestTelemetry(node)
        assertEquals(1, model.helper.state.value.cachedDataPoints.size)

        admin.ownerInfoResults += { OwnerInfoResponse("v1", "n", "owner") }
        model.requestOwnerInfo(node)
        assertTrue(model.state.value.ownerInfoLoaded)

        model.requestNeighbors(node)
        assertTrue(model.state.value.neighborsLoaded)
        assertEquals<List<Duration?>>(List(4) { 45.seconds }, admin.requestTimeouts.toList())

        admin.ownerInfoResults += { throw TimeoutFault() }
        model.requestOwnerInfo(node)
        assertEquals(TransientRetryRunner.REQUEST_TIMED_OUT, model.state.value.ownerInfoError)
        assertFalse(model.state.value.isLoadingOwnerInfo)
    }

    @Test
    fun `requests and handler changes no-op while disconnected`() = runSuspend {
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.requestStatus(session())
        model.requestNeighbors(session())
        model.registerHandlers()
        model.cleanup()
        assertNull(model.startDiscovery(session()))
        assertNull(model.helper.state.value.session)
        assertEquals(RepeaterStatusState(), model.state.value)
    }

    @Test
    fun `registered handlers ignore other nodes and pass role-specific metrics`() = runSuspend {
        val admin = FakeStatusAdmin()
        val snapshots = SnapshotServiceFake(WindowedSnapshotPersister(clock), clock)
        val repeater = RepeaterStatusStateHolder(clock, FakeFaults, this)
        repeater.configure({ admin }, { null }, { snapshots }, { null })
        repeater.helper.setSession(session())
        repeater.registerHandlers()

        admin.invokeStatusHandler(statusResponse(prefix = Bytes(ByteArray(6) { 0x11 })))
        admin.invokeNeighboursHandler(neighboursResponse(prefix = Bytes(ByteArray(6) { 0x11 })))
        assertNull(repeater.helper.state.value.status)
        assertFalse(repeater.state.value.neighborsLoaded)

        admin.invokeStatusHandler(statusResponse())
        admin.invokeTelemetryHandler(telemetryResponse())
        admin.invokeNeighboursHandler(neighboursResponse())
        assertTrue(repeater.helper.state.value.statusLoaded)
        assertTrue(repeater.helper.state.value.telemetryLoaded)
        assertTrue(repeater.state.value.neighborsLoaded)

        val roomAdmin = FakeStatusAdmin()
        val roomKey = Bytes(ByteArray(32) { 0x55 })
        val roomSnapshots = SnapshotServiceFake(WindowedSnapshotPersister(clock), clock)
        val room = RoomStatusStateHolder(clock, FakeFaults)
        room.configure({ roomAdmin }, { null }, { roomSnapshots })
        room.helper.setSession(session(publicKey = roomKey))
        room.registerHandlers()
        roomAdmin.invokeStatusHandler(statusResponse(prefix = roomKey.prefix(6)).copy(roomServerPostedCount = 1234u, roomServerPostPushCount = 7u))
        val row = room.helper.fetchHistory().single()
        assertEquals(1234.toUShort(), row.postedCount)
        assertNull(row.rxAirtimeSeconds)
        assertEquals("1,234", room.postsReceivedDisplay(Locale.US))
        assertEquals("7", room.postsPushedDisplay(Locale.US))
    }

    @Test
    fun `receive errors show only when positive`() = runSuspend {
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.helper.setSession(session())
        assertNull(model.receiveErrorsDisplay(Locale.US))
        model.helper.handleStatusResponse(statusResponse())
        assertNull(model.receiveErrorsDisplay(Locale.US))
        model.helper.handleStatusResponse(statusResponse().copy(receiveErrors = 2048u))
        assertEquals("2,048", model.receiveErrorsDisplay(Locale.US))
        assertEquals(NodeStatusDisplay.EM_DASH, RoomStatusStateHolder(clock, FakeFaults).postsReceivedDisplay(Locale.US))
    }

    // MARK: - Discovery

    @Test
    fun `discovery counts down 60 seconds and re-requests neighbours every fifth tick`() = runSuspend {
        val admin = FakeStatusAdmin()
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.configure({ admin }, { null }, { null }, { null })
        val job = assertNotNull(model.startDiscovery(session()))
        assertTrue(model.state.value.isDiscovering)
        assertEquals(60, model.state.value.discoverySecondsRemaining)
        assertNull(model.startDiscovery(session()), "A second start while discovering is ignored")
        job.join()

        assertEquals(listOf("discover.neighbors" to 10.seconds), admin.commands)
        assertEquals(12, admin.neighbourFetches)
        assertEquals(60, clock.sleeps.size)
        assertFalse(model.state.value.isDiscovering)
        assertEquals(0, model.state.value.discoverySecondsRemaining)
    }

    @Test
    fun `discovery command failure sets the neighbours error and ends discovery`() = runSuspend {
        val admin = FakeStatusAdmin()
        val fault = OtherFault()
        admin.commandResults += { throw fault }
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.configure({ admin }, { null }, { null }, { null })
        model.startDiscovery(session())?.join()
        assertEquals(RemoteNodesText.Failure(fault), model.state.value.neighborsSectionError)
        assertFalse(model.state.value.isDiscovering)
        assertEquals(0, admin.neighbourFetches)
    }

    @Test
    fun `stopping discovery cancels the loop`() = runSuspend {
        val admin = FakeStatusAdmin()
        lateinit var model: RepeaterStatusStateHolder
        admin.commandResults += { model.stopDiscovery(); "" }
        model = RepeaterStatusStateHolder(clock, FakeFaults, CoroutineScope(coroutineContext))
        model.configure({ admin }, { null }, { null }, { null })
        val job = assertNotNull(model.startDiscovery(session()))
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(model.state.value.isDiscovering)
        assertEquals(0, model.state.value.discoverySecondsRemaining)
        assertEquals(0, admin.neighbourFetches)
        assertTrue(clock.sleeps.size <= 1)
    }

    // MARK: - Chat-node telemetry

    @Test
    fun `direct telemetry maps a binary session timeout to the telemetry timeout text`() = runSuspend {
        val binary = FakeBinaryTelemetry()
        val node = contact(typeRawValue = 1u)
        val model = NodeTelemetryStateHolder(clock, FakeFaults)
        model.configure({ binary }, { null }, { null }, node)

        binary.results += { throw BinarySessionTimeoutFault() }
        model.requestTelemetry()
        assertEquals(NodeTelemetryStateHolder.TELEMETRY_TIMED_OUT, model.helper.state.value.telemetrySectionError)

        val fault = OtherFault()
        binary.results += { throw fault }
        model.requestTelemetry()
        assertEquals(RemoteNodesText.Failure(fault), model.helper.state.value.telemetrySectionError)

        binary.results += { telemetryResponse() }
        model.requestTelemetry()
        assertNull(model.helper.state.value.telemetrySectionError)
        assertTrue(model.helper.state.value.telemetryLoaded)
        assertEquals(List(3) { TEST_PUBLIC_KEY }, binary.keys)

        val disconnected = NodeTelemetryStateHolder(clock, FakeFaults)
        disconnected.configure({ null }, { null }, { null }, node)
        disconnected.requestTelemetry()
        assertFalse(disconnected.helper.state.value.isLoadingTelemetry)
        assertNull(disconnected.helper.state.value.telemetry)
    }

    // MARK: - Auth path

    @Test
    fun `auth path resolves hops against repeaters and empties on failure`() = runSuspend {
        val relay = contact(publicKey = Bytes(ByteArray(32) { if (it == 0) 0xAA.toByte() else 0x01 }), name = "Relay")
        val node = contact(outPathLength = 2u, outPath = Bytes.of(0xAA, 0xBB))
        val store = FakeHistoryStore(contacts = listOf(relay))
        val model = NodeAuthPathStateHolder()

        model.load(node, store, TEST_RADIO, null, Locale.US)
        assertEquals(
            listOf(
                ResolvedPathHop(0, "AA", NodeNameResolution("Relay", NodeNameMatchKind.EXACT)),
                ResolvedPathHop(1, "BB", null),
            ),
            model.hops.value,
        )

        store.failure = OtherFault()
        model.load(node, store, TEST_RADIO, null, Locale.US)
        assertTrue(model.hops.value.isEmpty())

        store.failure = null
        model.load(node, store, TEST_RADIO, null, Locale.US)
        model.load(node, null, TEST_RADIO, null, Locale.US)
        assertTrue(model.hops.value.isEmpty())
    }
}
