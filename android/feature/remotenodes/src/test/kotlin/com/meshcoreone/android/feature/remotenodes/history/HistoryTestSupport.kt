// AndroidOnly: WP-313 In-memory PersistenceStore stand-in and English title table for the history JVM tests.
package com.meshcoreone.android.feature.remotenodes.history

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.NeighborSnapshotEntry
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.TelemetrySnapshotEntry
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesClock
import com.meshcoreone.android.feature.remotenodes.dependencies.RemoteNodeHistoryStore
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Mirrors the `PersistenceStore` reads the overview uses: snapshots by node key ascending by timestamp
 * (optionally since a date), contacts keyed by radio + public key, discovered nodes by radio. Each read
 * can be made to throw.
 */
internal class InMemoryHistoryStore(private val clock: RemoteNodesClock) : RemoteNodeHistoryStore {
    private val snapshots = mutableListOf<NodeStatusSnapshotDTO>()
    private val contacts = LinkedHashMap<Pair<RadioId, Bytes>, ContactDTO>()
    private val discovered = mutableListOf<DiscoveredNodeDTO>()
    var snapshotError: Exception? = null
    var contactError: Exception? = null
    var contactsError: Exception? = null
    var discoveredError: Exception? = null
    var contactReads = 0
        private set

    fun saveNodeStatusSnapshot(
        nodePublicKey: Bytes,
        timestamp: Instant = clock.now,
        batteryMillivolts: UShort? = null,
        lastSNR: Double? = null,
        lastRSSI: Short? = null,
        noiseFloor: Short? = null,
        packetsSent: UInt? = null,
        packetsReceived: UInt? = null,
    ): UUID {
        val snapshot = NodeStatusSnapshotDTO(
            timestamp = timestamp, nodePublicKey = nodePublicKey, batteryMillivolts = batteryMillivolts,
            lastSNR = lastSNR, lastRSSI = lastRSSI, noiseFloor = noiseFloor,
            packetsSent = packetsSent, packetsReceived = packetsReceived,
        )
        snapshots += snapshot
        return snapshot.id
    }

    fun updateSnapshotTelemetry(id: UUID, telemetry: List<TelemetrySnapshotEntry>) =
        replace(id) { it.copy(telemetryEntries = telemetry.snapshot()) }

    fun updateSnapshotNeighbors(id: UUID, neighbors: List<NeighborSnapshotEntry>) =
        replace(id) { it.copy(neighborSnapshots = neighbors.snapshot()) }

    fun saveContact(contact: ContactDTO) {
        contacts[contact.radioId to contact.publicKey] = contact
    }

    fun saveDiscoveredNode(node: DiscoveredNodeDTO) {
        discovered += node
    }

    private fun replace(id: UUID, transform: (NodeStatusSnapshotDTO) -> NodeStatusSnapshotDTO) {
        val index = snapshots.indexOfFirst { it.id == id }
        snapshots[index] = transform(snapshots[index])
    }

    override suspend fun fetchNodeStatusSnapshots(nodePublicKey: Bytes, since: Instant?): SnapshotList<NodeStatusSnapshotDTO> {
        snapshotError?.let { throw it }
        return snapshots.filter { it.nodePublicKey == nodePublicKey && (since == null || it.timestamp >= since) }
            .sortedBy { it.timestamp }.snapshot()
    }

    override suspend fun fetchContact(radioId: RadioId, publicKey: Bytes): ContactDTO? {
        contactReads++
        contactError?.let { throw it }
        return contacts[radioId to publicKey]
    }

    override suspend fun fetchContacts(radioId: RadioId): SnapshotList<ContactDTO> {
        contactsError?.let { throw it }
        return contacts.values.filter { it.radioId == radioId }.snapshot()
    }

    override suspend fun fetchDiscoveredNodes(radioId: RadioId): SnapshotList<DiscoveredNodeDTO> {
        discoveredError?.let { throw it }
        return discovered.filter { it.radioId == radioId }.snapshot()
    }
}

/** English text (values/l10n_strings.xml) for the resource ids the history tests sort or compare. */
internal val ENGLISH_TITLES: Map<Int, String> = mapOf(
    AppRemoteNodesStrings.remoteNodesStatusSensorVoltage to "Voltage",
    AppRemoteNodesStrings.remoteNodesStatusSensorTemperature to "Temperature",
    AppRemoteNodesStrings.remoteNodesStatusSensorHumidity to "Humidity",
    AppRemoteNodesStrings.remoteNodesStatusSensorMcuTemperature to "MCU temperature",
    AppRemoteNodesStrings.remoteNodesStatusSensorBarometer to "Pressure",
)

internal val englishTitle: TitleTextResolver = { id -> ENGLISH_TITLES.getValue(id) }

internal val UTC: ZoneId = ZoneId.of("UTC")
internal val EN_US: Locale = Locale.US
