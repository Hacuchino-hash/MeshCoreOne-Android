// AndroidOnly: WP-206 The single connectedDevice-type foreground service and its platform starter.
package com.meshcoreone.android.core.connectivity.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Host-supplied notification content (WP-402 owns copy, l10n and actions). When no provider is
 * registered the service still satisfies the start contract with the application label.
 */
fun interface ConnectedDeviceNotificationProvider {
    fun notification(context: Context, channelId: String): Notification
}

/** Host hooks; WP-303 routes them to `ConnectedDeviceHostingController.onService*`. */
object ConnectedDeviceServiceHost {
    const val CHANNEL_ID = "connected_device"
    const val NOTIFICATION_ID = 0x4d43
    @Volatile var notificationProvider: ConnectedDeviceNotificationProvider? = null
    @Volatile var onStarted: (() -> Unit)? = null
    @Volatile var onStartFailed: ((Throwable) -> Unit)? = null
    @Volatile var onStopped: (() -> Unit)? = null
    @Volatile var onFailure: ((Throwable) -> Unit)? = null
}

/**
 * Keeps the process eligible to run the BLE/LAN connection owner while a radio connection is
 * live. It owns no GATT or socket itself; the process runtime does. Not sticky: after process
 * death the connection is restored by presence callbacks or the next foreground activation.
 */
class ConnectedDeviceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * `startForeground` is always the first platform obligation: nothing before it may abort the
     * start, because stopping a `startForegroundService` service first crashes the app. Only a
     * failing `startForeground` itself (API 34 type check) is reported back before stopping.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification()
        try {
            startForeground(ConnectedDeviceServiceHost.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (failure: RuntimeException) {
            ConnectedDeviceServiceHost.onStartFailed?.invoke(failure)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ConnectedDeviceServiceHost.onStarted?.invoke()
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        try {
            ensureChannel()
            ConnectedDeviceServiceHost.notificationProvider?.notification(this, ConnectedDeviceServiceHost.CHANNEL_ID)?.let { return it }
        } catch (failure: RuntimeException) {
            ConnectedDeviceServiceHost.onFailure?.invoke(failure)
        }
        return fallbackNotification()
    }

    override fun onDestroy() {
        ConnectedDeviceServiceHost.onStopped?.invoke()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ConnectedDeviceServiceHost.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(ConnectedDeviceServiceHost.CHANNEL_ID, applicationInfo.loadLabel(packageManager),
                NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun fallbackNotification(): Notification =
        Notification.Builder(this, ConnectedDeviceServiceHost.CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(applicationInfo.loadLabel(packageManager))
            .setOngoing(true)
            .build()
}

/** Starts/stops [ConnectedDeviceService], mapping platform refusals to typed outcomes. */
class AndroidForegroundServiceStarter(context: Context) : ForegroundServiceStarter {
    private val context = context.applicationContext
    private val intent get() = Intent(context, ConnectedDeviceService::class.java)

    /** The API 34 type check happens later, inside `startForeground` (see `onStartFailed`). */
    override fun start(): StartOutcome = try {
        context.startForegroundService(intent)
        StartOutcome.Started
    } catch (refused: ForegroundServiceStartNotAllowedException) {
        StartOutcome.BackgroundStartNotAllowed
    } catch (failure: SecurityException) {
        StartOutcome.Failed(failure)
    } catch (failure: IllegalStateException) {
        StartOutcome.Failed(failure)
    }

    override fun stop() {
        context.stopService(intent)
    }
}
