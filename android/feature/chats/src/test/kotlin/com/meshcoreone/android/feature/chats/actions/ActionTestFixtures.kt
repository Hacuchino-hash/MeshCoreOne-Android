// AndroidOnly: WP-309 Deterministic fixtures for the authorized actions/details test package.
package com.meshcoreone.android.feature.chats.actions

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.model.MessageRepeatDTO
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.ReactionDTO
import com.meshcoreone.android.core.model.TextType
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.RouteType
import com.meshcoreone.android.core.protocol.model.ContactType
import java.time.Instant
import java.util.UUID

internal val TEST_RADIO = RadioId(UUID.fromString("00000000-0000-0000-0000-000000000309"))
internal val TEST_TIME: Instant = Instant.ofEpochSecond(1_700_000_000)

internal fun message(
    direction: MessageDirection = MessageDirection.INCOMING,
    channelIndex: UByte? = null,
    pathLength: UByte = 2u,
    pathNodes: Bytes? = Bytes.of(0xA3, 0x7F),
    routeType: RouteType? = null,
    senderKeyPrefix: Bytes? = null,
    senderNodeName: String? = if (channelIndex != null) "RemoteNode" else null,
    heardRepeats: Long = 0,
    status: MessageStatus = MessageStatus.DELIVERED,
    id: UUID = UUID.randomUUID(),
) = MessageDTO(
    id = id,
    radioId = TEST_RADIO,
    contactID = if (channelIndex == null) UUID.randomUUID() else null,
    channelIndex = channelIndex,
    text = "Test",
    timestamp = 1_700_000_000u,
    createdAt = TEST_TIME,
    direction = direction,
    status = status,
    textType = TextType.PLAIN,
    pathLength = pathLength,
    pathNodes = pathNodes,
    senderKeyPrefix = senderKeyPrefix,
    senderNodeName = senderNodeName,
    heardRepeats = heardRepeats,
    routeType = routeType,
)

internal fun repeat(
    messageId: UUID,
    pathNodes: Bytes = Bytes.of(0xA3),
    pathLength: UByte = 1u,
    receivedAt: Instant = TEST_TIME,
    snr: Double? = 7.0,
    rssi: Long? = -80,
) = MessageRepeatDTO(
    messageID = messageId,
    receivedAt = receivedAt,
    pathNodes = pathNodes,
    pathLength = pathLength,
    snr = snr,
    rssi = rssi,
)

internal fun contact(
    first: Int,
    second: Int = 0,
    name: String = "Node",
    type: ContactType = ContactType.REPEATER,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    isBlocked: Boolean = false,
    lastAdvert: UInt = 0u,
) = ContactDTO(
    radioId = TEST_RADIO,
    publicKey = Bytes.of(first, second, *IntArray(30)),
    name = name,
    typeRawValue = type.rawValue,
    lastAdvertTimestamp = lastAdvert,
    latitude = latitude,
    longitude = longitude,
    lastHeardTimestamp = 0u,
    isBlocked = isBlocked,
)

internal fun discovered(
    first: Int,
    second: Int = 0,
    name: String = "Discovered",
    latitude: Double = 38.0,
    longitude: Double = -122.5,
) = DiscoveredNodeDTO(
    id = UUID.randomUUID(),
    radioId = TEST_RADIO,
    publicKey = Bytes.of(first, second, *IntArray(30)),
    name = name,
    typeRawValue = ContactType.REPEATER.rawValue,
    lastHeard = TEST_TIME,
    lastAdvertTimestamp = 0u,
    latitude = latitude,
    longitude = longitude,
    outPathLength = 0u,
    outPath = Bytes.EMPTY,
    inboundHopCount = null,
    inboundHopAdvertTimestamp = null,
)

internal fun reaction(
    messageId: UUID,
    emoji: String,
    sender: String,
    at: Instant = TEST_TIME,
) = ReactionDTO(
    messageID = messageId,
    emoji = emoji,
    senderName = sender,
    messageHash = "a1b2c3d4",
    rawText = emoji,
    receivedAt = at,
    radioId = TEST_RADIO,
)
