// AndroidOnly: WP-210 Native RoomServerService incoming-message cases (Swift has no unit tests for it): session matching, dedup, unread counts, self detection, connection recovery and queries.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomServerIncomingTest {
    private val roomKey = remoteCoreKey(0xD1)
    private val roomPrefix = roomKey.prefix(6)
    private val author = Bytes.of(0x11, 0x22, 0x33, 0x44)

    private suspend fun RemoteAdminHarness.room(connected: Boolean = true): EntityKey =
        core.addSession(remoteCoreSession(core.radioId, roomKey, RemoteNodeRole.ROOM_SERVER, RoomPermissionLevel.READ_WRITE, connected))

    private suspend fun RemoteAdminHarness.receive(text: String = "hi all", timestamp: UInt = 1000u, from: Bytes = author) =
        rooms.handleIncomingMessage(roomPrefix, timestamp, from, text)

    @TestFactory
    fun incomingCases(): List<DynamicTest> = listOf(
        remoteCoreNative("a new room message is saved with the resolved author, bookmarks the sync timestamp and counts unread") {
            withRemoteAdminHarness {
                val key = room()
                core.store.saveContact(
                    remoteCoreContact(core.radioId, Bytes.of(0x11, 0x22, 0x33, 0x44) + Bytes(ByteArray(28) { 7 })).copy(name = "Alice"),
                )
                val saved = assertNotNull(receive())
                assertEquals(key.id, saved.sessionID)
                assertEquals(author, saved.authorKeyPrefix)
                assertEquals("Alice", saved.authorName)
                assertEquals("hi all", saved.text)
                assertEquals(1000u, saved.timestamp)
                assertFalse(saved.isFromSelf)
                assertEquals(MessageStatus.DELIVERED, saved.status)
                assertEquals(RoomMessageDTOKey.of(1000u, author, "hi all"), saved.deduplicationKey)
                assertEquals(saved, store.message(EntityKey(core.radioId, saved.id)))
                assertEquals(listOf("save:3", "activity:1000", "unread+1"), store.operations)
                assertEquals(1L, core.store.session(key)?.unreadCount)
                assertEquals(1000u, core.store.session(key)?.lastSyncTimestamp)
                assertEquals(listOf("roomReceived ${roomPrefix.hexString} ${author.hexString} 6"), audit.entries)
            }
        },
        remoteCoreNative("unknown authors keep a nil name; the bookmark never moves backwards") {
            withRemoteAdminHarness {
                val key = room()
                assertNull(assertNotNull(receive(timestamp = 2000u)).authorName)
                assertNotNull(receive(text = "older", timestamp = 1500u))
                assertEquals(2000u, core.store.session(key)?.lastSyncTimestamp)
                assertEquals(2L, core.store.session(key)?.unreadCount)
            }
        },
        remoteCoreNative("a duplicate (same timestamp, author and text) returns nil without saving, auditing or counting") {
            withRemoteAdminHarness {
                val key = room()
                assertNotNull(receive())
                assertNull(receive())
                assertNotNull(receive(text = "hi all!"))
                assertNotNull(receive(timestamp = 1001u))
                assertNotNull(receive(from = Bytes.of(0x11, 0x22, 0x33, 0x45)))
                assertEquals(4, store.messageCount)
                assertEquals(4L, core.store.session(key)?.unreadCount)
                assertEquals(4, audit.entries.size)
            }
        },
        remoteCoreNative("messages from unknown rooms, repeaters or another radio's session are ignored") {
            withRemoteAdminHarness {
                assertNull(receive())
                core.addSession(remoteCoreSession(core.radioId, roomKey, RemoteNodeRole.REPEATER, isConnected = true))
                assertNull(receive())
                core.addSession(remoteCoreSession(RadioId(UUID.randomUUID()), remoteCoreKey(0xD2), RemoteNodeRole.ROOM_SERVER))
                assertNull(rooms.handleIncomingMessage(remoteCoreKey(0xD2).prefix(6), 1u, author, "x"))
                room()
                assertNull(rooms.handleIncomingMessage(roomKey.prefix(4), 1u, author, "x"))
                assertEquals(0, store.messageCount)
                assertTrue(store.operations.isEmpty())
            }
        },
        remoteCoreNative("any message from a disconnected room recovers the session once, even a duplicate") {
            withRemoteAdminHarness {
                val key = room(connected = false)
                val seen = mutableListOf<RoomServerEvent>()
                val stream = rooms.events()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { stream.collect { seen += it } }
                assertNotNull(receive())
                remoteCoreAwait("recovery event never arrived") { seen.size == 1 }
                assertEquals(RoomServerEvent.ConnectionRecovered(key), seen.single())
                assertEquals(true, core.store.session(key)?.isConnected)
                assertEquals(listOf("markConnected", "save:3", "activity:1000", "unread+1"), store.operations)

                core.store.markSessionDisconnected(key)
                assertNull(receive())
                remoteCoreAwait("second recovery never arrived") { seen.size == 2 }
                assertEquals(true, core.store.session(key)?.isConnected)
                assertNotNull(receive(text = "connected now"))
                remoteCoreSettle()
                assertEquals(2, seen.size)
                assertEquals(2, store.operations.count { it == "markConnected" })
                collector.cancel()
            }
        },
        remoteCoreNative("a failed recovery write is ignored: no event, the session stays disconnected, the message is stored") {
            withRemoteAdminHarness {
                val key = room(connected = false)
                val seen = mutableListOf<RoomServerEvent>()
                val stream = rooms.events()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { stream.collect { seen += it } }
                store.markConnectedError = IllegalStateException("locked")
                assertNotNull(receive())
                remoteCoreSettle()
                assertTrue(seen.isEmpty())
                assertEquals(false, core.store.session(key)?.isConnected)
                assertEquals(listOf("markConnected", "save:3", "activity:1000", "unread+1"), store.operations)
                collector.cancel()
            }
        },
        remoteCoreNative("our own author prefix marks the message from self and skips the unread count") {
            withRemoteAdminHarness {
                val key = room()
                rooms.setSelfPublicKeyPrefix(Bytes.of(0x11, 0x22, 0x33, 0x44, 0x55, 0x66))
                val saved = assertNotNull(receive(from = Bytes.of(0x11, 0x22, 0x33, 0x44, 0x99)))
                assertTrue(saved.isFromSelf)
                assertEquals(listOf("save:3", "activity:1000"), store.operations)
                assertEquals(0L, core.store.session(key)?.unreadCount)
            }
        },
        remoteCoreNative("without a self prefix nothing is from self, including an all-zero author") {
            withRemoteAdminHarness {
                room()
                assertFalse(assertNotNull(receive(from = Bytes.of(0, 0, 0, 0))).isFromSelf)
            }
        },
        remoteCoreNative("fetchMessages passes limit and offset through; markAsRead resets the unread count") {
            withRemoteAdminHarness {
                val key = room()
                (1..5).forEach { assertNotNull(receive(text = "m$it", timestamp = it.toUInt())) }
                assertEquals(listOf("m2", "m3"), rooms.fetchMessages(key, limit = 2, offset = 1).map { it.text })
                assertEquals(5, rooms.fetchMessages(key).size)
                assertTrue(store.operations.containsAll(listOf("fetchMessages:2:1", "fetchMessages:nil:nil")))
                assertEquals(5L, core.store.session(key)?.unreadCount)
                rooms.markAsRead(key)
                assertEquals(0L, core.store.session(key)?.unreadCount)
            }
        },
        remoteCoreNative("fetchRoomSessions lists rooms; getConnectedSession is scoped to the service radio and connected rooms") {
            withRemoteAdminHarness {
                val key = room()
                core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xD3), RemoteNodeRole.ROOM_SERVER))
                core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xD4), RemoteNodeRole.REPEATER, isConnected = true))
                val otherRadio = RadioId(UUID.randomUUID())
                core.addSession(remoteCoreSession(otherRadio, remoteCoreKey(0xD5), RemoteNodeRole.ROOM_SERVER, isConnected = true))
                assertEquals(2, rooms.fetchRoomSessions(core.radioId).size)
                assertEquals(1, rooms.fetchRoomSessions(otherRadio).size)
                assertEquals(key.id, rooms.getConnectedSession(roomPrefix)?.id)
                assertNull(rooms.getConnectedSession(remoteCoreKey(0xD3).prefix(6)))
                assertNull(rooms.getConnectedSession(remoteCoreKey(0xD4).prefix(6)))
                assertNull(rooms.getConnectedSession(remoteCoreKey(0xD5).prefix(6)))
            }
        },
    )
}

/** Swift `RoomMessage.generateDeduplicationKey` spelled out: "<timestamp>-<AUTHOR HEX>-<first 4 SHA-256 bytes HEX>". */
private object RoomMessageDTOKey {
    fun of(timestamp: UInt, author: Bytes, text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return "$timestamp-${author.hexString.uppercase()}-${Bytes(digest.copyOf(4)).hexString.uppercase()}"
    }
}
