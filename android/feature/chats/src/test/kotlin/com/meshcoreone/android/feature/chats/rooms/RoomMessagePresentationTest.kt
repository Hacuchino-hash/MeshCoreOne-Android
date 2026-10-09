// AndroidOnly: WP-310 Native room action/role/status presentation (guest/participant/admin cases).
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.Fixtures
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RoomMessagePresentationTest {
    private fun session(level: RoomPermissionLevel, connected: Boolean = true, role: RemoteNodeRole = RemoteNodeRole.ROOM_SERVER) =
        RemoteNodeSessionDTO(
            radioId = Fixtures.radio, publicKey = Bytes(ByteArray(32)), name = "Room", role = role,
            isConnected = connected, permissionLevel = level,
            lastConnectedDate = if (connected) Instant.EPOCH else null,
        )

    private fun message(fromSelf: Boolean = false, author: String? = "Bob", status: MessageStatus = MessageStatus.DELIVERED) = RoomMessageDTO(
        sessionID = UUID.randomUUID(), authorKeyPrefix = Bytes(byteArrayOf(1)), authorName = author, text = "hi", timestamp = 1u,
        isFromSelf = fromSelf, statusRawValue = status.rawValue,
    )

    @Test
    fun `guest cannot reply but can DM a named author`() {
        val availability = RoomMessageActionAvailability(message(), session(RoomPermissionLevel.GUEST))
        assertFalse(availability.canReply)
        assertTrue(availability.canSendDm)
        assertFalse(availability.canSendAgain)
        assertEquals(
            listOf(RoomMessageAction.SEND_DM, RoomMessageAction.COPY, RoomMessageAction.TRANSLATE), availability.actions,
        )
    }

    @Test
    fun `participant and admin can reply to others`() {
        for (level in listOf(RoomPermissionLevel.READ_WRITE, RoomPermissionLevel.ADMIN)) {
            val availability = RoomMessageActionAvailability(message(), session(level))
            assertTrue(availability.canReply, level.name)
            assertEquals(RoomMessageAction.REPLY, availability.actions.first())
        }
    }

    @Test
    fun `own messages offer send again but never reply or DM`() {
        val availability = RoomMessageActionAvailability(message(fromSelf = true), session(RoomPermissionLevel.ADMIN))
        assertFalse(availability.canReply)
        assertFalse(availability.canSendDm)
        assertTrue(availability.canSendAgain)
        assertEquals(RoomMessageAction.SEND_AGAIN, availability.actions.last())
    }

    @Test
    fun `an anonymous author cannot be DMed`() {
        assertFalse(RoomMessageActionAvailability(message(author = null), session(RoomPermissionLevel.ADMIN)).canSendDm)
    }

    @Test
    fun `a repeater session never allows posting even with write permission`() {
        val repeater = session(RoomPermissionLevel.READ_WRITE, role = RemoteNodeRole.REPEATER)
        assertFalse(RoomMessageActionAvailability(message(), repeater).canReply)
    }

    @Test
    fun `status text and accessibility buckets`() {
        fun text(s: MessageStatus) = message(status = s).statusText
        assertEquals(RoomMessageStatusText.SENDING, text(MessageStatus.PENDING))
        assertEquals(RoomMessageStatusText.SENDING, text(MessageStatus.SENDING))
        assertEquals(RoomMessageStatusText.SENT, text(MessageStatus.SENT))
        assertEquals(RoomMessageStatusText.DELIVERED, text(MessageStatus.DELIVERED))
        assertEquals(RoomMessageStatusText.FAILED, text(MessageStatus.FAILED))
        assertEquals(RoomMessageStatusText.RETRYING, text(MessageStatus.RETRYING))
        assertEquals(RoomMessageStatusAccessibility.FAILED, message(status = MessageStatus.FAILED).statusAccessibility)
        for (s in listOf(MessageStatus.PENDING, MessageStatus.SENDING, MessageStatus.RETRYING)) {
            assertEquals(RoomMessageStatusAccessibility.SENDING, message(status = s).statusAccessibility)
        }
        for (s in listOf(MessageStatus.SENT, MessageStatus.DELIVERED)) {
            assertEquals(RoomMessageStatusAccessibility.DELIVERED, message(status = s).statusAccessibility)
        }
    }

    @Test
    fun `row connection needs both a live session and a ready radio`() {
        val connected = session(RoomPermissionLevel.GUEST)
        assertEquals(RoomRowConnection.CONNECTED, roomRowConnection(connected, true))
        assertEquals(RoomRowConnection.TAP_TO_RECONNECT, roomRowConnection(connected, false))
        assertEquals(RoomRowConnection.TAP_TO_RECONNECT, roomRowConnection(session(RoomPermissionLevel.GUEST, connected = false), true))
    }

    @Test
    fun `info entries expose telemetry only when connected and settings only for admins`() {
        assertEquals(RoomInfoEntries(true, true, true, true), roomInfoEntries(session(RoomPermissionLevel.ADMIN)))
        assertEquals(RoomInfoEntries(true, false, true, true), roomInfoEntries(session(RoomPermissionLevel.READ_WRITE)))
        assertEquals(RoomInfoEntries(false, false, false, false), roomInfoEntries(session(RoomPermissionLevel.ADMIN, connected = false)))
    }
}
