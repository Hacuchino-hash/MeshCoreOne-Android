// AndroidOnly: WP-311 Source-equivalent DTO fixtures (the Swift tests' createContact/makeNode/fixture helpers).
package com.meshcoreone.android.feature.nodes.support

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

internal object Fixtures {
    fun radio(): RadioId = RadioId(UUID.randomUUID())

    fun randomKey(): Bytes = Bytes(Random.nextBytes(ProtocolLimits.PUBLIC_KEY_SIZE))

    fun key(vararg prefix: Int, fill: Int = 0x00): Bytes =
        Bytes(ByteArray(ProtocolLimits.PUBLIC_KEY_SIZE) { index -> (if (index < prefix.size) prefix[index] else fill).toByte() })

    fun repeated(byte: Int): Bytes = Bytes(ByteArray(ProtocolLimits.PUBLIC_KEY_SIZE) { byte.toByte() })

    fun device(radio: RadioId = radio(), pathHashMode: UByte = 0u, publicKey: Bytes = repeated(0x5A)): DeviceDTO =
        DeviceDTO(radioId = radio, publicKey = publicKey, nodeName = "Device", pathHashMode = pathHashMode, maxContacts = 350u)

    /** `createContact` in ContactsViewModelTests. */
    fun contact(
        id: UUID = UUID.randomUUID(),
        radioId: RadioId = radio(),
        publicKey: Bytes? = null,
        name: String = "TestContact",
        type: ContactType = ContactType.CHAT,
        isFavorite: Boolean = false,
        isBlocked: Boolean = false,
        lastAdvertTimestamp: UInt = 0u,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        lastModified: UInt = 0u,
        outPathLength: UByte = 0u,
        outPath: Bytes = Bytes.EMPTY,
        nickname: String? = null,
    ): ContactDTO = ContactDTO(
        id = id, radioId = radioId, publicKey = publicKey ?: randomKey(), name = name, typeRawValue = type.rawValue,
        flags = 0u, outPathLength = outPathLength, outPath = outPath, lastAdvertTimestamp = lastAdvertTimestamp,
        latitude = latitude, longitude = longitude, lastModified = lastModified, lastHeardTimestamp = null,
        nickname = nickname, isBlocked = isBlocked, isMuted = false, isFavorite = isFavorite, lastMessageDate = null, unreadCount = 0,
    )

    /** `ContactDTO.fixture` in PathManagementViewModelEditingTests (repeater, 0xAA key, flood path). */
    fun pathContact(
        name: String = "Test Repeater",
        publicKey: Bytes = repeated(0xAA),
        type: ContactType = ContactType.REPEATER,
        isFavorite: Boolean = false,
        radioId: RadioId = radio(),
        outPathLength: UByte = 0xFFu,
        outPath: Bytes = Bytes.EMPTY,
    ): ContactDTO = contact(
        radioId = radioId, publicKey = publicKey, name = name, type = type, isFavorite = isFavorite,
        outPathLength = outPathLength, outPath = outPath,
    )

    /** `makeNode` in DiscoveryViewModelTests. */
    fun node(
        id: UUID = UUID.randomUUID(),
        radioId: RadioId = radio(),
        publicKey: Bytes = repeated(0xAB),
        name: String = "Node",
        type: ContactType = ContactType.CHAT,
        lastHeard: Instant = Instant.ofEpochSecond(1_700_000_000),
        lastAdvertTimestamp: UInt = 1_700_000_000u,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        outPathLength: UByte = 0u,
        outPath: Bytes = Bytes.EMPTY,
        inboundHopCount: Long? = null,
    ): DiscoveredNodeDTO = DiscoveredNodeDTO(
        id, radioId, publicKey, name, type.rawValue, lastHeard, lastAdvertTimestamp, latitude, longitude,
        outPathLength, outPath, inboundHopCount, null,
    )

    /** `DiscoveredNodeDTO.fixture` (repeater, 0xBB key, flood path). */
    fun discoveredRepeater(name: String = "Test Discovered", publicKey: Bytes = repeated(0xBB), radioId: RadioId = radio()): DiscoveredNodeDTO =
        node(radioId = radioId, publicKey = publicKey, name = name, type = ContactType.REPEATER, lastHeard = Instant.EPOCH,
            lastAdvertTimestamp = 0u, outPathLength = 0xFFu)
}
