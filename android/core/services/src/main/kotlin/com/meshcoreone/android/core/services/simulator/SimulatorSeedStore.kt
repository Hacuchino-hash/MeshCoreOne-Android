// AndroidOnly: WP-217 narrow persistence port for the simulator seed; WP-303 adapts core:data's store to it.
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageRepeatDTO
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.ReactionDTO
import com.meshcoreone.android.core.model.RxLogEntryDTO
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.SnapshotSet
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant

/**
 * The exact persistence surface `SimulatorConnectionMode.seedDataStore` uses on the Swift `PersistenceStore`.
 *
 * Every member except the last two has the same signature as the `core:contracts` persistence interface it
 * mirrors (`DevicePersisting`, `ContactPersisting`, `ChannelPersisting`, `MessagePersisting`,
 * `ReactionPersisting`, `HeardRepeatPersisting`, `RxLogPersisting`, `NodeSnapshotPersisting`), so an adapter
 * over the real store is one delegation per member. The snapshot pair mirrors the Swift backup-import helpers
 * (`existingNodeStatusSnapshotKeys`, `batchInsertNodeStatusSnapshots`), which have no contract yet.
 *
 * Semantics the seed relies on (from the Swift store): `saveContact`/`saveChannel`/`saveMessage`/`saveReaction`
 * and `saveMessageRepeat` upsert on the row id; `saveContact` overwrites `avatarImageData` (null included);
 * `saveMessage` (Swift `Message(dto:)`) drops the preview image/icon blobs and resets `linkPreviewFetched`, so
 * the seed reapplies its previews with `updateMessageLinkPreview`; `saveMessageRepeat` requires the parent
 * message; `saveRxLogEntry` is insert-only.
 */
interface SimulatorSeedStore {
    suspend fun saveDevice(dto: DeviceDTO)
    suspend fun fetchContact(key: EntityKey): ContactDTO?
    suspend fun saveContact(dto: ContactDTO)
    suspend fun saveChannel(dto: ChannelDTO)
    suspend fun saveMessage(dto: MessageDTO)
    suspend fun updateMessageLinkPreview(
        key: EntityKey, url: String?, title: String?, imageData: Bytes?, iconData: Bytes?, fetched: Boolean,
    )
    suspend fun saveReaction(dto: ReactionDTO)
    suspend fun updateMessageReactionSummary(message: EntityKey, summary: String?)
    suspend fun saveMessageRepeat(radioId: RadioId, dto: MessageRepeatDTO)
    suspend fun fetchRxLogEntries(radioId: RadioId, limit: Long = 500): SnapshotList<RxLogEntryDTO>
    suspend fun saveRxLogEntry(dto: RxLogEntryDTO)
    suspend fun fetchLatestNodeStatusSnapshot(nodePublicKey: Bytes): NodeStatusSnapshotDTO?
    suspend fun existingNodeStatusSnapshotKeys(): SnapshotSet<NodeStatusSnapshotKey>
    suspend fun batchInsertNodeStatusSnapshots(
        dtos: SnapshotList<NodeStatusSnapshotDTO>, existingKeys: SnapshotSet<NodeStatusSnapshotKey>,
    ): NodeStatusSnapshotInsertResult
}

/**
 * Swift `nodeStatusSnapshotKey(nodePublicKey:timestamp:)`: the node key plus the timestamp truncated to whole
 * milliseconds (`Int(timeIntervalSince1970 * 1000)`), deliberately excluding the row id so re-import is
 * idempotent. `core:data` keys its backup restore the same way (`snapshotBackupKey`).
 */
data class NodeStatusSnapshotKey(val nodePublicKey: Bytes, val milliseconds: Long) {
    companion object {
        fun of(nodePublicKey: Bytes, timestamp: Instant): NodeStatusSnapshotKey =
            NodeStatusSnapshotKey(nodePublicKey, swiftMilliseconds(timestamp))

        fun of(dto: NodeStatusSnapshotDTO): NodeStatusSnapshotKey = of(dto.nodePublicKey, dto.timestamp)

        /** Truncation toward zero, as Swift's `Int(Double)` conversion does. */
        private fun swiftMilliseconds(timestamp: Instant): Long {
            val floorMillis = Math.addExact(Math.multiplyExact(timestamp.epochSecond, 1000L), timestamp.nano / 1_000_000L)
            val hasSubMillis = timestamp.nano % 1_000_000 != 0
            return if (timestamp.epochSecond < 0 && hasSubMillis) floorMillis + 1 else floorMillis
        }
    }
}

/** Swift `batchInsertNodeStatusSnapshots` result tuple. */
data class NodeStatusSnapshotInsertResult(val inserted: Long, val skipped: Long)
