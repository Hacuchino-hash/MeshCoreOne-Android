// PortedFrom: MC1Services/Sources/MC1Services/Protocols/AppStateProvider.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Protocols/ChannelServiceProtocol.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Protocols/ContactServiceProtocol.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Protocols/MessagePollingServiceProtocol.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/ChannelService.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/ContactService.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/ServiceContainer.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.contracts.domain

import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.event.ChannelMessage
import com.meshcoreone.android.core.protocol.event.ContactMessage
import java.time.Duration
import java.time.Instant

interface AppStateProvider { suspend fun isInForeground(): Boolean }

data class ContactSyncResult(val contactsReceived: Long, val lastSyncTimestamp: UInt, val isIncremental: Boolean)

sealed interface ChannelSyncErrorType {
    data object Timeout : ChannelSyncErrorType
    data object SendTimeout : ChannelSyncErrorType
    data object TransportError : ChannelSyncErrorType
    data object CircuitBreaker : ChannelSyncErrorType
    data class DeviceError(val code: UByte) : ChannelSyncErrorType
    data object DatabaseError : ChannelSyncErrorType
    data object Unknown : ChannelSyncErrorType
}

data class ChannelSyncError(val index: UByte, val errorType: ChannelSyncErrorType, val description: String) {
    val isRetryable: Boolean get() = errorType == ChannelSyncErrorType.Timeout || errorType == ChannelSyncErrorType.SendTimeout
    val countsTowardCircuitBreaker: Boolean get() = isRetryable || errorType == ChannelSyncErrorType.TransportError
}

data class ChannelSyncResult(val channelsSynced: Long, val errors: SnapshotList<ChannelSyncError> = SnapshotList.empty()) {
    val isComplete: Boolean get() = errors.isEmpty()
    val requestTimeoutCount: Long get() = errors.count { it.errorType == ChannelSyncErrorType.Timeout }.toLong()
    val sendTimeoutCount: Long get() = errors.count { it.errorType == ChannelSyncErrorType.SendTimeout }.toLong()
    val circuitBreakerAborted: Boolean get() = errors.any { it.errorType == ChannelSyncErrorType.CircuitBreaker }
    val retryableIndices: SnapshotList<UByte> get() = errors.filter { it.isRetryable }.map { it.index }.snapshot()
}

interface ChannelServiceProtocol {
    suspend fun syncChannels(radioId: RadioId, maxChannels: UByte, usePipelinedRead: Boolean = false): ChannelSyncResult
    suspend fun retryFailedChannels(radioId: RadioId, indices: SnapshotList<UByte>): ChannelSyncResult
}

interface ContactServiceProtocol {
    suspend fun syncContacts(radioId: RadioId, since: Instant?): ContactSyncResult
}

interface MessagePollingServiceProtocol {
    suspend fun pollAllMessages(): Long
    suspend fun waitForPendingHandlers(timeout: Duration): Boolean
    suspend fun startAutoFetch(radioId: RadioId)
    suspend fun pauseAutoFetch()
    suspend fun resumeAutoFetch()
    suspend fun setContactMessageHandler(handler: suspend (ContactMessage, ContactDTO?, DeliveryContext) -> Unit)
    suspend fun setChannelMessageHandler(handler: suspend (ChannelMessage, ChannelDTO?, DeliveryContext) -> Unit)
    suspend fun setSignedMessageHandler(handler: suspend (ContactMessage, ContactDTO?) -> Unit)
    suspend fun setCLIMessageHandler(handler: suspend (ContactMessage, ContactDTO?) -> Unit)
    suspend fun clearMessageHandlers()
}
