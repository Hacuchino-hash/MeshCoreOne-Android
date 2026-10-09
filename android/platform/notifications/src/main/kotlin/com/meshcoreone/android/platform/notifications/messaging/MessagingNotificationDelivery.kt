// AndroidOnly: WP-401 NotificationManager-backed NotificationDeliveryPort (replaces UNUserNotificationCenter).
package com.meshcoreone.android.platform.notifications.messaging

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import com.meshcoreone.android.core.contracts.domain.NotificationId
import com.meshcoreone.android.core.contracts.domain.NotificationPostResult
import com.meshcoreone.android.core.contracts.notifications.DeliveredNotification
import com.meshcoreone.android.core.contracts.notifications.NotificationAuthorizationStatus
import com.meshcoreone.android.core.contracts.notifications.NotificationCategory
import com.meshcoreone.android.core.contracts.notifications.NotificationActionDefinition
import com.meshcoreone.android.core.contracts.notifications.NotificationCategoryDefinition
import com.meshcoreone.android.core.contracts.notifications.NotificationDeliveryPort
import com.meshcoreone.android.core.contracts.notifications.NotificationRequest
import com.meshcoreone.android.core.model.SnapshotList

/**
 * Delivery adapter behind the WP-215 policy. Permission denial, a blocked channel or any platform
 * failure surfaces as a [NotificationPostResult] or a throw the policy already contains; nothing here
 * can gate, delay or fail message send/receive. [setBadgeCount] is a no-op: Android derives launcher
 * badges from the active notifications, there is no app-set count API.
 *
 * @param receiver component of the (app-registered, non-exported) [MessagingActionReceiver] subclass.
 * @param permissions app-owned permission state/prompt; null means the adapter can report but not ask.
 */
class MessagingNotificationDelivery(
    context: Context,
    receiver: ComponentName,
    private val permissions: NotificationPermissionGateway? = null,
    private val labels: MessagingChannelLabels = MessagingChannelLabels(),
) : NotificationDeliveryPort {
    private val appContext: Context = context.applicationContext ?: context
    private val manager: NotificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val shortcuts = ConversationShortcuts(appContext)

    @Volatile
    private var definitions: Map<NotificationCategory, List<NotificationActionDefinition>> = emptyMap()
    private val factory = MessagingNotificationFactory(appContext, receiver, { definitions })

    override suspend fun authorizationStatus(): NotificationAuthorizationStatus = MessagingAuthorization.status(
        granted = permissions?.isGranted() ?: true,
        appEnabled = manager.areNotificationsEnabled(),
        requested = permissions?.wasRequested() ?: true,
    )

    override suspend fun requestAuthorization(): Boolean {
        val gateway = permissions ?: return false
        return gateway.isGranted() || gateway.request()
    }

    override suspend fun registerCategories(categories: SnapshotList<NotificationCategoryDefinition>) {
        MessagingChannels.register(manager, labels)
        definitions = categories.associate { it.category to it.actions.toList() }
    }

    override suspend fun post(request: NotificationRequest): NotificationPostResult {
        if (authorizationStatus() != NotificationAuthorizationStatus.AUTHORIZED) return NotificationPostResult.PermissionDenied
        val channel = MessagingNotificationContract.channelFor(request.category, request.soundEnabled)
        MessagingChannels.register(manager, labels)
        if (MessagingChannels.isBlocked(manager, channel)) return NotificationPostResult.PermissionDenied
        val shortcutId = shortcuts.publish(request)
        manager.notify(request.id.value, MessagingNotificationContract.NOTIFICATION_NUMERIC_ID, factory.build(request, shortcutId))
        return NotificationPostResult.Posted
    }

    override suspend fun setBadgeCount(count: Long) = Unit

    override suspend fun deliveredNotifications(): SnapshotList<DeliveredNotification> =
        SnapshotList(
            manager.activeNotifications.mapNotNull { active ->
                val tag = active.tag ?: return@mapNotNull null
                val payload = MessagingNotificationFactory.payloadFrom(
                    active.notification.extras?.getBundle(MessagingNotificationContract.EXTRA_PAYLOAD),
                )
                DeliveredNotification(NotificationId(tag), payload)
            },
        )

    override suspend fun removeDelivered(ids: SnapshotList<NotificationId>) {
        ids.forEach { manager.cancel(it.value, MessagingNotificationContract.NOTIFICATION_NUMERIC_ID) }
    }
}
