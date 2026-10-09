// AndroidOnly: WP-401 Receives direct-reply and mark-read actions, including after a cold process start.
package com.meshcoreone.android.platform.notifications.messaging

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meshcoreone.android.core.contracts.notifications.NotificationAction
import com.meshcoreone.android.core.contracts.notifications.NotificationResponse
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Subclass-free receiver: register it (not exported) in the app manifest. It performs no radio or
 * database work itself; it decodes the intent and hands a [NotificationResponse] to the router, which
 * queues it if the service graph is not up yet (cold start). The pending result is finished when the
 * router returns, so the process is not reclaimed mid-action. A reply notification is always cleared
 * afterwards so the platform's reply spinner never sticks; any failure notification is posted by the
 * WP-215 policy under its own id.
 */
open class MessagingActionReceiver : BroadcastReceiver() {
    protected open val router: MessagingResponseRouter get() = MessagingNotifications.router

    override fun onReceive(context: Context, intent: Intent) {
        val response = parse(intent) ?: return
        val tag = intent.getStringExtra(MessagingNotificationContract.EXTRA_TAG)
        val pending = goAsync()
        EXECUTOR.execute {
            val finish = Continuation<ResponseDisposition>(EmptyCoroutineContext) { result ->
                result.exceptionOrNull()?.let { logger.log(Level.WARNING, "Notification action failed", it) }
                if (response.action == NotificationAction.REPLY && tag != null) clearReplySpinner(context, tag)
                pending.finish()
            }
            suspend { router.dispatch(response) }.startCoroutine(finish)
        }
    }

    private fun clearReplySpinner(context: Context, tag: String) {
        try {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(tag, MessagingNotificationContract.NOTIFICATION_NUMERIC_ID)
        } catch (failure: RuntimeException) {
            logger.log(Level.FINE, "Could not clear reply notification", failure)
        }
    }

    companion object {
        private val logger: Logger = Logger.getLogger("com.mc1.MessagingActionReceiver")
        private val EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mc1-notification-actions").apply { isDaemon = true }
        }

        /** Null for an unknown action or an undecodable payload; nothing is dispatched for those. */
        fun parse(intent: Intent): NotificationResponse? {
            val action = when (intent.action) {
                MessagingNotificationContract.ACTION_REPLY -> NotificationAction.REPLY
                MessagingNotificationContract.ACTION_MARK_READ -> NotificationAction.MARK_READ
                else -> return null
            }
            val payload = MessagingNotificationFactory.payloadFrom(
                intent.getBundleExtra(MessagingNotificationContract.EXTRA_PAYLOAD),
            ) ?: return null
            val text = if (action == NotificationAction.REPLY) {
                RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessagingNotificationContract.REMOTE_INPUT_KEY)?.toString()
            } else {
                null
            }
            return NotificationResponse(payload, action, text)
        }
    }
}
