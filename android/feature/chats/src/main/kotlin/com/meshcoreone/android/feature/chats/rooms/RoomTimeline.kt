// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomConversationViewModel.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomTiledRow.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.rooms

import com.meshcoreone.android.core.model.RoomMessageDTO
import java.util.UUID
import kotlin.math.abs

/** A materialized timeline row: the message plus its grouping chrome (translation chrome is WP-406). */
data class RoomTiledRow(
    val message: RoomMessageDTO,
    val showTimestamp: Boolean,
    val showSenderName: Boolean,
    val showAvatar: Boolean,
) {
    val id: UUID get() = message.id
}

/** Ordering, timestamp and cluster rules of the room timeline. */
object RoomTimeline {
    /** Time gap in seconds that breaks message grouping for timestamps. */
    const val MESSAGE_GROUPING_GAP_SECONDS = 300

    private fun gap(a: RoomMessageDTO, b: RoomMessageDTO): Long = abs(a.timestamp.toLong() - b.timestamp.toLong())

    /**
     * Inserts [message] in server-timestamp order, deduped by id. Live arrivals can be older than the tail
     * (routine on LoRa and during history sync); ties keep arrival order, matching the store's
     * `[timestamp, createdAt]` sort. Returns [messages] unchanged for a duplicate.
     */
    fun insertedInOrder(messages: List<RoomMessageDTO>, message: RoomMessageDTO): List<RoomMessageDTO> {
        if (messages.any { it.id == message.id }) return messages
        val index = messages.indexOfFirst { it.timestamp > message.timestamp }.takeIf { it >= 0 } ?: messages.size
        return messages.toMutableList().apply { add(index, message) }
    }

    /** A timestamp shows for the first message or after a gap above [MESSAGE_GROUPING_GAP_SECONDS]. */
    fun shouldShowTimestamp(index: Int, messages: List<RoomMessageDTO>): Boolean {
        if (index <= 0) return true
        return gap(messages[index], messages[index - 1]) > MESSAGE_GROUPING_GAP_SECONDS
    }

    /** Incoming messages cluster on `authorKeyPrefix` within the gap; display-name matches do not merge. */
    fun incomingClusterContinues(earlier: RoomMessageDTO, later: RoomMessageDTO): Boolean {
        if (earlier.isFromSelf || later.isFromSelf) return false
        if (gap(later, earlier) > MESSAGE_GROUPING_GAP_SECONDS) return false
        return earlier.authorKeyPrefix.hexString == later.authorKeyPrefix.hexString
    }

    /** Message ids that start a cluster (show the name) and end one (show the avatar). */
    data class Bookends(val nameIds: Set<UUID>, val avatarIds: Set<UUID>)

    fun incomingBookends(messages: List<RoomMessageDTO>): Bookends {
        val names = linkedSetOf<UUID>()
        val avatars = linkedSetOf<UUID>()
        messages.forEachIndexed { index, message ->
            if (message.isFromSelf) return@forEachIndexed
            val previous = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            val continuesFromPrevious = previous?.let { incomingClusterContinues(it, message) } ?: false
            val continuesToNext = next?.let { incomingClusterContinues(message, it) } ?: false
            if (!continuesFromPrevious) names += message.id
            if (!continuesToNext) avatars += message.id
        }
        return Bookends(names, avatars)
    }

    fun tiledRows(messages: List<RoomMessageDTO>): List<RoomTiledRow> {
        val bookends = incomingBookends(messages)
        return messages.mapIndexed { index, message ->
            RoomTiledRow(
                message = message,
                showTimestamp = shouldShowTimestamp(index, messages),
                showSenderName = message.id in bookends.nameIds,
                showAvatar = message.id in bookends.avatarIds,
            )
        }
    }
}
