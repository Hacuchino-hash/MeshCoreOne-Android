// AndroidOnly: WP-303 SyncRetryHost over the runtime connection snapshot; the resync outcome feeds the runtime's initialSync result.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.ConnectionSignals
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.DevicePlatform
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.runtime.ConnectionError
import com.meshcoreone.android.core.runtime.RuntimeDisconnectReason
import com.meshcoreone.android.core.services.sync.SyncRetryHost
import com.meshcoreone.android.core.services.sync.SyncRetryServices
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred

/** How the resync loop ended, as the runtime's `initialSync` must report it. */
sealed interface ResyncOutcome {
    data object Promoted : ResyncOutcome
    data class Exhausted(val cause: Throwable) : ResyncOutcome
}

/**
 * Binds the sync retry policy to one generation of the runtime connection manager. The runtime owns state
 * publication, so "promote to ready after resync" cannot write `READY` here (no generation-checked runtime
 * entry point exists); instead the resync outcome completes [outcome], `initialSync` returns it to the
 * runtime, and the runtime's own promotion (device time, room re-auth, `onDeviceSynced`, listeners) runs
 * exactly once. State is therefore never claimed `READY` before the runtime says so.
 */
internal class SessionResyncHost(
    private val signals: ConnectionSignals,
    private val runtimeState: RuntimeSyncState,
    private val resyncFailure: ResyncFailureSignal,
    private val services: () -> SyncRetryServices?,
) : SyncRetryHost {
    private val lock = Any()
    private var pending: CompletableDeferred<ResyncOutcome>? = null

    /** Opens the outcome for one `initialSync` attempt; a previous unresolved outcome is abandoned. */
    fun beginOutcome(): CompletableDeferred<ResyncOutcome> = CompletableDeferred<ResyncOutcome>().also {
        synchronized(lock) { pending = it }
    }

    fun abandonOutcome() {
        synchronized(lock) { pending.also { pending = null } }?.cancel()
    }

    private fun resolve(outcome: ResyncOutcome) {
        synchronized(lock) { pending }?.complete(outcome)
    }

    override val connectionIntent: ConnectionIntent get() = signals.snapshot.value.intent
    override val connectionState: DeviceConnectionState get() = signals.snapshot.value.state
    override val currentServices: SyncRetryServices? get() = services()
    override val detectedPlatform: DevicePlatform get() = runtimeState.detectedPlatform
    override val lastCleanChannelSync: Pair<RadioId, Instant>? get() = runtimeState.lastCleanChannelSync
    override val lastAttemptedChannelSync: Pair<RadioId, Instant>? get() = runtimeState.lastAttemptedChannelSync

    // The runtime already resolves the platform at connect (WiFi implies ESP32); nothing to store.
    override fun storeDetectedPlatform(platform: DevicePlatform) = Unit

    // Room sessions torn down for reconnect are re-authenticated by the runtime after a usable sync.
    override fun sessionsAwaitingReauth(): Set<UUID> = emptySet()
    override fun consumeSessionsAwaitingReauth(ids: Set<UUID>) = Unit

    override suspend fun promoteToReadyAfterResync() = resolve(ResyncOutcome.Promoted)

    // The runtime's promotion performs device-time sync and onDeviceSynced once the outcome is Promoted.
    override suspend fun syncDeviceTimeIfNeeded() = Unit
    override suspend fun onDeviceSynced() = Unit

    override fun onResyncFailed() = resyncFailure.resyncFailed()

    override suspend fun disconnectAfterResyncFailure() {
        resolve(ResyncOutcome.Exhausted(ConnectionError.InitializationFailed("resync failed after 3 attempts")))
        // The disconnect cancels this very loop and waits on the connect operation, so it cannot run inline.
        runtimeState.requestDisconnect(RuntimeDisconnectReason.RESYNC_FAILED)
    }
}
