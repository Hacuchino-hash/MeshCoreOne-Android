// AndroidOnly: WP-401 Builds MessagingStyle notifications with direct-reply and mark-read actions.
package com.meshcoreone.android.platform.notifications.messaging

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import com.meshcoreone.android.core.contracts.notifications.NotificationAction
import com.meshcoreone.android.core.contracts.notifications.NotificationActionDefinition
import com.meshcoreone.android.core.contracts.notifications.NotificationCategory
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.contracts.notifications.NotificationRequest

/**
 * PendingIntent policy: every intent is explicit (component set) and not exported. Only the direct
 * reply action is FLAG_MUTABLE, as RemoteInput requires the system to fill in the typed text; mark-read
 * and the content tap are FLAG_IMMUTABLE.
 */
class MessagingNotificationFactory(
    private val context: Context,
    private val receiver: ComponentName,
    private val actionDefinitions: () -> Map<NotificationCategory, List<NotificationActionDefinition>>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun build(request: NotificationRequest, shortcutId: String?): Notification {
        val tag = request.id.value
        val builder = Notification.Builder(context, MessagingNotificationContract.channelFor(request.category, request.soundEnabled))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setWhen(clock())
            .setContentIntent(contentIntent(request.payload, tag))
            .addExtras(Bundle().apply {
                putBundle(MessagingNotificationContract.EXTRA_PAYLOAD, payloadBundle(request.payload))
            })
        request.threadIdentifier?.let { group -> builder.setGroup(group) }
        if (MessagingNotificationContract.isConversationMessage(request.payload)) {
            builder.setStyle(messagingStyle(request))
            shortcutId?.let { id -> builder.setShortcutId(id) }
        } else {
            builder.setContentTitle(request.title).setContentText(request.body)
        }
        request.badge?.let { badge -> builder.setNumber(badge.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) }
        val definitions = request.category?.let { actionDefinitions()[it] }.orEmpty()
        definitions.forEach { definition -> actionFor(definition, request.payload, tag)?.let(builder::addAction) }
        return builder.build()
    }

    private fun messagingStyle(request: NotificationRequest): Notification.MessagingStyle {
        val self = Person.Builder().setName(SELF_NAME).build()
        val sender = Person.Builder().setName(request.title).setKey(request.threadIdentifier ?: request.id.value).build()
        val style = Notification.MessagingStyle(self)
        if (MessagingNotificationContract.isGroupConversation(request.payload)) {
            style.setGroupConversation(true).setConversationTitle(request.title)
        }
        style.addMessage(Notification.MessagingStyle.Message(request.body, clock(), sender))
        return style
    }

    private fun actionFor(
        definition: NotificationActionDefinition,
        payload: NotificationPayload,
        tag: String,
    ): Notification.Action? {
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_send)
        return when (definition.action) {
            NotificationAction.REPLY -> {
                if (payload !is NotificationPayload.DirectMessage && payload !is NotificationPayload.ChannelMessage) return null
                val input = RemoteInput.Builder(MessagingNotificationContract.REMOTE_INPUT_KEY)
                    .setLabel(definition.textInputPlaceholder ?: definition.title).build()
                Notification.Action.Builder(icon, definition.title, replyIntent(payload, tag))
                    .addRemoteInput(input)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                    .setAllowGeneratedReplies(false)
                    .build()
            }
            NotificationAction.MARK_READ -> {
                if (!MessagingNotificationContract.isConversationMessage(payload)) return null
                Notification.Action.Builder(icon, definition.title, markReadIntent(payload, tag))
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .build()
            }
            NotificationAction.DISMISS -> null
        }
    }

    private fun replyIntent(payload: NotificationPayload, tag: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(tag, MessagingNotificationContract.ACTION_REPLY),
            actionIntent(MessagingNotificationContract.ACTION_REPLY, payload, tag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun markReadIntent(payload: NotificationPayload, tag: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(tag, MessagingNotificationContract.ACTION_MARK_READ),
            actionIntent(MessagingNotificationContract.ACTION_MARK_READ, payload, tag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(payload: NotificationPayload, tag: String): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(MessagingNotificationContract.EXTRA_PAYLOAD, payloadBundle(payload))
        return PendingIntent.getActivity(
            context, requestCode(tag, "tap"), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, payload: NotificationPayload, tag: String): Intent =
        Intent(action).setComponent(receiver).setPackage(context.packageName)
            .putExtra(MessagingNotificationContract.EXTRA_PAYLOAD, payloadBundle(payload))
            .putExtra(MessagingNotificationContract.EXTRA_TAG, tag)

    companion object {
        private const val SELF_NAME = "You"

        fun requestCode(tag: String, action: String): Int = 31 * tag.hashCode() + action.hashCode()

        fun payloadBundle(payload: NotificationPayload): Bundle =
            Bundle().apply { NotificationPayloadCodec.encode(payload).forEach { (key, value) -> putString(key, value) } }

        fun payloadFrom(bundle: Bundle?): NotificationPayload? {
            if (bundle == null) return null
            val values = bundle.keySet().mapNotNull { key -> bundle.getString(key)?.let { key to it } }.toMap()
            return NotificationPayloadCodec.decode(values)
        }
    }
}
