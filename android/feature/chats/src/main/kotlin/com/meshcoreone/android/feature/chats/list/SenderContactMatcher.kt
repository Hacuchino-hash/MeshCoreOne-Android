// PortedFrom: MC1/Views/Chats/SenderContactMatcher.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/BlockSenderContext.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/SendDMContext.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.list

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RadioId
import java.util.UUID

/** Resolves a channel sender's display name to stored contacts by case-insensitive name match (no trimming). */
object SenderContactMatcher {
    fun filter(contacts: List<ContactDTO>, senderName: String, excludeBlocked: Boolean = false): List<ContactDTO> =
        contacts.filter { (!excludeBlocked || !it.isBlocked) && ChatTextMatching.caseInsensitiveEquals(it.name, senderName) }
}

/** Context for presenting the block-sender sheet in channel conversations. */
data class BlockSenderContext(val senderName: String, val radioId: RadioId, val id: UUID = UUID.randomUUID())

/** Context for presenting the send-DM sheet from a channel message sender. */
data class SendDMContext(
    val senderName: String,
    val radioId: RadioId,
    val unverifiedNickname: String?,
    val id: UUID = UUID.randomUUID(),
)
