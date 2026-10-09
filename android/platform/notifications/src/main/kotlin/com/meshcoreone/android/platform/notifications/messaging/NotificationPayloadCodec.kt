// AndroidOnly: WP-401 Process-death-safe string encoding of NotificationPayload for extras and PendingIntents.
package com.meshcoreone.android.platform.notifications.messaging

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.model.RadioId
import java.util.UUID

/**
 * Round-trips a payload through flat string maps (Bundle/Intent extras). Every identifier keeps its
 * radio, so a response that arrives after a radio switch is still resolved against the radio that
 * posted it. Decoding is total: malformed or incomplete input yields null, never an exception.
 * Parsing uses no regular expressions.
 */
object NotificationPayloadCodec {
    private const val TYPE = "type"
    private const val RADIO = "radio"
    private const val CONTACT = "contact"
    private const val MESSAGE = "message"
    private const val CHANNEL = "channel"
    private const val ROOM = "room"
    private const val SESSION = "session"
    private const val BATTERY = "battery"

    private const val UUID_TEXT_LENGTH = 36
    private val UUID_DASH_POSITIONS = intArrayOf(8, 13, 18, 23)

    fun encode(payload: NotificationPayload): Map<String, String> = when (payload) {
        is NotificationPayload.DirectMessage -> mapOf(
            TYPE to "directMessage", RADIO to payload.contact.radioId.value.toString(),
            CONTACT to payload.contact.id.toString(), MESSAGE to payload.messageID.toString(),
        )
        is NotificationPayload.ChannelMessage -> mapOf(
            TYPE to "channelMessage", RADIO to payload.radioId.value.toString(),
            CHANNEL to payload.channelIndex.toString(), MESSAGE to payload.messageID.toString(),
        )
        is NotificationPayload.RoomMessage -> mapOf(
            TYPE to "roomMessage", RADIO to payload.session.radioId.value.toString(),
            SESSION to payload.session.id.toString(), ROOM to payload.roomName, MESSAGE to payload.messageID.toString(),
        )
        is NotificationPayload.NewContact -> mapOf(
            TYPE to "newContact", RADIO to payload.contact.radioId.value.toString(), CONTACT to payload.contact.id.toString(),
        )
        is NotificationPayload.Reaction -> buildMap {
            put(TYPE, "reaction")
            put(MESSAGE, payload.messageID.toString())
            val contact = payload.contact
            if (contact != null) {
                put(RADIO, contact.radioId.value.toString())
                put(CONTACT, contact.id.toString())
            }
            val index = payload.channelIndex
            val channelRadio = payload.radioId
            if (index != null && channelRadio != null) {
                put(CHANNEL, index.toString())
                put("channelRadio", channelRadio.value.toString())
            }
        }
        is NotificationPayload.LowBattery -> mapOf(TYPE to "lowBattery", BATTERY to payload.batteryPercentage.toString())
        is NotificationPayload.QuickReplyFailed -> mapOf(
            TYPE to "quickReplyFailed", RADIO to payload.contact.radioId.value.toString(), CONTACT to payload.contact.id.toString(),
        )
        is NotificationPayload.ChannelQuickReplyFailed -> mapOf(
            TYPE to "channelQuickReplyFailed", RADIO to payload.radioId.value.toString(), CHANNEL to payload.channelIndex.toString(),
        )
    }

    fun decode(values: Map<String, String>): NotificationPayload? {
        val radio = uuid(values[RADIO])?.let(::RadioId)
        val message = uuid(values[MESSAGE])
        return when (values[TYPE]) {
            "directMessage" -> directMessage(radio, uuid(values[CONTACT]), message)
            "channelMessage" -> {
                val index = channelIndex(values[CHANNEL])
                if (radio == null || index == null || message == null) null
                else NotificationPayload.ChannelMessage(radio, index, message)
            }
            "roomMessage" -> {
                val session = uuid(values[SESSION])
                val name = values[ROOM]
                if (radio == null || session == null || name == null || message == null) null
                else NotificationPayload.RoomMessage(name, EntityKey(radio, session), message)
            }
            "newContact" -> {
                val contact = uuid(values[CONTACT])
                if (radio == null || contact == null) null else NotificationPayload.NewContact(EntityKey(radio, contact))
            }
            "reaction" -> reaction(values, radio, message)
            "lowBattery" -> values[BATTERY]?.toLongOrNull()?.let { NotificationPayload.LowBattery(it) }
            "quickReplyFailed" -> {
                val contact = uuid(values[CONTACT])
                if (radio == null || contact == null) null else NotificationPayload.QuickReplyFailed(EntityKey(radio, contact))
            }
            "channelQuickReplyFailed" -> {
                val index = channelIndex(values[CHANNEL])
                if (radio == null || index == null) null else NotificationPayload.ChannelQuickReplyFailed(radio, index)
            }
            else -> null
        }
    }

    private fun directMessage(radio: RadioId?, contact: UUID?, message: UUID?): NotificationPayload? =
        if (radio == null || contact == null || message == null) null
        else NotificationPayload.DirectMessage(EntityKey(radio, contact), message)

    private fun reaction(values: Map<String, String>, radio: RadioId?, message: UUID?): NotificationPayload? {
        if (message == null) return null
        val contactId = uuid(values[CONTACT])
        val contact = if (radio != null && contactId != null) EntityKey(radio, contactId) else null
        val index = channelIndex(values[CHANNEL])
        val channelRadio = uuid(values["channelRadio"])?.let(::RadioId)
        val hasChannel = index != null && channelRadio != null
        if (contact == null && !hasChannel) return null
        return NotificationPayload.Reaction(
            message, contact, index.takeIf { hasChannel }, channelRadio.takeIf { hasChannel },
        )
    }

    /** Strict canonical UUID text only; `UUID.fromString` alone would accept short, dash-shifted forms. */
    internal fun uuid(text: String?): UUID? {
        if (text == null || text.length != UUID_TEXT_LENGTH) return null
        for (i in text.indices) {
            val c = text[i]
            val dash = i in UUID_DASH_POSITIONS
            if (dash != (c == '-')) return null
            if (!dash && !((c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F'))) return null
        }
        return UUID.fromString(text)
    }

    private fun channelIndex(text: String?): UByte? {
        val value = text?.toIntOrNull() ?: return null
        return if (value in 0..255) value.toUByte() else null
    }
}
