// AndroidOnly: WP-210 Narrow store, session and config ports for the repeater/room admin, room server and binary protocol services over the merged contracts/protocol APIs.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.ContactPersisting
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.RoomPersisting
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.MessageSentInfo
import com.meshcoreone.android.core.protocol.session.ContactSessionOps
import com.meshcoreone.android.core.protocol.session.DiagnosticsSessionOps
import com.meshcoreone.android.core.protocol.session.SessionEventStreaming

/**
 * The slice of Swift's `PersistenceStoreProtocol` the admin and room-server services touch, on top of
 * [RemoteNodeStore] so one object can back both RemoteNodeService and these services. Signatures match
 * [RoomPersisting] and [ContactPersisting] exactly; production wiring passes the real store through
 * [remoteAdminStore]. Swift session UUIDs become [EntityKey]s because the store partitions by radio.
 */
interface RemoteAdminStore : RemoteNodeStore {
    suspend fun fetchRemoteNodeSessionByPrefix(radioId: RadioId, prefix: Bytes): RemoteNodeSessionDTO?
    suspend fun markRoomSessionConnected(key: EntityKey): Boolean
    suspend fun updateRoomActivity(key: EntityKey, syncTimestamp: UInt? = null)
    suspend fun saveRoomMessage(radioId: RadioId, dto: RoomMessageDTO)
    suspend fun fetchRoomMessage(key: EntityKey): RoomMessageDTO?
    suspend fun fetchRoomMessages(session: EntityKey, limit: Long? = null, offset: Long? = null): SnapshotList<RoomMessageDTO>
    suspend fun isDuplicateRoomMessage(session: EntityKey, deduplicationKey: String): Boolean
    suspend fun updateRoomMessageStatus(key: EntityKey, status: MessageStatus, ackCode: UInt?, roundTripTime: UInt?)
    suspend fun updateRoomMessageRetryStatus(key: EntityKey, status: MessageStatus, retryAttempt: Long, maxRetryAttempts: Long)
    suspend fun incrementRoomUnreadCount(key: EntityKey)
    suspend fun resetRoomUnreadCount(key: EntityKey)
    suspend fun findContactNameByKeyPrefix(prefix: Bytes): String?
    suspend fun findContactByPublicKey(publicKey: Bytes): ContactDTO?
}

/** Production adapter: the persistence store implements both contracts (`PersistenceStoreProtocol`). */
fun remoteAdminStore(rooms: RoomPersisting, contacts: ContactPersisting): RemoteAdminStore =
    object : RemoteAdminStore, RemoteNodeStore by remoteNodeStore(rooms, contacts) {
        override suspend fun fetchRemoteNodeSessionByPrefix(radioId: RadioId, prefix: Bytes) =
            rooms.fetchRemoteNodeSessionByPrefix(radioId, prefix)
        override suspend fun markRoomSessionConnected(key: EntityKey) = rooms.markRoomSessionConnected(key)
        override suspend fun updateRoomActivity(key: EntityKey, syncTimestamp: UInt?) =
            rooms.updateRoomActivity(key, syncTimestamp)
        override suspend fun saveRoomMessage(radioId: RadioId, dto: RoomMessageDTO) = rooms.saveRoomMessage(radioId, dto)
        override suspend fun fetchRoomMessage(key: EntityKey) = rooms.fetchRoomMessage(key)
        override suspend fun fetchRoomMessages(session: EntityKey, limit: Long?, offset: Long?) =
            rooms.fetchRoomMessages(session, limit, offset)
        override suspend fun isDuplicateRoomMessage(session: EntityKey, deduplicationKey: String) =
            rooms.isDuplicateRoomMessage(session, deduplicationKey)
        override suspend fun updateRoomMessageStatus(key: EntityKey, status: MessageStatus, ackCode: UInt?, roundTripTime: UInt?) =
            rooms.updateRoomMessageStatus(key, status, ackCode, roundTripTime)
        override suspend fun updateRoomMessageRetryStatus(
            key: EntityKey, status: MessageStatus, retryAttempt: Long, maxRetryAttempts: Long,
        ) = rooms.updateRoomMessageRetryStatus(key, status, retryAttempt, maxRetryAttempts)
        override suspend fun incrementRoomUnreadCount(key: EntityKey) = rooms.incrementRoomUnreadCount(key)
        override suspend fun resetRoomUnreadCount(key: EntityKey) = rooms.resetRoomUnreadCount(key)
        override suspend fun findContactNameByKeyPrefix(prefix: Bytes) = contacts.findContactNameByKeyPrefix(prefix)
        override suspend fun findContactByPublicKey(publicKey: Bytes) = contacts.findContactByPublicKey(publicKey)
    }

/**
 * Swift's `any DiagnosticsSessionOps & SessionEventStreaming & ContactSessionOps` for
 * BinaryProtocolService; a real `MeshCoreSession` is adapted with [asBinaryProtocolSessionPort].
 */
interface BinaryProtocolSessionPort : DiagnosticsSessionOps, SessionEventStreaming, ContactSessionOps {
    // Declared by both DiagnosticsSessionOps and ContactSessionOps.
    override suspend fun sendPathDiscovery(destination: Bytes): MessageSentInfo
}

/** Adapts any session implementing the three protocol roles (for example `MeshCoreSession`). */
fun <S> S.asBinaryProtocolSessionPort(): BinaryProtocolSessionPort
    where S : DiagnosticsSessionOps, S : SessionEventStreaming, S : ContactSessionOps =
    this as? BinaryProtocolSessionPort ?: DelegatingBinaryProtocolSessionPort(this, this, this)

private class DelegatingBinaryProtocolSessionPort(
    private val diagnostics: DiagnosticsSessionOps,
    events: SessionEventStreaming,
    contacts: ContactSessionOps,
) : BinaryProtocolSessionPort, DiagnosticsSessionOps by diagnostics, SessionEventStreaming by events,
    ContactSessionOps by contacts {
    override suspend fun sendPathDiscovery(destination: Bytes): MessageSentInfo = diagnostics.sendPathDiscovery(destination)
}

/**
 * The three retry knobs RoomServerService reads from Swift's `MessageServiceConfig` (owned by WP-208,
 * whose port will construct this from its config). Defaults and the `maxAttempts <= 5` precondition
 * match the Swift initializer: 5 = 4 direct + 1 flood.
 */
data class RoomMessageRetryConfig(
    val maxAttempts: Long = 5,
    val floodAfter: Long = 4,
    val maxFloodAttempts: Long = 1,
) {
    init {
        require(maxAttempts <= 5) { "maxAttempts must be <= 5 (4 direct + 1 flood)" }
    }
}
