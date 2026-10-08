// AndroidOnly: WP-214 ConnectionManager stand-in implementing SyncRetryHost (Swift ConnectionManager.createForTesting/setTestState).
package com.meshcoreone.android.core.services.sync

import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.DevicePlatform
import com.meshcoreone.android.core.model.RadioId
import java.time.Instant
import java.util.Collections
import java.util.UUID

/**
 * Models the ConnectionManager members the retry policy touches. [disconnect] mirrors the source
 * `disconnect(reason:)` effects the loops observe: it cancels both loops, drops the service graph and
 * publishes `.disconnected`.
 */
internal class FakeSyncRetryHost : SyncRetryHost {
    @Volatile override var connectionIntent: ConnectionIntent = ConnectionIntent.WantsConnection()
    @Volatile override var connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED
    @Volatile override var currentServices: SyncRetryServices? = null
    @Volatile override var detectedPlatform: DevicePlatform = DevicePlatform.UNKNOWN
    @Volatile override var lastCleanChannelSync: Pair<RadioId, Instant>? = null
    @Volatile override var lastAttemptedChannelSync: Pair<RadioId, Instant>? = null
    @Volatile var controller: SyncRetryController? = null

    val awaitingReauth: MutableSet<UUID> = Collections.synchronizedSet(LinkedHashSet())
    val resyncFailed = CallTracker()
    val deviceSynced = CallTracker()
    val timeSyncs = CallTracker()
    val disconnects: MutableList<String> = Collections.synchronizedList(ArrayList())

    override fun storeDetectedPlatform(platform: DevicePlatform) {
        detectedPlatform = platform
    }

    override fun sessionsAwaitingReauth(): Set<UUID> = synchronized(awaitingReauth) { awaitingReauth.toSet() }
    override fun consumeSessionsAwaitingReauth(ids: Set<UUID>) {
        awaitingReauth.removeAll(ids)
    }

    override suspend fun promoteToReadyAfterResync() {
        connectionState = DeviceConnectionState.READY
    }

    override suspend fun syncDeviceTimeIfNeeded() = timeSyncs.markCalled()
    override suspend fun onDeviceSynced() = deviceSynced.markCalled()
    override fun onResyncFailed() = resyncFailed.markCalled()
    override suspend fun disconnectAfterResyncFailure() = disconnect("resyncFailed")

    fun disconnect(reason: String) {
        disconnects += reason
        controller?.cancelResyncLoop()
        controller?.cancelChannelRetry()
        currentServices = null
        connectionState = DeviceConnectionState.DISCONNECTED
        if (reason == "userInitiated") connectionIntent = ConnectionIntent.UserDisconnected
    }
}

/** Swift `ConnectionManager.createForTesting()`: a controller bound to a fresh fake host. */
internal fun SyncTestScope.retryController(host: FakeSyncRetryHost = FakeSyncRetryHost()): Pair<SyncRetryController, FakeSyncRetryHost> {
    val controller = SyncRetryController(host, scope, clock)
    host.controller = controller
    return controller to host
}
