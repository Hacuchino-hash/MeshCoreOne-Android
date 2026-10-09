// AndroidOnly: WP-401 Notification channels (iOS has no equivalent; categories map to channels).
package com.meshcoreone.android.platform.notifications.messaging

import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * User-visible channel names. The module owns no string resources (res/ is outside WP-401's paths),
 * so the app layer may supply localized names; defaults are English fallbacks.
 */
data class MessagingChannelLabels(
    val direct: String = "Direct messages",
    val channel: String = "Channel messages",
    val room: String = "Room messages",
    val reaction: String = "Reactions",
    val alerts: String = "Alerts",
)

object MessagingChannels {
    private const val SILENT_LABEL_SUFFIX = " (silent)"

    /** Idempotent: re-creating an existing channel keeps the user's own settings. */
    fun register(manager: NotificationManager, labels: MessagingChannelLabels = MessagingChannelLabels()) {
        val specs = listOf(
            MessagingNotificationContract.CHANNEL_DIRECT to (labels.direct to NotificationManager.IMPORTANCE_HIGH),
            MessagingNotificationContract.CHANNEL_CHANNEL to (labels.channel to NotificationManager.IMPORTANCE_HIGH),
            MessagingNotificationContract.CHANNEL_ROOM to (labels.room to NotificationManager.IMPORTANCE_HIGH),
            MessagingNotificationContract.CHANNEL_REACTION to (labels.reaction to NotificationManager.IMPORTANCE_DEFAULT),
            MessagingNotificationContract.CHANNEL_ALERTS to (labels.alerts to NotificationManager.IMPORTANCE_DEFAULT),
        )
        val channels = specs.flatMap { (id, spec) ->
            listOf(
                NotificationChannel(id, spec.first, spec.second),
                NotificationChannel(id + MessagingNotificationContract.SILENT_SUFFIX, spec.first + SILENT_LABEL_SUFFIX, NotificationManager.IMPORTANCE_LOW)
                    .apply { setSound(null, null) },
            )
        }
        manager.createNotificationChannels(channels)
    }

    /** True when the user has blocked the channel in system settings. */
    fun isBlocked(manager: NotificationManager, channelId: String): Boolean =
        manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
}
