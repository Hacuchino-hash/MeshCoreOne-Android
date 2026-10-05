// AndroidOnly: WP-202 Actual repository defaults, windows, reaction/repeat bookkeeping and checked failures.
package com.meshcoreone.android.core.data.repository

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NativeMessageRoleTest : RepositoryTest() {
    @Test fun defaultNewestFiftyOffsetAndRadioChannelQueriesReturnTheExactWindowShape() = runTest {
        for (i in 1..60) {
            store.saveMessage(message(text = "dm$i", timestamp = i.toUInt(), createdAt = AT.plusSeconds(i * 6L)))
            store.saveMessage(message(contactID = null, channelIndex = 7u, text = "ch$i", timestamp = i.toUInt(),
                createdAt = AT.plusSeconds(i * 6L)))
        }
        store.saveMessage(message(RADIO_B, text = "other", timestamp = UInt.MAX_VALUE))
        assertEquals((11u..60u).toList(), store.fetchMessages(entity()).map { it.timestamp })
        assertEquals((11u..60u).toList(), store.fetchMessages(RADIO_A, 7u).map { it.timestamp })
        assertEquals(listOf(54u, 55u), store.fetchMessages(entity(), 2, 5).map { it.timestamp })
        assertEquals(listOf(54u, 55u), store.fetchMessages(RADIO_A, 7u, 2, 5).map { it.timestamp })
        assertTrue(store.fetchMessages(entity(), 0).isEmpty())
        assertTrue(store.fetchMessages(entity(), 5, 60).isEmpty())
        assertEquals(listOf(UInt.MAX_VALUE), store.fetchMessages(entity(RADIO_B)).map { it.timestamp })
    }

    @Test fun batchPreviewKeysPreserveEqualIDsOnDifferentRadioPartitions() = runTest {
        val a = message(text = "A")
        val b = a.copy(radioId = RADIO_B, text = "B")
        store.saveMessage(a)
        store.saveMessage(b)
        val previews = store.fetchLastMessages(SnapshotList.of(entity(), entity(RADIO_B)), 1)
        assertEquals("A", previews[entity()]?.single()?.text)
        assertEquals("B", previews[entity(RADIO_B)]?.single()?.text)
        val channelId = UUID.randomUUID()
        store.saveMessage(a.copy(id = UUID.randomUUID(), contactID = null, channelIndex = 1u))
        store.saveMessage(b.copy(id = UUID.randomUUID(), contactID = null, channelIndex = 1u))
        val channels = store.fetchLastChannelMessages(SnapshotList.of(
            ChannelQuery(RADIO_A, 1u, channelId), ChannelQuery(RADIO_B, 1u, channelId)), 1)
        assertEquals("A", channels[entity(RADIO_A, channelId)]?.single()?.text)
        assertEquals("B", channels[entity(RADIO_B, channelId)]?.single()?.text)
    }

    @Test fun reactionsUseNewestFirstDefaultHundredAndExactSenderEmojiDedupScope() = runTest {
        val m = message()
        store.saveMessage(m)
        val ids = mutableListOf<UUID>()
        for (i in 0..100) {
            val reaction = ReactionDTO(messageID = m.id, emoji = "\uD83D\uDC4D", senderName = "Peer$i",
                messageHash = "AABBCCDD", rawText = "reaction$i", receivedAt = AT.plusNanos(i.toLong()), radioId = RADIO_A)
            ids += reaction.id
            store.saveReaction(reaction)
        }
        val recent = store.fetchReactions(entity(id = m.id))
        assertEquals(100, recent.size)
        assertEquals(ids[100], recent.first().id)
        assertEquals(ids[1], recent.last().id)
        assertTrue(store.reactionExists(entity(id = m.id), "Peer100", "\uD83D\uDC4D"))
        assertFalse(store.reactionExists(entity(id = m.id), "peer100", "\uD83D\uDC4D"))
        assertFalse(store.reactionExists(entity(RADIO_B, m.id), "Peer100", "\uD83D\uDC4D"))
        store.updateMessageReactionSummary(entity(id = m.id), "thumb:101")
        assertEquals("thumb:101", assertNotNull(store.fetchMessage(entity(id = m.id))).reactionSummary)
        store.updateMessageReactionSummary(entity(id = m.id), null)
        assertNull(assertNotNull(store.fetchMessage(entity(id = m.id))).reactionSummary)
        store.deleteReactionsForMessage(entity(id = m.id))
        assertTrue(store.fetchReactions(entity(id = m.id)).isEmpty())
    }

    @Test fun sentChannelRepeatCorrelationUsesExactContentClockRadioAndDirection() = runTest {
        val older = message(contactID = null, channelIndex = 1u, text = "echo", timestamp = UInt.MAX_VALUE)
        val newer = older.copy(id = UUID.randomUUID(), createdAt = AT.plusNanos(1), sortDate = AT.plusNanos(1))
        store.saveMessage(older)
        store.saveMessage(newer)
        store.saveMessage(newer.copy(id = UUID.randomUUID(), direction = MessageDirection.INCOMING,
            createdAt = AT.plusNanos(2), sortDate = AT.plusNanos(2)))
        assertEquals(newer.id, store.findSentChannelMessage(RADIO_A, 1u, UInt.MAX_VALUE, "echo")?.id)
        assertNull(store.findSentChannelMessage(RADIO_B, 1u, UInt.MAX_VALUE, "echo"))
        assertNull(store.findSentChannelMessage(RADIO_A, 1u, UInt.MAX_VALUE, "Echo"))
        assertNull(store.findSentChannelMessage(RADIO_A, 2u, UInt.MAX_VALUE, "echo"))
        val rxId = UUID.randomUUID()
        val repeat = MessageRepeatDTO(messageID = newer.id, receivedAt = AT, pathNodes = Bytes.of(0x80, 0xFF),
            pathLength = 0x41u, snr = -7.5, rssi = -127, rxLogEntryID = rxId)
        store.saveMessageRepeat(RADIO_A, repeat)
        assertTrue(store.messageRepeatExists(entity(id = rxId)))
        assertFalse(store.messageRepeatExists(entity(RADIO_B, rxId)))
        assertEquals(1L, store.incrementMessageHeardRepeats(entity(id = newer.id)))
        assertEquals(2L, store.incrementMessageSendCount(entity(id = newer.id)))
        assertEquals(listOf(repeat), store.fetchMessageRepeats(entity(id = newer.id)))
        store.deleteMessageRepeats(entity(id = newer.id))
        assertTrue(store.fetchMessageRepeats(entity(id = newer.id)).isEmpty())
        assertEquals(0L, store.incrementMessageHeardRepeats(entity(id = UUID.randomUUID())))
        assertEquals(0L, store.incrementMessageSendCount(entity(id = UUID.randomUUID())))
    }

    @Test fun seenFailedSendKeysArePartitionedAndResetOnlyOnANewTransitionToFailed() = runTest {
        store.saveContact(contact())
        val channel = ChannelDTO(radioId = RADIO_A, index = 3u, name = "channel")
        store.saveChannel(channel)
        val room = session()
        store.saveRemoteNodeSessionDTO(room)
        val dm = message(status = MessageStatus.FAILED)
        val ch = message(contactID = null, channelIndex = 3u, status = MessageStatus.FAILED)
        val rm = RoomMessageDTO(sessionID = room.id, authorKeyPrefix = Bytes.EMPTY, text = "room", timestamp = 42u,
            createdAt = AT, isFromSelf = true, statusRawValue = MessageStatus.FAILED.rawValue)
        store.saveMessage(dm)
        store.saveMessage(ch)
        store.saveMessage(dm.copy(radioId = RADIO_B))
        store.saveRoomMessage(RADIO_A, rm)
        assertEquals(setOf(CONTACT_A), store.fetchFailedSendConversationKeys(RADIO_A).contactIDs)
        assertEquals(setOf(channel.id), store.fetchFailedSendConversationKeys(RADIO_A).channelIDs)
        assertEquals(setOf(room.id), store.fetchFailedSendConversationKeys(RADIO_A).roomSessionIDs)
        store.markFailedSendsSeen(entity())
        store.markFailedSendsSeen(RADIO_A, 3u)
        store.markRoomFailedSendsSeen(entity(id = room.id))
        assertEquals(FailedSendConversationKeys.EMPTY, store.fetchFailedSendConversationKeys(RADIO_A))
        assertEquals(setOf(CONTACT_A), store.fetchFailedSendConversationKeys(RADIO_B).contactIDs)
        store.updateMessageStatus(entity(id = dm.id), MessageStatus.FAILED)
        assertTrue(assertNotNull(store.fetchMessage(entity(id = dm.id))).failureSeen)
        store.updateMessageStatus(entity(id = dm.id), MessageStatus.PENDING)
        store.updateMessageStatus(entity(id = dm.id), MessageStatus.FAILED)
        assertFalse(assertNotNull(store.fetchMessage(entity(id = dm.id))).failureSeen)
        store.updateRoomMessageStatus(entity(id = rm.id), MessageStatus.PENDING, null, null)
        store.updateRoomMessageRetryStatus(entity(id = rm.id), MessageStatus.FAILED, 2, 3)
        assertFalse(assertNotNull(store.fetchRoomMessage(entity(id = rm.id))).failureSeen)
    }

    @Test fun nullablePreviewAndHighBitAckUpdatesDoNotReapplyCreationDefaults() = runTest {
        val bytes = byteArrayOf(0x80.toByte(), 0xFF.toByte())
        val m = message().copy(pathNodes = Bytes(bytes))
        store.saveMessage(m)
        bytes.fill(0)
        val k = entity(id = m.id)
        store.updateMessageLinkPreview(k, "", null, Bytes.EMPTY, Bytes.of(0x80), true)
        store.updateMessageHeardRepeats(k, 3_000_000_000L)
        store.updateMessageTimestamp(k, UInt.MAX_VALUE)
        store.markMessageAsRead(k)
        val row = assertNotNull(store.fetchMessage(k))
        assertEquals(Bytes.of(0x80, 0xFF), row.pathNodes)
        assertEquals("", row.linkPreviewURL)
        assertNull(row.linkPreviewTitle)
        assertEquals(Bytes.EMPTY, row.linkPreviewImageData)
        assertEquals(Bytes.of(0x80), row.linkPreviewIconData)
        assertTrue(row.linkPreviewFetched)
        assertEquals(3_000_000_000L, row.heardRepeats)
        assertEquals(UInt.MAX_VALUE, row.timestamp)
        assertTrue(row.isRead)
        store.updateMessageLinkPreview(k, null, "", null, Bytes.EMPTY, false)
        val cleared = assertNotNull(store.fetchMessage(k))
        assertNull(cleared.linkPreviewURL)
        assertEquals("", cleared.linkPreviewTitle)
        assertNull(cleared.linkPreviewImageData)
        assertEquals(Bytes.EMPTY, cleared.linkPreviewIconData)
        assertFalse(cleared.linkPreviewFetched)
    }

    @Test fun invalidQueryBoundsAndMissingRequiredParentsHaveTypedReportedFailures() = runTest {
        val bad = assertFailsWith<PersistenceStoreException> { store.fetchMessages(entity(), -1) }
        assertEquals(PersistenceStoreError.InvalidData, bad.error)
        val device = assertFailsWith<PersistenceStoreException> { store.updateDeviceLastContactSync(RADIO_A, 1u) }
        assertEquals(PersistenceStoreError.DeviceNotFound, device.error)
        val contact = assertFailsWith<PersistenceStoreException> { store.setContactMuted(entity(), true) }
        assertEquals(PersistenceStoreError.ContactNotFound, contact.error)
        val channel = assertFailsWith<PersistenceStoreException> { store.setChannelFavorite(entity(), true) }
        assertEquals(PersistenceStoreError.ChannelNotFound, channel.error)
        val room = assertFailsWith<PersistenceStoreException> { store.setSessionFavorite(entity(), true) }
        assertEquals(PersistenceStoreError.RemoteNodeSessionNotFound, room.error)
        assertEquals(5, issues.size)
        assertTrue(store.fetchContacts(RADIO_A).isEmpty())
        assertTrue(store.fetchChannels(RADIO_A).isEmpty())
        assertTrue(store.fetchRemoteNodeSessions(RADIO_A).isEmpty())
    }
}
