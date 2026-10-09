// AndroidOnly: WP-401 Long-lived conversation shortcuts so MessagingStyle notifications qualify as conversations.
package com.meshcoreone.android.platform.notifications.messaging

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.meshcoreone.android.core.contracts.notifications.NotificationRequest
import java.util.logging.Level
import java.util.logging.Logger

/** Publishes one dynamic shortcut per conversation thread. Failure is contained: it only costs conversation ranking. */
class ConversationShortcuts(private val context: Context) {
    fun shortcutId(request: NotificationRequest): String? =
        if (MessagingNotificationContract.isConversationMessage(request.payload)) {
            request.threadIdentifier ?: request.id.value
        } else {
            null
        }

    fun publish(request: NotificationRequest): String? {
        val id = shortcutId(request) ?: return null
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return id
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return id
        val shortcut = ShortcutInfo.Builder(context, id)
            .setShortLabel(request.title.ifBlank { id })
            .setLongLived(true)
            .setIcon(Icon.createWithResource(context, android.R.drawable.stat_notify_chat))
            .setIntent(launch.setAction(android.content.Intent.ACTION_VIEW))
            .build()
        try {
            manager.pushDynamicShortcut(shortcut)
        } catch (failure: RuntimeException) {
            // Rate limiting or a disabled launcher must never stop the notification itself.
            logger.log(Level.FINE, "Conversation shortcut not published", failure)
        }
        return id
    }

    private companion object {
        val logger: Logger = Logger.getLogger("com.mc1.ConversationShortcuts")
    }
}
