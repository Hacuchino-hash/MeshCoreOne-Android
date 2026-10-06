// AndroidOnly: WP-210 Narrow collaborator ports for RemoteNodeService over the merged protocol/contracts APIs, so it never depends on unmerged keychain, audit-log or persistence implementations.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.ContactPersisting
import com.meshcoreone.android.core.contracts.domain.ContactSaveResult
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.RoomPersisting
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.ContactFrame
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.MessageSentInfo
import com.meshcoreone.android.core.protocol.session.ContactSessionOps
import com.meshcoreone.android.core.protocol.session.RemoteAccessSessionOps
import com.meshcoreone.android.core.protocol.session.SessionEventStreaming

/**
 * Swift's `any RemoteAccessSessionOps & SessionEventStreaming & ContactSessionOps`. Kotlin has no
 * existential intersection type, so this interface names it; a real `MeshCoreSession` is adapted with
 * [asRemoteNodeSessionPort].
 */
interface RemoteNodeSessionPort : RemoteAccessSessionOps, SessionEventStreaming, ContactSessionOps {
    // Declared by both RemoteAccessSessionOps and ContactSessionOps.
    override suspend fun sendPathDiscovery(destination: Bytes): MessageSentInfo
}

/** Adapts any session implementing the three protocol roles (for example `MeshCoreSession`). */
fun <S> S.asRemoteNodeSessionPort(): RemoteNodeSessionPort
    where S : RemoteAccessSessionOps, S : SessionEventStreaming, S : ContactSessionOps =
    this as? RemoteNodeSessionPort ?: DelegatingSessionPort(this, this, this)

private class DelegatingSessionPort(
    private val remote: RemoteAccessSessionOps,
    events: SessionEventStreaming,
    contacts: ContactSessionOps,
) : RemoteNodeSessionPort, RemoteAccessSessionOps by remote, SessionEventStreaming by events,
    ContactSessionOps by contacts {
    override suspend fun sendPathDiscovery(destination: Bytes): MessageSentInfo = remote.sendPathDiscovery(destination)
}

/**
 * The slice of Swift's `PersistenceStoreProtocol` that RemoteNodeService touches. Signatures match
 * [RoomPersisting] and [ContactPersisting] exactly; production wiring passes the real store through
 * [remoteNodeStore]. Swift's UUID session ids become [EntityKey]s because the Android store partitions
 * sessions by radio.
 */
interface RemoteNodeStore {
    suspend fun fetchRemoteNodeSession(key: EntityKey): RemoteNodeSessionDTO?
    suspend fun fetchRemoteNodeSessions(radioId: RadioId): SnapshotList<RemoteNodeSessionDTO>
    suspend fun fetchConnectedRemoteNodeSessions(radioId: RadioId): SnapshotList<RemoteNodeSessionDTO>
    suspend fun saveRemoteNodeSessionDTO(dto: RemoteNodeSessionDTO)
    suspend fun updateRemoteNodeSessionConnection(key: EntityKey, isConnected: Boolean, permissionLevel: RoomPermissionLevel)
    suspend fun cleanupDuplicateRemoteNodeSessions(publicKey: Bytes, keep: EntityKey)
    suspend fun deleteRemoteNodeSession(key: EntityKey)
    suspend fun markSessionDisconnected(key: EntityKey)
    suspend fun fetchContact(radioId: RadioId, publicKey: Bytes): ContactDTO?
    suspend fun saveContact(radioId: RadioId, frame: ContactFrame): ContactSaveResult
}

/** Production adapter: the persistence store implements both contracts (`PersistenceStoreProtocol`). */
fun remoteNodeStore(rooms: RoomPersisting, contacts: ContactPersisting): RemoteNodeStore = object : RemoteNodeStore {
    override suspend fun fetchRemoteNodeSession(key: EntityKey) = rooms.fetchRemoteNodeSession(key)
    override suspend fun fetchRemoteNodeSessions(radioId: RadioId) = rooms.fetchRemoteNodeSessions(radioId)
    override suspend fun fetchConnectedRemoteNodeSessions(radioId: RadioId) = rooms.fetchConnectedRemoteNodeSessions(radioId)
    override suspend fun saveRemoteNodeSessionDTO(dto: RemoteNodeSessionDTO) = rooms.saveRemoteNodeSessionDTO(dto)
    override suspend fun updateRemoteNodeSessionConnection(
        key: EntityKey, isConnected: Boolean, permissionLevel: RoomPermissionLevel,
    ) = rooms.updateRemoteNodeSessionConnection(key, isConnected, permissionLevel)
    override suspend fun cleanupDuplicateRemoteNodeSessions(publicKey: Bytes, keep: EntityKey) =
        rooms.cleanupDuplicateRemoteNodeSessions(publicKey, keep)
    override suspend fun deleteRemoteNodeSession(key: EntityKey) = rooms.deleteRemoteNodeSession(key)
    override suspend fun markSessionDisconnected(key: EntityKey) = rooms.markSessionDisconnected(key)
    override suspend fun fetchContact(radioId: RadioId, publicKey: Bytes) = contacts.fetchContact(radioId, publicKey)
    override suspend fun saveContact(radioId: RadioId, frame: ContactFrame) = contacts.saveContact(radioId, frame)
}

/**
 * Swift `KeychainService`'s node-password surface. Implemented by the WP-204 secure-storage port
 * (Keystore-backed); passwords are keyed by the node's full public key.
 */
interface RemoteNodePasswordStore {
    suspend fun storePassword(password: String, publicKey: Bytes)
    suspend fun retrievePassword(publicKey: Bytes): String?
    suspend fun hasPassword(publicKey: Bytes): Boolean
    suspend fun deletePassword(publicKey: Bytes)
}

/** Swift `CommandAuditLogger.Target`. */
enum class RemoteAuditTarget(val rawValue: String) { REPEATER("REPEATER"), ROOM("ROOM") }

/**
 * The `CommandAuditLogger` calls RemoteNodeService makes. Implemented by the WP-212 audit-logger port;
 * every method defaults to a no-op so the service runs before that port lands.
 */
interface RemoteCommandAuditLog {
    suspend fun logLoginRequest(target: RemoteAuditTarget, publicKey: Bytes, pathLength: UByte) {}
    suspend fun logLoginSuccess(target: RemoteAuditTarget, publicKey: Bytes, isAdmin: Boolean) {}
    suspend fun logLoginFailed(target: RemoteAuditTarget, publicKey: Bytes, reason: String) {}
    suspend fun logLogout(target: RemoteAuditTarget, publicKey: Bytes) {}
    suspend fun logStatusRequest(target: RemoteAuditTarget, publicKey: Bytes) {}
    suspend fun logTelemetryRequest(target: RemoteAuditTarget, publicKey: Bytes) {}
    suspend fun logCLICommand(publicKey: Bytes, command: String) {}
    suspend fun logKeepAlive(target: RemoteAuditTarget, publicKey: Bytes) {}

    companion object {
        val NONE: RemoteCommandAuditLog = object : RemoteCommandAuditLog {}
    }
}

internal val RemoteNodeSessionDTO.auditTarget: RemoteAuditTarget
    get() = if (isRoom) RemoteAuditTarget.ROOM else RemoteAuditTarget.REPEATER

internal val RemoteNodeSessionDTO.remoteSessionKey: EntityKey get() = EntityKey(radioId, id)
