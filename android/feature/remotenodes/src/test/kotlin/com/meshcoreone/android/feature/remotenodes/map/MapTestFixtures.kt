// AndroidOnly: WP-313 Shared map-data fixtures (Swift NeighborSNRMapBuilderTests / LocationPathMapBuilderTests helpers).
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.Neighbour
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.remotenodes.support.EPOCH
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import com.meshcoreone.android.feature.remotenodes.support.bytes
import com.meshcoreone.android.feature.remotenodes.support.session
import java.time.Duration
import java.util.UUID

internal const val KEY_SIZE = 32

internal val EXACT_PREFIX = listOf(0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6)
internal val SECOND_EXACT_PREFIX = listOf(0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6)

internal fun prefixBytes(prefix: List<Int>): Bytes = Bytes.of(*prefix.toIntArray())

internal fun paddedKey(prefix: List<Int>): Bytes = prefixBytes(prefix) + bytes(KEY_SIZE - prefix.size, 0)

/** Swift `makeSession`: a repeater whose key starts C0...C5. */
internal fun centerSession(latitude: Double, longitude: Double, name: String = "Center"): RemoteNodeSessionDTO =
    session(
        publicKey = paddedKey(listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC5)),
        name = name,
        latitude = latitude,
        longitude = longitude,
    )

/** Swift `makeContact`: a repeater contact advertised at timestamp 100. */
internal fun contact(
    prefix: List<Int>,
    name: String,
    latitude: Double,
    longitude: Double,
    isFavorite: Boolean = false,
): ContactDTO = ContactDTO(
    id = UUID.nameUUIDFromBytes(("contact-$name").toByteArray()),
    radioId = TEST_RADIO,
    publicKey = paddedKey(prefix),
    name = name,
    typeRawValue = ContactType.REPEATER.rawValue,
    flags = 0u,
    outPathLength = 0u,
    outPath = Bytes.EMPTY,
    lastAdvertTimestamp = 100u,
    latitude = latitude,
    longitude = longitude,
    lastModified = 0u,
    lastHeardTimestamp = null,
    nickname = null,
    isBlocked = false,
    isMuted = false,
    isFavorite = isFavorite,
    lastMessageDate = null,
    unreadCount = 0,
)

/** Swift `makeDiscoveredNode`: a repeater advert heard now. */
internal fun discoveredNode(prefix: List<Int>, name: String, latitude: Double, longitude: Double): DiscoveredNodeDTO =
    DiscoveredNodeDTO(
        id = UUID.nameUUIDFromBytes(("discovered-$name").toByteArray()),
        radioId = TEST_RADIO,
        publicKey = paddedKey(prefix),
        name = name,
        typeRawValue = ContactType.REPEATER.rawValue,
        lastHeard = EPOCH,
        lastAdvertTimestamp = 100u,
        latitude = latitude,
        longitude = longitude,
        outPathLength = 0u,
        outPath = Bytes.EMPTY,
        inboundHopCount = null,
        inboundHopAdvertTimestamp = null,
    )

/** Swift `makeNeighbor`. */
internal fun neighbor(prefix: List<Int>, snr: Double = -3.0): Neighbour = Neighbour(prefixBytes(prefix), 0, snr)

/** Swift `LocationPathMapBuilderTests.snapshot`: a snapshot [offsetMinutes] from a fixed base. */
internal fun locationSnapshot(
    offsetMinutes: Double,
    latitude: Double?,
    longitude: Double?,
    altitude: Double? = null,
    id: UUID = UUID.nameUUIDFromBytes("snapshot-$offsetMinutes".toByteArray()),
): NodeStatusSnapshotDTO = NodeStatusSnapshotDTO(
    id = id,
    timestamp = EPOCH.plus(Duration.ofMillis((offsetMinutes * 60_000).toLong())),
    nodePublicKey = bytes(KEY_SIZE, 0xDD),
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
)

/** Deterministic stand-in for Swift `UUID()`: 00000000-0000-0000-0000-00000000000N in call order. */
internal class SequentialIds {
    private var next = 0L
    fun next(): UUID = UUID(0L, ++next)
}
