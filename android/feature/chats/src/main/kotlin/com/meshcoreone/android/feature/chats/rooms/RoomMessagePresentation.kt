// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomMessageAction.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomMessageActionAvailability.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomMessageDTO+Status.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Room/RoomConversationRow.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Room/RoomInfoSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO

/** An action a user can take on a room message from its long-press sheet. */
enum class RoomMessageAction { COPY, TRANSLATE, REPLY, SEND_DM, SEND_AGAIN }

/**
 * Which actions a room message exposes, computed once at presentation so the buttons stay stable if the
 * session's permission changes while the sheet is open. Copy/translate are always offered; translation
 * itself is owned by WP-406.
 */
data class RoomMessageActionAvailability(val canReply: Boolean, val canSendDm: Boolean, val canSendAgain: Boolean) {
    constructor(message: RoomMessageDTO, session: RemoteNodeSessionDTO) : this(
        canReply = !message.isFromSelf && session.canPost,
        canSendDm = !message.isFromSelf && message.authorName != null,
        canSendAgain = message.isFromSelf,
    )

    /** Sheet order: reply, DM, copy, translate, send again. */
    val actions: List<RoomMessageAction>
        get() = buildList {
            if (canReply) add(RoomMessageAction.REPLY)
            if (canSendDm) add(RoomMessageAction.SEND_DM)
            add(RoomMessageAction.COPY)
            add(RoomMessageAction.TRANSLATE)
            if (canSendAgain) add(RoomMessageAction.SEND_AGAIN)
        }
}

/** Visible status text bucket of a room message (the Compose layer resolves the string). */
enum class RoomMessageStatusText { SENDING, SENT, DELIVERED, FAILED, RETRYING }

/** TalkBack status bucket of a room message. */
enum class RoomMessageStatusAccessibility { FAILED, SENDING, DELIVERED }

val RoomMessageDTO.statusText: RoomMessageStatusText
    get() = when (status) {
        MessageStatus.PENDING, MessageStatus.SENDING -> RoomMessageStatusText.SENDING
        MessageStatus.SENT -> RoomMessageStatusText.SENT
        MessageStatus.DELIVERED -> RoomMessageStatusText.DELIVERED
        MessageStatus.FAILED -> RoomMessageStatusText.FAILED
        MessageStatus.RETRYING -> RoomMessageStatusText.RETRYING
    }

val RoomMessageDTO.statusAccessibility: RoomMessageStatusAccessibility
    get() = when (status) {
        MessageStatus.FAILED -> RoomMessageStatusAccessibility.FAILED
        MessageStatus.PENDING, MessageStatus.SENDING, MessageStatus.RETRYING -> RoomMessageStatusAccessibility.SENDING
        else -> RoomMessageStatusAccessibility.DELIVERED
    }

/** Connection line of a room row: connected only while the session is up and the radio is ready. */
enum class RoomRowConnection { CONNECTED, TAP_TO_RECONNECT }

fun roomRowConnection(session: RemoteNodeSessionDTO, radioReady: Boolean): RoomRowConnection =
    if (session.isConnected && radioReady) RoomRowConnection.CONNECTED else RoomRowConnection.TAP_TO_RECONNECT

/** Which management entries the room info sheet offers. */
data class RoomInfoEntries(val showsTelemetry: Boolean, val showsSettings: Boolean, val showsStatus: Boolean, val showsLastConnected: Boolean)

fun roomInfoEntries(session: RemoteNodeSessionDTO): RoomInfoEntries = RoomInfoEntries(
    showsTelemetry = session.isConnected,
    showsSettings = session.isConnected && session.isAdmin,
    showsStatus = session.isConnected,
    showsLastConnected = session.lastConnectedDate != null,
)
