// AndroidOnly: WP-217 in-memory SimulatorSeedStore with the Swift PersistenceStore semantics the seed relies on.
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
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
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.model.snapshotSet
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant
import java.util.UUID

/**
 * Stands in for the Swift in-memory `PersistenceStore` (`core:services` cannot depend on `core:data`):
 * - contacts, channels, messages, reactions and repeats upsert on their id (SwiftData `@Attribute(.unique)`);
 * - `saveContact` writes `avatarImageData` as given, null included;
 * - `saveMessage` inserts `Message(dto:)`, which drops the preview image/icon blobs and resets `linkPreviewFetched`
 *   (URL, title and `reactionSummary` are kept);
 * - `saveMessageRepeat` requires the parent message;
 * - `saveRxLogEntry` is insert-only, so a duplicate save appends a second row;
 * - snapshots are inserted by `batchInsertNodeStatusSnapshots`, skipping known (key, millisecond) pairs.
 *
 * [failOn] makes a named operation throw (a store error, or a `CancellationException`) to prove propagation.
 */
internal class SimulatorInMemorySeedStore : SimulatorSeedStore {
    private val lock = Any()
    private var devices: Map<UUID, DeviceDTO> = emptyMap()
    private var contacts: Map<EntityKey, ContactDTO> = emptyMap()
    private var channels: Map<UUID, ChannelDTO> = emptyMap()
    private var messages: Map<UUID, MessageDTO> = emptyMap()
    private var reactions: Map<UUID, ReactionDTO> = emptyMap()
    private var repeats: Map<UUID, MessageRepeatDTO> = emptyMap()
    private var rxEntries: List<RxLogEntryDTO> = emptyList()
    private var snapshots: List<NodeStatusSnapshotDTO> = emptyList()
    private var failures: Map<String, () -> Throwable> = emptyMap()
    private var calls: List<String> = emptyList()

    fun failOn(operation: String, error: () -> Throwable = { PersistenceStoreException(PersistenceStoreError.SaveFailed(operation)) }) {
        synchronized(lock) { failures = failures + (operation to error) }
    }

    val recordedCalls: List<String> get() = synchronized(lock) { calls }

    private fun record(operation: String) {
        val failure = synchronized(lock) {
            calls = calls + operation
            failures[operation]
        }
        if (failure != null) throw failure()
    }

    // MARK: - SimulatorSeedStore

    override suspend fun saveDevice(dto: DeviceDTO) {
        record("saveDevice")
        synchronized(lock) { devices = devices + (dto.id to dto) }
    }

    override suspend fun fetchContact(key: EntityKey): ContactDTO? {
        record("fetchContact")
        return synchronized(lock) { contacts[key] }
    }

    override suspend fun saveContact(dto: ContactDTO) {
        record("saveContact")
        synchronized(lock) { contacts = contacts + (EntityKey(dto.radioId, dto.id) to dto) }
    }

    override suspend fun saveChannel(dto: ChannelDTO) {
        record("saveChannel")
        synchronized(lock) { channels = channels + (dto.id to dto) }
    }

    override suspend fun saveMessage(dto: MessageDTO) {
        record("saveMessage")
        val stored = dto.copy(linkPreviewImageData = null, linkPreviewIconData = null, linkPreviewFetched = false)
        synchronized(lock) { messages = messages + (dto.id to stored) }
    }

    override suspend fun updateMessageLinkPreview(
        key: EntityKey, url: String?, title: String?, imageData: Bytes?, iconData: Bytes?, fetched: Boolean,
    ) {
        record("updateMessageLinkPreview")
        mutateMessage(key) {
            it.copy(
                linkPreviewURL = url, linkPreviewTitle = title, linkPreviewImageData = imageData,
                linkPreviewIconData = iconData, linkPreviewFetched = fetched,
            )
        }
    }

    override suspend fun saveReaction(dto: ReactionDTO) {
        record("saveReaction")
        synchronized(lock) { reactions = reactions + (dto.id to dto) }
    }

    override suspend fun updateMessageReactionSummary(message: EntityKey, summary: String?) {
        record("updateMessageReactionSummary")
        mutateMessage(message) { it.copy(reactionSummary = summary) }
    }

    override suspend fun saveMessageRepeat(radioId: RadioId, dto: MessageRepeatDTO) {
        record("saveMessageRepeat")
        synchronized(lock) {
            val parent = messages[dto.messageID]
            if (parent == null || parent.radioId != radioId) throw PersistenceStoreException(PersistenceStoreError.MessageNotFound)
            repeats = repeats + (dto.id to dto)
        }
    }

    /** When set, the next `fetchRxLogEntries` signals [RxGate.reached] and suspends until [RxGate.release]. */
    var rxGate: RxGate? = null

    class RxGate {
        val reached = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
    }

    override suspend fun fetchRxLogEntries(radioId: RadioId, limit: Long): SnapshotList<RxLogEntryDTO> {
        record("fetchRxLogEntries")
        val gate = synchronized(lock) { rxGate.also { rxGate = null } }
        if (gate != null) {
            gate.reached.complete(Unit)
            gate.release.await()
        }
        return synchronized(lock) {
            rxEntries.filter { it.radioId == radioId }.sortedByDescending { it.receivedAt }.take(limit.toInt()).snapshot()
        }
    }

    override suspend fun saveRxLogEntry(dto: RxLogEntryDTO) {
        record("saveRxLogEntry")
        synchronized(lock) { rxEntries = rxEntries + dto }
    }

    override suspend fun fetchLatestNodeStatusSnapshot(nodePublicKey: Bytes): NodeStatusSnapshotDTO? {
        record("fetchLatestNodeStatusSnapshot")
        return synchronized(lock) { snapshots.filter { it.nodePublicKey == nodePublicKey }.maxByOrNull { it.timestamp } }
    }

    override suspend fun existingNodeStatusSnapshotKeys(): SnapshotSet<NodeStatusSnapshotKey> {
        record("existingNodeStatusSnapshotKeys")
        return synchronized(lock) { snapshots.map { NodeStatusSnapshotKey.of(it) }.snapshotSet() }
    }

    override suspend fun batchInsertNodeStatusSnapshots(
        dtos: SnapshotList<NodeStatusSnapshotDTO>, existingKeys: SnapshotSet<NodeStatusSnapshotKey>,
    ): NodeStatusSnapshotInsertResult {
        record("batchInsertNodeStatusSnapshots")
        val known = existingKeys.toMutableSet()
        var inserted = 0L
        var skipped = 0L
        val accepted = mutableListOf<NodeStatusSnapshotDTO>()
        for (dto in dtos) {
            if (!known.add(NodeStatusSnapshotKey.of(dto))) {
                skipped++
                continue
            }
            accepted += dto
            inserted++
        }
        synchronized(lock) { snapshots = snapshots + accepted }
        return NodeStatusSnapshotInsertResult(inserted, skipped)
    }

    // MARK: - Test-side reads (the Swift tests' PersistenceStore fetches)

    fun device(id: UUID): DeviceDTO? = synchronized(lock) { devices[id] }

    fun fetchContacts(radioId: RadioId): List<ContactDTO> = synchronized(lock) { contacts.values.filter { it.radioId == radioId } }

    fun fetchChannels(radioId: RadioId): List<ChannelDTO> = synchronized(lock) { channels.values.filter { it.radioId == radioId } }

    fun fetchMessage(id: UUID): MessageDTO? = synchronized(lock) { messages[id] }

    fun allMessages(): List<MessageDTO> = synchronized(lock) { messages.values.toList() }

    fun fetchReactions(messageID: UUID): List<ReactionDTO> = synchronized(lock) { reactions.values.filter { it.messageID == messageID } }

    fun allReactions(): List<ReactionDTO> = synchronized(lock) { reactions.values.toList() }

    fun fetchMessageRepeats(messageID: UUID): List<MessageRepeatDTO> =
        synchronized(lock) { repeats.values.filter { it.messageID == messageID }.sortedBy { it.receivedAt } }

    fun allRepeats(): List<MessageRepeatDTO> = synchronized(lock) { repeats.values.toList() }

    fun allRxEntries(): List<RxLogEntryDTO> = synchronized(lock) { rxEntries }

    fun fetchNodeStatusSnapshots(nodePublicKey: Bytes, since: Instant?): List<NodeStatusSnapshotDTO> = synchronized(lock) {
        snapshots.filter { it.nodePublicKey == nodePublicKey && (since == null || it.timestamp >= since) }.sortedBy { it.timestamp }
    }

    fun insertSnapshot(dto: NodeStatusSnapshotDTO) = synchronized(lock) { snapshots = snapshots + dto }

    fun insertRxEntry(dto: RxLogEntryDTO) = synchronized(lock) { rxEntries = rxEntries + dto }

    private fun mutateMessage(key: EntityKey, transform: (MessageDTO) -> MessageDTO) {
        synchronized(lock) {
            val existing = messages[key.id]?.takeIf { it.radioId == key.radioId } ?: return
            messages = messages + (key.id to transform(existing))
        }
    }
}
