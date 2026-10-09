// AndroidOnly: WP-310 Native room login/sync conversation behavior (load, send, retry, events, coalesced reload, auth, info).
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.Fixtures
import com.meshcoreone.android.feature.chats.list.support.settle
import java.io.IOException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeRoomPort(val session: RemoteNodeSessionDTO) : RoomConversationPort {
    var stored: List<RoomMessageDTO> = emptyList()
    var fetchFailure: Exception? = null
    var postFailure: Exception? = null
    var markFailedFailure: Exception? = null
    var fetchedSession: RemoteNodeSessionDTO? = null
    var retryResult: RoomMessageDTO? = null
    val calls = mutableListOf<String>()
    var fetchCount = 0

    override suspend fun fetchMessages(sessionId: UUID): List<RoomMessageDTO> {
        fetchCount++
        fetchFailure?.let { throw it }
        return stored
    }
    override suspend fun markAsRead(sessionId: UUID) { calls += "markAsRead" }
    override suspend fun markFailedSendsSeen(sessionId: UUID) {
        markFailedFailure?.let { throw it }
        calls += "markFailedSendsSeen"
    }
    override suspend fun postMessage(sessionId: UUID, text: String): RoomMessageDTO {
        postFailure?.let { throw it }
        return RoomMessageDTO(sessionID = sessionId, authorKeyPrefix = Bytes(byteArrayOf(9)), text = text, timestamp = 1000u, isFromSelf = true)
    }
    override suspend fun retryMessage(messageId: UUID): RoomMessageDTO = retryResult ?: error("no retry result")
    override suspend fun fetchSession(sessionId: UUID) = fetchedSession
    override suspend fun removeDeliveredNotifications(sessionId: UUID) { calls += "removeNotifications" }
    override suspend fun updateBadgeCount() { calls += "badge" }
    override fun notifyConversationsChanged() { calls += "notify" }
}

class RoomConversationStateHolderTest {
    private val session = Fixtures.room("Room")
    private fun msg(ts: UInt, id: UUID = UUID.randomUUID(), fromSelf: Boolean = false, status: MessageStatus = MessageStatus.DELIVERED) = RoomMessageDTO(
        id = id, sessionID = session.id, authorKeyPrefix = Bytes(byteArrayOf(1)), authorName = "A", text = "t$ts", timestamp = ts,
        isFromSelf = fromSelf, statusRawValue = status.rawValue,
    )

    private fun run(block: suspend (CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try { block(scope) } finally { scope.cancel() }
    }

    @Test
    fun `load fetches then clears unread notifications badge and notifies`() = run { scope ->
        val port = FakeRoomPort(session).apply { stored = listOf(msg(100u), msg(200u)) }
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        val state = holder.state.value
        assertEquals(2, state.messages.size)
        assertEquals(2, state.tiledRows.size)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("markAsRead", "markFailedSendsSeen", "removeNotifications", "badge", "notify"), port.calls)
    }

    @Test
    fun `load failure surfaces the error and still ends loading`() = run { scope ->
        val port = FakeRoomPort(session).apply { fetchFailure = IOException("offline") }
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        assertEquals("offline", holder.state.value.errorMessage)
        assertTrue(holder.state.value.hasLoadedOnce)
        assertFalse(holder.state.value.isLoading)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `load without services is a no-op`() = run { scope ->
        val holder = RoomConversationStateHolder({ null }, scope)
        holder.loadMessages(session)
        assertFalse(holder.state.value.hasLoadedOnce)
        assertNull(holder.state.value.session)
    }

    @Test
    fun `send appends the posted message in order`() = run { scope ->
        val port = FakeRoomPort(session).apply { stored = listOf(msg(500u)) }
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        holder.sendMessage("hello")
        assertEquals(listOf<UInt>(500u, 1000u), holder.state.value.messages.map { it.timestamp })
        assertFalse(holder.state.value.isSending)
    }

    @Test
    fun `send with empty text or no session keeps the draft`() = run { scope ->
        val holder = RoomConversationStateHolder({ FakeRoomPort(session) }, scope)
        holder.sendMessage("draft")
        assertEquals("draft", holder.state.value.composingText)
        holder.loadMessages(session)
        holder.sendMessage("")
        assertTrue(holder.state.value.messages.isEmpty())
    }

    @Test
    fun `send failure reports the error`() = run { scope ->
        val port = FakeRoomPort(session).apply { postFailure = IOException("no route") }
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        holder.sendMessage("x")
        assertEquals("no route", holder.state.value.errorMessage)
        assertFalse(holder.state.value.isSending)
        holder.dismissError()
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `retry replaces the failed message in place`() = run { scope ->
        val failedId = UUID.randomUUID()
        val port = FakeRoomPort(session).apply { stored = listOf(msg(100u, failedId, true, MessageStatus.FAILED)) }
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        port.retryResult = msg(100u, failedId, true, MessageStatus.SENDING)
        holder.retryMessage(failedId)
        assertEquals(MessageStatus.SENDING, holder.state.value.messages.single().status)
    }

    @Test
    fun `retry failure reports the error`() = run { scope ->
        val port = FakeRoomPort(session)
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        holder.retryMessage(UUID.randomUUID())
        assertTrue(holder.state.value.errorMessage != null)
    }

    @Test
    fun `refresh session picks up the updated permission`() = run { scope ->
        val port = FakeRoomPort(session)
        val holder = RoomConversationStateHolder({ port }, scope)
        holder.loadMessages(session)
        val promoted = session.copy(permissionLevel = com.meshcoreone.android.core.model.RoomPermissionLevel.ADMIN)
        port.fetchedSession = promoted
        holder.refreshSession()
        assertEquals(promoted.permissionLevel, holder.state.value.session?.permissionLevel)
        port.fetchedSession = null
        holder.refreshSession()
        assertEquals(promoted.permissionLevel, holder.state.value.session?.permissionLevel)
    }

    @Test
    fun `received message appends optimistically and coalesces a burst into one reload`() = run { scope ->
        val port = FakeRoomPort(session)
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 20.milliseconds)
        holder.loadMessages(session)
        val baseline = port.fetchCount
        holder.handleEvent(RoomEvent.MessageReceived(msg(300u), session.id))
        holder.handleEvent(RoomEvent.MessageReceived(msg(200u), session.id))
        assertEquals(listOf<UInt>(200u, 300u), holder.state.value.messages.map { it.timestamp })
        assertEquals(baseline, port.fetchCount)
        delay(150)
        settle()
        assertEquals(baseline + 1, port.fetchCount)
    }

    @Test
    fun `received message for another room is ignored`() = run { scope ->
        val port = FakeRoomPort(session)
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 10.milliseconds)
        holder.loadMessages(session)
        holder.handleEvent(RoomEvent.MessageReceived(msg(300u), UUID.randomUUID()))
        assertTrue(holder.state.value.messages.isEmpty())
    }

    @Test
    fun `status updates reload only for known messages`() = run { scope ->
        val known = msg(100u)
        val port = FakeRoomPort(session).apply { stored = listOf(known) }
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 10.milliseconds)
        holder.loadMessages(session)
        val baseline = port.fetchCount
        holder.handleEvent(RoomEvent.MessageStatusUpdated(UUID.randomUUID()))
        holder.handleEvent(RoomEvent.Other)
        delay(60)
        assertEquals(baseline, port.fetchCount)
        holder.handleEvent(RoomEvent.MessageStatusUpdated(known.id))
        delay(100)
        assertEquals(baseline + 1, port.fetchCount)
    }

    @Test
    fun `failed message marks failed sends seen and reloads`() = run { scope ->
        val known = msg(100u, fromSelf = true)
        val port = FakeRoomPort(session).apply { stored = listOf(known) }
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 10.milliseconds)
        holder.loadMessages(session)
        port.calls.clear()
        holder.handleEvent(RoomEvent.MessageFailed(known.id))
        assertTrue("markFailedSendsSeen" in port.calls)
        assertTrue("notify" in port.calls)
    }

    @Test
    fun `failure to mark failed sends seen is reported but not fatal`() = run { scope ->
        val known = msg(100u, fromSelf = true)
        val port = FakeRoomPort(session).apply { stored = listOf(known) }
        val diagnostics = mutableListOf<String>()
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 10.milliseconds) { m, _ -> diagnostics += m }
        holder.loadMessages(session)
        port.markFailedFailure = IOException("db")
        holder.handleEvent(RoomEvent.MessageFailed(known.id))
        assertEquals(1, diagnostics.size)
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `events before a session loads are ignored`() = run { scope ->
        val holder = RoomConversationStateHolder({ FakeRoomPort(session) }, scope)
        holder.handleEvent(RoomEvent.MessageReceived(msg(1u), session.id))
        assertTrue(holder.state.value.messages.isEmpty())
    }

    @Test
    fun `close cancels a pending reload`() = run { scope ->
        val port = FakeRoomPort(session)
        val holder = RoomConversationStateHolder({ port }, scope, reloadDebounce = 50.milliseconds)
        holder.loadMessages(session)
        val baseline = port.fetchCount
        holder.handleEvent(RoomEvent.MessageReceived(msg(10u), session.id))
        holder.close()
        delay(120)
        assertEquals(baseline, port.fetchCount)
    }

    // MARK: login (room authentication sheet)

    private class FakeAuthPort(val contact: ContactDTO?, val failure: Exception? = null) : RoomAuthenticationPort {
        override suspend fun fetchContact(radioId: RadioId, publicKey: Bytes): ContactDTO? {
            failure?.let { throw it }
            return contact
        }
    }

    @Test
    fun `authentication resolves the room contact or reports not found`() = runBlocking {
        val contact = Fixtures.contact("Room contact")
        val ready = RoomAuthenticationStateHolder(FakeAuthPort(contact))
        assertEquals(RoomAuthenticationContent.Loading, ready.content.value)
        ready.load(session)
        assertEquals(RoomAuthenticationContent.Ready(contact), ready.content.value)
        val missing = RoomAuthenticationStateHolder(FakeAuthPort(null))
        missing.load(session)
        assertEquals(RoomAuthenticationContent.NotFound, missing.content.value)
        val failing = RoomAuthenticationStateHolder(FakeAuthPort(contact, IOException("db")))
        failing.load(session)
        assertIs<RoomAuthenticationContent.NotFound>(failing.content.value)
        Unit
    }

    // MARK: info sheet

    @Test
    fun `info quick actions persist and a newer change supersedes an in-flight one`() = run { scope ->
        val levels = mutableListOf<NotificationLevel>()
        val favorites = mutableListOf<Boolean>()
        val port = object : RoomInfoPort {
            override suspend fun setNotificationLevel(session: RemoteNodeSessionDTO, level: NotificationLevel) {
                delay(20)
                levels += level
            }
            override suspend fun setFavorite(session: RemoteNodeSessionDTO, isFavorite: Boolean) { favorites += isFavorite }
        }
        val holder = RoomInfoStateHolder(session, port, scope)
        holder.setNotificationLevel(NotificationLevel.MUTED)
        holder.setNotificationLevel(NotificationLevel.MENTIONS_ONLY)
        holder.setFavorite(true)
        assertEquals(NotificationLevel.MENTIONS_ONLY, holder.notificationLevel.value)
        assertTrue(holder.isFavorite.value)
        delay(80)
        assertEquals(listOf(NotificationLevel.MENTIONS_ONLY), levels)
        assertEquals(listOf(true), favorites)
        holder.close()
    }
}
