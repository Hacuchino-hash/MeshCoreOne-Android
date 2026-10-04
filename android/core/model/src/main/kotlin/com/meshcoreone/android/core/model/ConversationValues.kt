// PortedFrom: MC1Services/Sources/MC1Services/Models/ChatConversationID.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/FailedSendConversationKeys.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/MessageEnvelope.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.model

import java.time.Instant
import java.util.UUID

sealed interface ConversationKey {
    data class DM(val contactID: UUID) : ConversationKey
    data class Channel(val channelIndex: UByte) : ConversationKey
}

data class ChatConversationID(val radioId: RadioId, val conversation: ConversationKey) {
    val draftStorageKey: String
        get() = when (val key = conversation) {
            is ConversationKey.DM -> "${radioId.canonicalString}|dm|${key.contactID.canonicalString()}"
            is ConversationKey.Channel -> "${radioId.canonicalString}|ch|${key.channelIndex}"
        }
    companion object {
        fun dm(radioId: RadioId, contactID: UUID): ChatConversationID = ChatConversationID(radioId, ConversationKey.DM(contactID))
        fun channel(radioId: RadioId, channelIndex: UByte): ChatConversationID = ChatConversationID(radioId, ConversationKey.Channel(channelIndex))
    }
}

data class FailedSendConversationKeys(
    val contactIDs: SnapshotSet<UUID> = SnapshotSet.empty(),
    val channelIDs: SnapshotSet<UUID> = SnapshotSet.empty(),
    val roomSessionIDs: SnapshotSet<UUID> = SnapshotSet.empty(),
) {
    companion object { val EMPTY = FailedSendConversationKeys() }
}

// WP-213 supplies the actual immutable rendering identities, without a model-to-rendering edge.
data class MessageEnvelope<Resolution, Avatar>(
    val messageID: UUID,
    val isOutgoing: Boolean,
    val senderName: String,
    val senderResolution: Resolution,
    val status: MessageStatus,
    val date: Instant,
    val hasFailed: Boolean,
    val containsSelfMention: Boolean,
    val mentionSeen: Boolean,
    val incomingAvatar: Avatar?,
) {
    fun withStatus(status: MessageStatus): MessageEnvelope<Resolution, Avatar> =
        copy(status = status, hasFailed = status == MessageStatus.FAILED)
    fun withIncomingAvatar(avatar: Avatar?): MessageEnvelope<Resolution, Avatar> = copy(incomingAvatar = avatar)
}
