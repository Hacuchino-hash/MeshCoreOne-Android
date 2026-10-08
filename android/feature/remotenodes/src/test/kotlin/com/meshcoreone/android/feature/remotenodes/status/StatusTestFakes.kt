// AndroidOnly: WP-313 Hand-written port fakes reproducing the WP-210 handler-slot and snapshot-store semantics for the status suite.
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.NeighborBaseline
import com.meshcoreone.android.core.contracts.domain.NodeSnapshotPersisting
import com.meshcoreone.android.core.contracts.domain.fetchNeighborBaseline
import com.meshcoreone.android.core.contracts.domain.fetchPreviousStatusSnapshot
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.NeighborSnapshotEntry
import com.meshcoreone.android.core.model.NodeLocationFix
import com.meshcoreone.android.core.model.NodeSnapshotPolicy
import com.meshcoreone.android.core.model.NodeStatusMetrics
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.TelemetrySnapshotEntry
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.ContactMessage
import com.meshcoreone.android.core.protocol.event.Neighbour
import com.meshcoreone.android.core.protocol.event.NeighboursResponse
import com.meshcoreone.android.core.protocol.event.OwnerInfoResponse
import com.meshcoreone.android.core.protocol.event.StatusResponse
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.lpp.LPPEncoder
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesClock
import com.meshcoreone.android.feature.remotenodes.dependencies.BinaryTelemetryPort
import com.meshcoreone.android.feature.remotenodes.dependencies.ContactOcvPort
import com.meshcoreone.android.feature.remotenodes.dependencies.NodeSnapshotPort
import com.meshcoreone.android.feature.remotenodes.dependencies.RemoteNodeFaultClassifier
import com.meshcoreone.android.feature.remotenodes.dependencies.RemoteNodeHistoryStore
import com.meshcoreone.android.feature.remotenodes.dependencies.RepeaterAdminPort
import com.meshcoreone.android.feature.remotenodes.dependencies.RoomAdminPort
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

// MARK: - Faults

internal class TimeoutFault : Exception("service timeout")
internal class NoResponseYetFault : Exception("remote node has not answered yet")
internal class BinarySessionTimeoutFault : Exception("binary session timeout")
internal class OtherFault(message: String = "other failure") : Exception(message)

/** Classifies the marker faults above the way app wiring classifies the WP-210 errors. */
internal object FakeFaults : RemoteNodeFaultClassifier {
    override fun isTimeout(error: Throwable) = error is TimeoutFault
    override fun isRemoteNoResponseYet(error: Throwable) = error is NoResponseYetFault
    override fun isBinarySessionTimeout(error: Throwable) = error is BinarySessionTimeoutFault
}

// MARK: - Admin services

/**
 * Handler slots with the WP-210 `RepeaterAdminService` / `RoomAdminService` semantics: setters replace
 * one slot, `clearStatusHandlers` drops the status-surface slots, `clearHandlers` also drops the CLI
 * slot, and `invoke*` delivers to the current slot or ignores the response when it is empty.
 */
internal open class FakeStatusAdmin : RepeaterAdminPort, RoomAdminPort {
    @Volatile var statusSlot: (suspend (StatusResponse) -> Unit)? = null
    @Volatile var telemetrySlot: (suspend (TelemetryResponse) -> Unit)? = null
    @Volatile var neighboursSlot: (suspend (NeighboursResponse) -> Unit)? = null
    @Volatile var cliSlot: (suspend (ContactMessage, ContactDTO) -> Unit)? = null

    val statusResults = ArrayDeque<() -> StatusResponse>()
    val telemetryResults = ArrayDeque<() -> TelemetryResponse>()
    val neighboursResults = ArrayDeque<() -> NeighboursResponse>()
    val ownerInfoResults = ArrayDeque<() -> OwnerInfoResponse>()
    val commandResults = ArrayDeque<() -> String>()
    val requestTimeouts = mutableListOf<kotlin.time.Duration?>()
    val commands = mutableListOf<Pair<String, kotlin.time.Duration>>()
    var neighbourFetches = 0

    override suspend fun sendCommand(session: EntityKey, command: String, timeout: kotlin.time.Duration): String {
        commands += command to timeout
        return commandResults.removeFirstOrNull()?.invoke() ?: ""
    }

    override suspend fun sendRawCommand(session: EntityKey, command: String, timeout: kotlin.time.Duration) =
        sendCommand(session, command, timeout)

    override fun setCLIHandler(handler: suspend (ContactMessage, ContactDTO) -> Unit) {
        cliSlot = handler
    }

    override suspend fun requestStatus(session: EntityKey, timeout: kotlin.time.Duration?): StatusResponse {
        requestTimeouts += timeout
        return statusResults.removeFirst().invoke()
    }

    override suspend fun requestTelemetry(session: EntityKey, timeout: kotlin.time.Duration?): TelemetryResponse {
        requestTimeouts += timeout
        return telemetryResults.removeFirst().invoke()
    }

    override fun setStatusHandler(handler: suspend (StatusResponse) -> Unit) {
        statusSlot = handler
    }

    override fun setTelemetryHandler(handler: suspend (TelemetryResponse) -> Unit) {
        telemetrySlot = handler
    }

    override fun clearHandlers() {
        clearStatusHandlers()
        cliSlot = null
    }

    override fun clearStatusHandlers() {
        statusSlot = null
        neighboursSlot = null
        telemetrySlot = null
    }

    override suspend fun requestOwnerInfo(session: EntityKey, timeout: kotlin.time.Duration?): OwnerInfoResponse {
        requestTimeouts += timeout
        return ownerInfoResults.removeFirst().invoke()
    }

    override suspend fun fetchAllNeighbors(session: EntityKey, timeout: kotlin.time.Duration?): NeighboursResponse {
        neighbourFetches += 1
        requestTimeouts += timeout
        return neighboursResults.removeFirstOrNull()?.invoke() ?: neighboursResponse()
    }

    override fun setNeighboursHandler(handler: suspend (NeighboursResponse) -> Unit) {
        neighboursSlot = handler
    }

    suspend fun invokeStatusHandler(status: StatusResponse) = statusSlot?.invoke(status) ?: Unit
    suspend fun invokeTelemetryHandler(response: TelemetryResponse) = telemetrySlot?.invoke(response) ?: Unit
    suspend fun invokeNeighboursHandler(response: NeighboursResponse) = neighboursSlot?.invoke(response) ?: Unit
    suspend fun invokeCLIHandler(message: ContactMessage, contact: ContactDTO) = cliSlot?.invoke(message, contact) ?: Unit
}

// MARK: - Snapshots

/** `NodeSnapshotService` over a persister: same delegation and the same log-and-fallback recovery. */
internal class SnapshotServiceFake(private val store: NodeSnapshotPersisting, private val clock: RemoteNodesClock) : NodeSnapshotPort {
    override suspend fun recordSnapshot(
        nodePublicKey: Bytes,
        status: NodeStatusMetrics?,
        telemetry: SnapshotList<TelemetrySnapshotEntry>?,
        neighbors: SnapshotList<NeighborSnapshotEntry>?,
        location: NodeLocationFix?,
    ): UUID? = recovering(null) { store.recordNodeStatusSnapshot(nodePublicKey, status, telemetry, neighbors, location) }

    override suspend fun neighborBaseline(nodePublicKey: Bytes): NeighborBaseline =
        recovering(NeighborBaseline(null, com.meshcoreone.android.core.model.SnapshotSet.empty())) {
            store.fetchNeighborBaseline(nodePublicKey, Clock.fixed(clock.now, ZoneOffset.UTC))
        }

    override suspend fun previousStatusSnapshot(nodePublicKey: Bytes, before: Instant): NodeStatusSnapshotDTO? =
        recovering(null) { store.fetchPreviousStatusSnapshot(nodePublicKey, before) }

    override suspend fun fetchSnapshots(nodePublicKey: Bytes, since: Instant?): SnapshotList<NodeStatusSnapshotDTO> =
        recovering(SnapshotList.empty()) { store.fetchNodeStatusSnapshots(nodePublicKey, since) }

    private suspend fun <T> recovering(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        fallback
    }
}

/** Shared bookkeeping for the in-memory persisters; the unused legacy writes are not exercised. */
internal abstract class InMemorySnapshotPersister(protected val clock: RemoteNodesClock) : NodeSnapshotPersisting {
    protected val rows = mutableListOf<NodeStatusSnapshotDTO>()

    override suspend fun fetchNodeStatusSnapshots(nodePublicKey: Bytes, since: Instant?) = synchronized(rows) {
        rows.filter { it.nodePublicKey == nodePublicKey && (since == null || !it.timestamp.isBefore(since)) }
            .sortedBy { it.timestamp }.snapshot()
    }

    override suspend fun fetchLatestNodeStatusSnapshot(nodePublicKey: Bytes) = synchronized(rows) {
        rows.filter { it.nodePublicKey == nodePublicKey }.maxByOrNull { it.timestamp }
    }

    override suspend fun saveNodeStatusSnapshot(
        nodePublicKey: Bytes, batteryMillivolts: UShort?, lastSNR: Double?, lastRSSI: Short?, noiseFloor: Short?,
        uptimeSeconds: UInt?, rxAirtimeSeconds: UInt?, packetsSent: UInt?, packetsReceived: UInt?, receiveErrors: UInt?,
        postedCount: UShort?, postPushCount: UShort?,
    ): UUID = UUID.randomUUID()

    override suspend fun saveNodeStatusSnapshot(nodePublicKey: Bytes, status: NodeStatusMetrics): UUID = UUID.randomUUID()
    override suspend fun updateSnapshotNeighbors(id: UUID, neighbors: SnapshotList<NeighborSnapshotEntry>) = Unit
    override suspend fun updateSnapshotTelemetry(id: UUID, telemetry: SnapshotList<TelemetrySnapshotEntry>) = Unit
    override suspend fun saveTelemetryOnlySnapshot(nodePublicKey: Bytes, telemetryEntries: SnapshotList<TelemetrySnapshotEntry>) =
        UUID.randomUUID()
    override suspend fun deleteOldNodeStatusSnapshots(olderThan: Instant) = Unit
}

/**
 * The core:data `DiagnosticRepository.recordNodeStatusSnapshot` semantics: the latest row younger than
 * `NodeSnapshotPolicy.minimumInterval` is enriched, otherwise a row is inserted; status applies only to
 * a row without uptime, location only to a row without latitude; telemetry and neighbors replace.
 */
internal class WindowedSnapshotPersister(clock: RemoteNodesClock) : InMemorySnapshotPersister(clock) {
    override suspend fun recordNodeStatusSnapshot(
        nodePublicKey: Bytes, status: NodeStatusMetrics?, telemetry: SnapshotList<TelemetrySnapshotEntry>?,
        neighbors: SnapshotList<NeighborSnapshotEntry>?, location: NodeLocationFix?,
    ): UUID = synchronized(rows) {
        val now = clock.now
        val latest = rows.filter { it.nodePublicKey == nodePublicKey }.maxByOrNull { it.timestamp }
        val inWindow = latest != null && Duration.between(latest.timestamp, now) < NodeSnapshotPolicy.minimumInterval
        var dto = if (inWindow && latest != null) latest else NodeStatusSnapshotDTO(timestamp = now, nodePublicKey = nodePublicKey)
        if (status != null && dto.uptimeSeconds == null) dto = dto.applying(status)
        if (telemetry != null) dto = dto.copy(telemetryEntries = telemetry)
        if (neighbors != null) dto = dto.copy(neighborSnapshots = neighbors)
        if (location != null && dto.latitude == null) {
            dto = dto.copy(latitude = location.latitude, longitude = location.longitude, altitude = location.altitude)
        }
        rows.removeAll { it.id == dto.id }
        rows += dto
        dto.id
    }
}

/** Swift `NodeLocationCaptureTests.StoringSnapshotPersister`: every capture appends a row. */
internal class AppendingSnapshotPersister(clock: RemoteNodesClock) : InMemorySnapshotPersister(clock) {
    override suspend fun recordNodeStatusSnapshot(
        nodePublicKey: Bytes, status: NodeStatusMetrics?, telemetry: SnapshotList<TelemetrySnapshotEntry>?,
        neighbors: SnapshotList<NeighborSnapshotEntry>?, location: NodeLocationFix?,
    ): UUID = synchronized(rows) {
        val dto = NodeStatusSnapshotDTO(
            timestamp = clock.now, nodePublicKey = nodePublicKey, uptimeSeconds = status?.uptimeSeconds,
            neighborSnapshots = neighbors, telemetryEntries = telemetry,
            latitude = location?.latitude, longitude = location?.longitude, altitude = location?.altitude,
        )
        rows += dto
        dto.id
    }
}

// MARK: - Contacts, telemetry, history

internal class FakeContactOcv(var contact: ContactDTO? = null) : ContactOcvPort {
    var getError: Exception? = null
    var updateError: Exception? = null
    var getCalls = 0
    val updates = mutableListOf<Triple<UUID, String, String?>>()

    override suspend fun getContact(radioId: RadioId, publicKey: Bytes): ContactDTO? {
        getCalls += 1
        getError?.let { throw it }
        return contact?.takeIf { it.publicKey == publicKey }
    }

    override suspend fun updateContactOCVSettings(contactId: UUID, preset: String, customArray: String?) {
        updateError?.let { throw it }
        updates += Triple(contactId, preset, customArray)
    }
}

internal class FakeBinaryTelemetry(val results: ArrayDeque<() -> TelemetryResponse> = ArrayDeque()) : BinaryTelemetryPort {
    val keys = mutableListOf<Bytes>()
    override suspend fun requestTelemetry(publicKey: Bytes): TelemetryResponse {
        keys += publicKey
        return results.removeFirst().invoke()
    }
}

internal class FakeHistoryStore(
    var contacts: List<ContactDTO> = emptyList(),
    var discovered: List<DiscoveredNodeDTO> = emptyList(),
    var failure: Exception? = null,
) : RemoteNodeHistoryStore {
    override suspend fun fetchNodeStatusSnapshots(nodePublicKey: Bytes, since: Instant?) = SnapshotList.empty<NodeStatusSnapshotDTO>()
    override suspend fun fetchContact(radioId: RadioId, publicKey: Bytes): ContactDTO? {
        failure?.let { throw it }
        return contacts.firstOrNull { it.publicKey == publicKey }
    }
    override suspend fun fetchContacts(radioId: RadioId): SnapshotList<ContactDTO> {
        failure?.let { throw it }
        return contacts.snapshot()
    }
    override suspend fun fetchDiscoveredNodes(radioId: RadioId): SnapshotList<DiscoveredNodeDTO> {
        failure?.let { throw it }
        return discovered.snapshot()
    }
}

// MARK: - Fixtures

internal val TEST_PUBLIC_KEY: Bytes = Bytes(ByteArray(32) { 0x42 })

/** Swift `RepeaterStatusViewModelTests.createStatusResponse()`. */
internal fun statusResponse(prefix: Bytes = TEST_PUBLIC_KEY.prefix(6), uptime: UInt = 3600u, battery: Long = 3850) = StatusResponse(
    publicKeyPrefix = prefix, battery = battery, txQueueLength = 0, noiseFloor = -120, lastRSSI = -87,
    packetsReceived = 1000u, packetsSent = 500u, airtime = 100u, uptime = uptime, sentFlood = 0u, sentDirect = 0u,
    receivedFlood = 0u, receivedDirect = 0u, fullEvents = 0, lastSNR = 8.5, directDuplicates = 0, floodDuplicates = 0,
    rxAirtime = 100u, receiveErrors = 0u,
)

/** Swift `createTelemetryResponse()`: one 22.5 °C temperature on channel 1. */
internal fun telemetryResponse(prefix: Bytes = TEST_PUBLIC_KEY.prefix(6)): TelemetryResponse {
    val encoder = LPPEncoder()
    encoder.addTemperature(1u, 22.5)
    return TelemetryResponse(prefix, null, encoder.encode())
}

/** Swift `createNeighboursResponse()`: one neighbour 01..06, 30 s ago, 5.5 dB. */
internal fun neighboursResponse(
    prefix: Bytes = TEST_PUBLIC_KEY.prefix(6),
    neighbours: List<Neighbour> = listOf(Neighbour(Bytes.of(1, 2, 3, 4, 5, 6), 30, 5.5)),
) = NeighboursResponse(prefix, Bytes.of(0, 0, 0, 1), neighbours.size.toLong(), neighbours)

internal fun contact(
    publicKey: Bytes = TEST_PUBLIC_KEY,
    name: String = "Test Node",
    typeRawValue: UByte = 2u,
    ocvPreset: String? = null,
    customOCVArrayString: String? = null,
    outPathLength: UByte = 255u,
    outPath: Bytes = Bytes.EMPTY,
) = ContactDTO(
    radioId = TEST_RADIO, publicKey = publicKey, name = name, typeRawValue = typeRawValue, lastHeardTimestamp = null,
    ocvPreset = ocvPreset, customOCVArrayString = customOCVArrayString, outPathLength = outPathLength, outPath = outPath,
)
