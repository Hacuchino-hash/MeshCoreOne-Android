// PortedFrom: MC1Services/Sources/MC1Services/Services/ChannelService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MeshTransportError
import com.meshcoreone.android.core.protocol.transport.mock.MockTransportException
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportError
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportException
import com.meshcoreone.android.core.contracts.domain.ChannelSyncErrorType
import kotlin.time.Duration
import kotlinx.coroutines.delay

/** Channel service failures; messages match the source `errorDescription` strings. */
sealed class ChannelServiceError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : ChannelServiceError("Not connected to device.")
    class ChannelNotFound : ChannelServiceError("Channel not found.")
    class InvalidChannelIndex : ChannelServiceError("Invalid channel index.")
    class SecretHashingFailed : ChannelServiceError("Failed to hash channel secret.")
    class SaveFailed(val reason: String) : ChannelServiceError("Failed to save channel: $reason")
    class SendFailed(val reason: String) : ChannelServiceError("Send failed: $reason")
    class SessionError(val error: MeshCoreException) : ChannelServiceError(error.describe(), error)
    class SyncAlreadyInProgress : ChannelServiceError("Channel sync is already in progress.")
    class CircuitBreakerOpen(val consecutiveFailures: Int) :
        ChannelServiceError("Channel sync suspended after $consecutiveFailures consecutive failures.")
}

/**
 * Narrow port of the `RxLogService.updateChannels(from:)` dependency: channel writes and syncs
 * forward the fresh channel list so captured packets can be decrypted. The RX log service is
 * owned by another work package; its adapter implements this role when it lands.
 */
fun interface ChannelDecryptionCache {
    suspend fun updateChannels(channels: List<ChannelDTO>)
}

/** Fired when slots' occupants change (vacated or rewritten with a different secret). */
typealias SlotOccupantChangedHandler = suspend (RadioId, Set<UByte>) -> Unit

/** Injectable sleep for retry backoff, so tests never wait on wall time. */
fun interface ChannelServiceClock {
    suspend fun sleep(duration: Duration)

    companion object {
        val SYSTEM: ChannelServiceClock = ChannelServiceClock { delay(it) }
    }
}

/**
 * Classifies platform transport failures the services module cannot see (for example the BLE
 * transport's exception type). Returning null falls back to the source classification order.
 */
fun interface ChannelTransportFailureClassifier {
    fun classify(failure: Throwable): ChannelSyncErrorType?

    companion object {
        val NONE: ChannelTransportFailureClassifier = ChannelTransportFailureClassifier { null }
    }
}

internal fun Throwable.describe(): String = message ?: javaClass.simpleName

/**
 * Source `classifyError(_:forIndex:)` transport branches. `MockTransportException` is the
 * protocol mock's analogue of the source mock's `MeshTransportError` throws.
 */
internal fun classifyTransportFailure(error: Throwable): Pair<ChannelSyncErrorType, String>? = when (error) {
    is WiFiTransportException -> when (error.error) {
        WiFiTransportError.SendTimeout -> ChannelSyncErrorType.SendTimeout to "Send timed out"
        WiFiTransportError.NotConnected, is WiFiTransportError.ConnectionFailed,
        WiFiTransportError.ConnectionTimeout, is WiFiTransportError.SendFailed,
        -> ChannelSyncErrorType.TransportError to error.describe()
        WiFiTransportError.InvalidHost, WiFiTransportError.InvalidPort, WiFiTransportError.NotConfigured,
        -> ChannelSyncErrorType.Unknown to error.describe()
    }
    is MeshTransportError -> ChannelSyncErrorType.TransportError to error.describe()
    is MockTransportException -> ChannelSyncErrorType.TransportError to error.describe()
    else -> null
}
