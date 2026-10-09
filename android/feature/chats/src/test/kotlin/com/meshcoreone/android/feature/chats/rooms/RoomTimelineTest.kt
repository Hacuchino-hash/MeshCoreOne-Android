// PortedFrom: MC1Tests/ViewModels/RoomConversationViewModelOrderingTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class RoomTimelineTest {
    private val sessionId = UUID.randomUUID()
    private val alice = Bytes(byteArrayOf(0xAA.toByte()))

    private fun message(ts: UInt, id: UUID = UUID.randomUUID(), text: String = "msg") = RoomMessageDTO(
        id = id, sessionID = sessionId, authorKeyPrefix = Bytes(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01)),
        authorName = "Author", text = text, timestamp = ts,
    )

    private fun roomMessage(ts: UInt, prefix: Bytes, name: String, isFromSelf: Boolean = false) = RoomMessageDTO(
        sessionID = sessionId, authorKeyPrefix = prefix, authorName = name, text = "msg", timestamp = ts, isFromSelf = isFromSelf,
    )

    private fun insert(existing: List<RoomMessageDTO>, new: RoomMessageDTO) = RoomTimeline.insertedInOrder(existing, new)

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::out-of-order live message inserts into the middle()")
    fun `out-of-order live message inserts into the middle`() {
        val result = insert(listOf(message(100u), message(200u), message(300u)), message(150u))
        assertEquals(listOf<UInt>(100u, 150u, 200u, 300u), result.map { it.timestamp })
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::message older than all inserts at the front()")
    fun `message older than all inserts at the front`() {
        assertEquals(listOf<UInt>(100u, 200u, 300u), insert(listOf(message(200u), message(300u)), message(100u)).map { it.timestamp })
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::newest message inserts at the tail()")
    fun `newest message inserts at the tail`() {
        assertEquals(listOf<UInt>(100u, 200u, 300u), insert(listOf(message(100u), message(200u)), message(300u)).map { it.timestamp })
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::equal-timestamp message inserts after existing same-timestamp messages()")
    fun `equal-timestamp message inserts after existing same-timestamp messages`() {
        val first = message(200u, text = "first")
        val second = message(200u, text = "second")
        val before = listOf(message(100u), first)
        val result = insert(before, second)
        assertEquals(listOf(before[0].id, first.id, second.id), result.map { it.id })
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::duplicate id is ignored()")
    fun `duplicate id is ignored`() {
        val existing = message(200u)
        val result = insert(listOf(message(100u), existing), message(150u, id = existing.id))
        assertEquals(listOf<UInt>(100u, 200u), result.map { it.timestamp })
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::same-prefix burst books name on first and avatar on last()")
    fun `same-prefix burst books name on first and avatar on last`() {
        val messages = listOf(roomMessage(100u, alice, "Alice"), roomMessage(160u, alice, "Alice"), roomMessage(220u, alice, "Alice"))
        val bookends = RoomTimeline.incomingBookends(messages)
        assertEquals(setOf(messages[0].id), bookends.nameIds)
        assertEquals(setOf(messages[2].id), bookends.avatarIds)
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::different prefixes are two clusters even when display names match()")
    fun `different prefixes are two clusters even when display names match`() {
        val messages = listOf(roomMessage(100u, alice, "Alice"), roomMessage(160u, Bytes(byteArrayOf(0xBB.toByte())), "Alice"))
        val bookends = RoomTimeline.incomingBookends(messages)
        assertEquals(messages.map { it.id }.toSet(), bookends.nameIds)
        assertEquals(messages.map { it.id }.toSet(), bookends.avatarIds)
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::self messages never show a name or avatar()")
    fun `self messages never show a name or avatar`() {
        val messages = listOf(roomMessage(100u, alice, "Me", true), roomMessage(160u, alice, "Me", true))
        val bookends = RoomTimeline.incomingBookends(messages)
        assertTrue(bookends.nameIds.isEmpty())
        assertTrue(bookends.avatarIds.isEmpty())
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::follow-up flips previous row Hashable identity so the tiled cell reconfigures()")
    fun `follow-up flips previous row identity so the tiled cell reconfigures`() {
        val first = roomMessage(100u, alice, "Alice")
        val second = roomMessage(160u, alice, "Alice")
        val before = RoomTimeline.tiledRows(listOf(first))
        val after = RoomTimeline.tiledRows(listOf(first, second))
        assertEquals(first.id, before[0].id)
        assertTrue(before[0].showAvatar)
        assertEquals(first.id, after[0].id)
        assertFalse(after[0].showAvatar)
        assertTrue(after[1].showAvatar)
        assertNotEquals(before[0], after[0])
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::mid-insert flips the next row timestamp flag()")
    fun `mid-insert flips the next row timestamp flag`() {
        val first = roomMessage(100u, alice, "Alice")
        val later = roomMessage(500u, alice, "Alice")
        val mid = roomMessage(250u, alice, "Alice")
        val before = RoomTimeline.tiledRows(listOf(first, later))
        assertTrue(before[1].showTimestamp)
        val after = RoomTimeline.tiledRows(listOf(first, mid, later))
        assertEquals(later.id, after[2].id)
        assertFalse(after[2].showTimestamp)
        assertNotEquals(before[1], after[2])
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::300s same-prefix is one cluster; 301s is two()")
    fun `300s same-prefix is one cluster and 301s is two`() {
        val a = roomMessage(1000u, alice, "Alice")
        val at300 = roomMessage(1300u, alice, "Alice")
        val at301 = roomMessage(1301u, alice, "Alice")
        val clustered = RoomTimeline.tiledRows(listOf(a, at300))
        assertTrue(clustered[0].showSenderName)
        assertFalse(clustered[0].showAvatar)
        assertFalse(clustered[1].showSenderName)
        assertTrue(clustered[1].showAvatar)
        val split = RoomTimeline.tiledRows(listOf(a, at301))
        assertTrue(split[0].showAvatar)
        assertTrue(split[1].showSenderName)
        assertTrue(split[1].showAvatar)
    }

    @Test @OriginalCase("RoomConversationViewModelOrderingTests::self message breaks an incoming prefix cluster()")
    fun `self message breaks an incoming prefix cluster`() {
        val rows = RoomTimeline.tiledRows(
            listOf(roomMessage(100u, alice, "Alice"), roomMessage(160u, alice, "Alice", true), roomMessage(220u, alice, "Alice")),
        )
        assertTrue(rows[0].showSenderName)
        assertTrue(rows[0].showAvatar)
        assertFalse(rows[1].showSenderName)
        assertFalse(rows[1].showAvatar)
        assertTrue(rows[2].showSenderName)
        assertTrue(rows[2].showAvatar)
    }

    @Test
    fun `timestamp shows for the first message and after gaps above 300 seconds`() {
        val messages = listOf(message(0u), message(300u), message(601u))
        assertTrue(RoomTimeline.shouldShowTimestamp(0, messages))
        assertFalse(RoomTimeline.shouldShowTimestamp(1, messages))
        assertTrue(RoomTimeline.shouldShowTimestamp(2, messages))
    }
}
