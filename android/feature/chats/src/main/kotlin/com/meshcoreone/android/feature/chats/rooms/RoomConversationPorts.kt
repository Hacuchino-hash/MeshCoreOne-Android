// AndroidOnly: WP-310 Feature-owned seams over RoomServerService/DataStore/NotificationService (features may not import core:services).
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID

/** Room operations the conversation needs; null/unavailable services mirror a disconnected state. */
interface RoomConversationPort {
    suspend fun fetchMessages(sessionId: UUID): List<RoomMessageDTO>
    suspend fun markAsRead(sessionId: UUID)
    suspend fun markFailedSendsSeen(sessionId: UUID)
    suspend fun postMessage(sessionId: UUID, text: String): RoomMessageDTO
    suspend fun retryMessage(messageId: UUID): RoomMessageDTO
    suspend fun fetchSession(sessionId: UUID): RemoteNodeSessionDTO?
    suspend fun removeDeliveredNotifications(sessionId: UUID)
    suspend fun updateBadgeCount()
    fun notifyConversationsChanged()
}

/** `MessageEvent` cases that matter to a room conversation. */
sealed interface RoomEvent {
    data class MessageReceived(val message: RoomMessageDTO, val sessionId: UUID) : RoomEvent
    data class MessageStatusUpdated(val messageId: UUID) : RoomEvent
    data class MessageFailed(val messageId: UUID) : RoomEvent

    /** Every non-room `MessageEvent`: not room-scoped, ignored. */
    data object Other : RoomEvent
}

/** Contact lookup behind the room login sheet. */
interface RoomAuthenticationPort {
    suspend fun fetchContact(radioId: RadioId, publicKey: Bytes): ContactDTO?
}

/** Persists the room info sheet's quick actions. */
interface RoomInfoPort {
    suspend fun setNotificationLevel(session: RemoteNodeSessionDTO, level: NotificationLevel)
    suspend fun setFavorite(session: RemoteNodeSessionDTO, isFavorite: Boolean)
}
