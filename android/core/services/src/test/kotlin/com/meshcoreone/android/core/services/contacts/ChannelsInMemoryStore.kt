// AndroidOnly: WP-209 in-memory ChannelPersisting fake standing in for the source SwiftData PersistenceStore.createTestDataStore.
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.contracts.domain.ChannelPersisting
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.event.ChannelInfo
import java.time.Instant
import java.util.UUID

/** A stored channel message (only the fields channel tests observe). */
internal data class ChannelsStoredMessage(val radioId: RadioId, val channelIndex: UByte, val text: String)

/**
 * Mirrors the source channel persistence semantics the service relies on: index-sorted fetches,
 * secret-change history wipes, single-transaction `batchSaveChannels` (upsert, delete
 * unconfigured slots and their messages, prune beyond capacity, rollback on failure).
 */
internal class ChannelsInMemoryStore : ChannelPersisting {
    private val lock = Any()
    private var channels: List<ChannelDTO> = emptyList()
    private var messages: List<ChannelsStoredMessage> = emptyList()

    /** When set, the next `batchSaveChannels` throws it before committing. */
    @Volatile var batchSaveFailure: Exception? = null

    /** When set, `fetchChannels` throws it (snapshot-failure path). */
    @Volatile var fetchChannelsFailure: Exception? = null

    fun saveTestMessage(radioId: RadioId, channelIndex: UByte, text: String) = synchronized(lock) {
        messages = messages + ChannelsStoredMessage(radioId, channelIndex, text)
    }

    fun fetchMessages(radioId: RadioId, channelIndex: UByte): List<ChannelsStoredMessage> = synchronized(lock) {
        messages.filter { it.radioId == radioId && it.channelIndex == channelIndex }
    }

    fun replace(dto: ChannelDTO) = synchronized(lock) {
        channels = channels.filterNot { it.id == dto.id } + dto
    }

    override suspend fun fetchChannels(radioId: RadioId): SnapshotList<ChannelDTO> {
        fetchChannelsFailure?.let { throw it }
        return synchronized(lock) { channels.filter { it.radioId == radioId }.sortedBy { it.index }.snapshot() }
    }

    override suspend fun fetchChannel(radioId: RadioId, index: UByte): ChannelDTO? = synchronized(lock) {
        channels.firstOrNull { it.radioId == radioId && it.index == index }
    }

    override suspend fun fetchChannel(key: EntityKey): ChannelDTO? = synchronized(lock) { channels.firstOrNull { it.id == key.id } }

    override suspend fun saveChannel(radioId: RadioId, info: ChannelInfo): UUID = synchronized(lock) {
        val existing = channels.firstOrNull { it.radioId == radioId && it.index == info.index }
        if (existing != null && existing.secret != info.secret) messages = messages.withoutSlot(radioId, info.index)
        val saved = if (existing == null) ChannelDTO.fromInfo(radioId, info) else radioUpdated(existing, info)
        channels = channels.filterNot { it.id == saved.id } + saved
        saved.id
    }

    override suspend fun saveChannel(dto: ChannelDTO) = replace(dto)

    override suspend fun batchSaveChannels(
        radioId: RadioId, configured: SnapshotList<ChannelInfo>, unconfiguredIndices: SnapshotList<UByte>, pruneBeyond: UByte?,
    ): SnapshotList<ChannelDTO> {
        synchronized(lock) {
            val byIndex = LinkedHashMap<UByte, ChannelDTO>()
            channels.filter { it.radioId == radioId }.forEach { byIndex.putIfAbsent(it.index, it) }
            var stagedMessages = messages
            for (info in configured) {
                val row = byIndex[info.index]
                if (row != null && row.secret != info.secret) stagedMessages = stagedMessages.withoutSlot(radioId, row.index)
                byIndex[info.index] = if (row == null) ChannelDTO.fromInfo(radioId, info) else radioUpdated(row, info)
            }
            for (index in unconfiguredIndices) {
                stagedMessages = stagedMessages.withoutSlot(radioId, index)
                byIndex.remove(index)
            }
            if (pruneBeyond != null) {
                byIndex.keys.filter { it >= pruneBeyond }.forEach { index ->
                    stagedMessages = stagedMessages.withoutSlot(radioId, index)
                    byIndex.remove(index)
                }
            }
            batchSaveFailure?.let { batchSaveFailure = null; throw it }
            channels = channels.filterNot { it.radioId == radioId } + byIndex.values
            messages = stagedMessages
        }
        return fetchChannels(radioId)
    }

    override suspend fun deleteChannel(key: EntityKey) = synchronized(lock) {
        val channel = channels.firstOrNull { it.id == key.id } ?: return@synchronized
        messages = messages.withoutSlot(channel.radioId, channel.index)
        channels = channels.filterNot { it.id == key.id }
    }

    override suspend fun deleteMessagesForChannel(radioId: RadioId, channelIndex: UByte) = synchronized(lock) {
        messages = messages.withoutSlot(radioId, channelIndex)
    }

    override suspend fun updateChannelLastMessage(key: EntityKey, date: Instant?) = update(key) { it.copy(lastMessageDate = date) }
    override suspend fun incrementChannelUnreadCount(key: EntityKey) = update(key) { it.copy(unreadCount = it.unreadCount + 1) }
    override suspend fun clearChannelUnreadCount(key: EntityKey) = update(key) { it.copy(unreadCount = 0) }
    override suspend fun clearChannelUnreadCount(radioId: RadioId, index: UByte) = synchronized(lock) {
        channels = channels.map { if (it.radioId == radioId && it.index == index) it.copy(unreadCount = 0) else it }
    }
    override suspend fun setChannelMuted(key: EntityKey, isMuted: Boolean) =
        update(key) { it.withNotificationLevel(if (isMuted) NotificationLevel.MUTED else NotificationLevel.ALL) }
    override suspend fun setChannelNotificationLevel(key: EntityKey, level: NotificationLevel) = update(key) { it.withNotificationLevel(level) }
    override suspend fun setSessionNotificationLevel(key: EntityKey, level: NotificationLevel) = update(key) { it.withNotificationLevel(level) }
    override suspend fun setChannelFavorite(key: EntityKey, isFavorite: Boolean) = update(key) { it.withFavorite(isFavorite) }
    override suspend fun setChannelFloodScope(key: EntityKey, floodScope: ChannelFloodScope) = update(key) { it.withFloodScope(floodScope) }
    override suspend fun incrementChannelUnreadMentionCount(key: EntityKey) = update(key) { it.copy(unreadMentionCount = it.unreadMentionCount + 1) }
    override suspend fun decrementChannelUnreadMentionCount(key: EntityKey) =
        update(key) { it.copy(unreadMentionCount = maxOf(0, it.unreadMentionCount - 1)) }
    override suspend fun clearChannelUnreadMentionCount(key: EntityKey) = update(key) { it.copy(unreadMentionCount = 0) }
    override suspend fun fetchUnseenChannelMentionIDs(radioId: RadioId, channelIndex: UByte): SnapshotList<UUID> = SnapshotList.empty()

    private fun update(key: EntityKey, transform: (ChannelDTO) -> ChannelDTO) = synchronized(lock) {
        channels = channels.map { if (it.id == key.id) transform(it) else it }
    }

    /** Source `applyRadioChannelInfo`: a changed secret is a new occupant, so counters reset. */
    private fun radioUpdated(row: ChannelDTO, info: ChannelInfo): ChannelDTO =
        if (row.secret == info.secret) row.updating(info)
        else row.copy(lastMessageDate = null, unreadCount = 0, unreadMentionCount = 0).updating(info)

    private fun List<ChannelsStoredMessage>.withoutSlot(radioId: RadioId, index: UByte) =
        filterNot { it.radioId == radioId && it.channelIndex == index }
}
