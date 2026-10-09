// AndroidOnly: WP-401 Names shared by the messaging notification channels, actions and PendingIntents.
package com.meshcoreone.android.platform.notifications.messaging

import com.meshcoreone.android.core.contracts.notifications.NotificationCategory
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload

/** Stable identifiers. Channel ids are persisted by the platform once created, so they never change. */
object MessagingNotificationContract {
    const val CHANNEL_DIRECT = "mc1_messages_direct"
    const val CHANNEL_CHANNEL = "mc1_messages_channel"
    const val CHANNEL_ROOM = "mc1_messages_room"
    const val CHANNEL_REACTION = "mc1_reactions"
    const val CHANNEL_ALERTS = "mc1_alerts"

    /** Receiver action strings (explicit component intents only; never exported). */
    const val ACTION_REPLY = "com.meshcoreone.android.notifications.REPLY"
    const val ACTION_MARK_READ = "com.meshcoreone.android.notifications.MARK_READ"

    const val REMOTE_INPUT_KEY = "mc1_reply_text"
    const val EXTRA_PAYLOAD = "mc1_notification_payload"
    const val EXTRA_TAG = "mc1_notification_tag"

    /** Notification numeric id; the NotificationId string is carried as the platform tag. */
    const val NOTIFICATION_NUMERIC_ID = 0

    /** Messages kept in one conversation notification's style. */
    const val MAX_STYLE_MESSAGES = 8

    /** Suffix of the low-importance twin of every channel, used when a notification asks for no sound. */
    const val SILENT_SUFFIX = "_silent"

    /**
     * Android fixes a channel's sound at creation, so "no sound" is a separate silent channel rather
     * than a per-notification flag.
     */
    fun channelFor(category: NotificationCategory?, soundEnabled: Boolean = true): String {
        val base = when (category) {
            NotificationCategory.DIRECT_MESSAGE -> CHANNEL_DIRECT
            NotificationCategory.CHANNEL_MESSAGE -> CHANNEL_CHANNEL
            NotificationCategory.ROOM_MESSAGE -> CHANNEL_ROOM
            NotificationCategory.REACTION -> CHANNEL_REACTION
            NotificationCategory.LOW_BATTERY, null -> CHANNEL_ALERTS
        }
        return if (soundEnabled) base else base + SILENT_SUFFIX
    }

    /** Whether the payload is a chat message shown with MessagingStyle. */
    fun isConversationMessage(payload: NotificationPayload): Boolean =
        payload is NotificationPayload.DirectMessage ||
            payload is NotificationPayload.ChannelMessage ||
            payload is NotificationPayload.RoomMessage

    /** Whether the payload is a group conversation (channel or room) rather than one-to-one. */
    fun isGroupConversation(payload: NotificationPayload): Boolean =
        payload is NotificationPayload.ChannelMessage || payload is NotificationPayload.RoomMessage
}
