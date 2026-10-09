// AndroidOnly: WP-314 Node discovery scan/add logic on fixture responses (the source has no NodeDiscoveryViewModel tests); lives under the trace test path.
package com.meshcoreone.android.feature.tools.trace

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.ContactFrame
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.DiscoverResponse
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.tools.discovery.AddContactFailure
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryContactAdder
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryDirectory
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryFeatureDependencies
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryFilter
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoverySession
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoverySortOrder
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryStateHolder
import com.meshcoreone.android.feature.tools.discovery.NodeDiscoveryStrings
import java.io.IOException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NodeDiscoveryStateHolderTest {
    private class ContactTableFull : Exception("full")

    private class FakeSession : NodeDiscoverySession {
        val requests = mutableListOf<Pair<UByte, Boolean>>()
        var tag: UInt = 0x11223344u
        var failure: Exception? = null
        private val subscribers = mutableListOf<Channel<MeshEvent>>()

        override suspend fun sendNodeDiscoverRequest(filter: UByte, prefixOnly: Boolean): UInt {
            requests += filter to prefixOnly
            failure?.let { throw it }
            return tag
        }

        override fun events(): Flow<MeshEvent> {
            val channel = Channel<MeshEvent>(Channel.UNLIMITED)
            subscribers += channel
            return flow { for (event in channel) emit(event) }
        }

        fun emit(event: MeshEvent) = subscribers.forEach { it.trySend(event) }
    }

    private class Deps : NodeDiscoveryFeatureDependencies {
        var session: NodeDiscoverySession? = null
        var contacts: List<ContactDTO> = emptyList()
        var nodes: List<DiscoveredNodeDTO> = emptyList()
        var adder: NodeDiscoveryContactAdder? = null
        var maxContacts: UShort? = null
        override fun session() = session
        override fun directory() = object : NodeDiscoveryDirectory {
            override suspend fun fetchDiscoveredNodes(radioId: RadioId) = nodes
            override suspend fun fetchContacts(radioId: RadioId) = contacts
        }
        override fun radioId() = RADIO
        override fun contactAdder() = adder
        override fun maxContacts() = maxContacts
        override fun classifyAddFailure(error: Exception) =
            if (error is ContactTableFull) AddContactFailure.CONTACT_TABLE_FULL else AddContactFailure.OTHER
    }

    /** English copy from en.lproj Tools/Contacts strings. */
    private object Strings : NodeDiscoveryStrings {
        override fun filterTitle(filter: NodeDiscoveryFilter) = if (filter == NodeDiscoveryFilter.REPEATERS) "Repeaters" else "Sensors"
        override fun notConnectedDescription(filterTitle: String) = "Connect to a mesh radio to discover $filterTitle."
        override val unknownNode = "Unknown"
        override fun nodeListFull(maxContacts: Int) = "Node list is full (max $maxContacts nodes)"
        override val nodeListFullSimple = "Node list is full"
        override fun userFacingMessage(error: Exception) = "failed: ${error.message}"
    }

    private val main = MainQueue()
    private val time = VirtualTime(T0, main)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + main)
    private val diagnostics = RecordingDiagnostics()
    private val holder = NodeDiscoveryStateHolder(scope, time, Strings, diagnostics)
    private val session = FakeSession()
    private val deps = Deps().also { it.session = session }
    private val state get() = holder.state.value

    init {
        holder.configure(deps)
    }

    private fun response(publicKey: Bytes, snr: Double, tag: Bytes = Bytes.of(0x44, 0x33, 0x22, 0x11)) =
        MeshEvent.DiscoverResponse(DiscoverResponse(0x02u, 1.5, snr, -90, 0u, tag, publicKey))

    private fun startScan() {
        holder.scan()
        main.runCurrent()
    }

    @Test
    fun `scanning while disconnected explains which filter needs a radio`() {
        deps.session = null
        holder.setFilter(NodeDiscoveryFilter.SENSORS)
        holder.scan()
        assertEquals("Connect to a mesh radio to discover Sensors.", state.errorMessage)
        assertFalse(state.isScanning)
    }

    @Test
    fun `a scan collects tagged responses, names them and ends after 15 s`() {
        deps.nodes = listOf(discovered(key(0x01), "Discovered One"))
        deps.contacts = listOf(contact(0x02, name = "Saved Two"))
        startScan()
        assertTrue(state.isScanning)
        assertEquals(listOf(0x04u.toUByte() to false), session.requests)
        session.emit(response(key(0x01), 5.0))
        session.emit(response(key(0x02), 7.0))
        session.emit(response(key(0x09, 0xAB, 0xCD, 0xEF), 1.0))
        session.emit(response(key(0x03), 9.0, tag = Bytes.of(0, 0, 0, 0)))
        session.emit(response(key(0x01), 6.0))
        main.runCurrent()
        assertEquals(listOf("Discovered One", "Saved Two", "Unknown (09ABCDEF)"), state.results.map { it.name })
        assertEquals(6.0, state.results[0].snr)
        assertTrue(state.isAdded(key(0x02)))
        time.advanceBy(14_999.milliseconds)
        assertTrue(state.isScanning)
        time.advanceBy(1.milliseconds)
        assertFalse(state.isScanning)
        assertEquals(1, state.scanSuccessHapticTrigger)
        assertEquals(0, state.scanEmptyHapticTrigger)
        assertEquals(1, state.scanStartHapticTrigger)
    }

    @Test
    fun `an empty scan ends with the empty trigger`() {
        startScan()
        time.advanceBy(15.seconds)
        assertFalse(state.isScanning)
        assertEquals(1, state.scanEmptyHapticTrigger)
    }

    @Test
    fun `stopping or restarting finishes once and a superseded scan never ends its successor`() {
        startScan()
        holder.stopScan()
        main.runCurrent()
        assertFalse(state.isScanning)
        assertEquals(1, state.scanEmptyHapticTrigger)
        startScan()
        startScan()
        assertTrue(state.isScanning)
        assertEquals(2, state.scanEmptyHapticTrigger)
        time.advanceBy(15.seconds)
        assertFalse(state.isScanning)
        assertEquals(3, state.scanEmptyHapticTrigger)
    }

    @Test
    fun `a send failure surfaces the user-facing message and ends the scan`() {
        session.failure = IOException("radio busy")
        startScan()
        assertEquals("failed: radio busy", state.errorMessage)
        assertFalse(state.isScanning)
        assertEquals("nodeDiscovery", diagnostics.failures.single().first)
    }

    @Test
    fun `a rescan replaces only the current filter's results`() {
        startScan()
        session.emit(response(key(0x01), 5.0))
        time.advanceBy(15.seconds)
        holder.setFilter(NodeDiscoveryFilter.SENSORS)
        startScan()
        assertEquals(listOf(0x04u.toUByte() to false, 0x10u.toUByte() to false), session.requests)
        assertEquals(1, state.results.size)
        assertTrue(state.sortedResults().isEmpty())
    }

    @Test
    fun `results sort by signal or by case-insensitive name`() {
        deps.nodes = listOf(discovered(key(0x01), "bravo"), discovered(key(0x02), "Charlie"), discovered(key(0x03), "alpha"))
        startScan()
        session.emit(response(key(0x01), 1.0))
        session.emit(response(key(0x02), 9.0))
        session.emit(response(key(0x03), 5.0))
        main.runCurrent()
        assertEquals(listOf(9.0, 5.0, 1.0), state.sortedResults(Locale.US).map { it.snr })
        holder.setSortOrder(NodeDiscoverySortOrder.NAME)
        assertEquals(listOf("alpha", "bravo", "Charlie"), state.sortedResults(Locale.US).map { it.name })
    }

    @Test
    fun `adding a node saves a flood-routed contact frame and marks it added`() {
        val frames = mutableListOf<ContactFrame>()
        deps.adder = NodeDiscoveryContactAdder { _, frame -> frames += frame }
        startScan()
        session.emit(MeshEvent.DiscoverResponse(DiscoverResponse(0x10u, 1.0, 2.0, -80, 0u, Bytes.of(0x44, 0x33, 0x22, 0x11), key(0x07))))
        main.runCurrent()
        holder.addNode(state.results.single())
        assertEquals(key(0x07), state.addingPublicKey)
        main.runCurrent()
        val frame = frames.single()
        assertEquals(ContactType.REPEATER, frame.type)
        assertEquals(0xFFu.toUByte(), frame.outPathLength)
        assertTrue(frame.outPath.isEmpty)
        assertEquals(0u, frame.lastAdvertTimestamp)
        assertTrue(state.isAdded(key(0x07)))
        assertEquals(1, state.addSuccessHapticTrigger)
        assertNull(state.addingPublicKey)
    }

    @Test
    fun `a full node table reports the capacity when known`() {
        deps.adder = NodeDiscoveryContactAdder { _, _ -> throw ContactTableFull() }
        startScan()
        session.emit(response(key(0x05), 3.0))
        main.runCurrent()
        val result = state.results.single()
        deps.maxContacts = 350u
        holder.addNode(result)
        main.runCurrent()
        assertEquals("Node list is full (max 350 nodes)", state.errorMessage)
        deps.maxContacts = null
        holder.addNode(result)
        main.runCurrent()
        assertEquals("Node list is full", state.errorMessage)
        deps.adder = NodeDiscoveryContactAdder { _, _ -> throw IOException("timeout") }
        holder.addNode(result)
        main.runCurrent()
        assertEquals("failed: timeout", state.errorMessage)
        assertEquals(3, state.addErrorHapticTrigger)
        assertFalse(state.isAdded(key(0x05)))
    }

    @Test
    fun `the request tag matches responses as four little-endian bytes`() =
        assertEquals(Bytes.of(0x44, 0x33, 0x22, 0x11), NodeDiscoveryStateHolder.littleEndian(0x11223344u))
}
