// PortedFrom: MC1Services/Sources/MC1Services/Simulator/SimulatorConnectionMode.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.DeviceDTO
import java.time.Clock
import java.time.Instant
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Connection mode for the simulator and demo mode on a device. Provides mock data and a simulated connection
 * without requiring real hardware: nothing here opens a radio or network link.
 *
 * The Swift type is `@MainActor`; here `isConnected` is lock-confined and never held across a suspension,
 * and seed passes are serialized by a coroutine [Mutex] so two concurrent connects cannot interleave the
 * check-then-insert RX-log and snapshot steps. [clock] supplies the single `now` each seed pass is built
 * from and the simulated device's `lastConnected`.
 */
class SimulatorConnectionMode(
    private val clock: Clock = Clock.systemUTC(),
    private val connectDelay: Duration = DEFAULT_CONNECT_DELAY,
) {
    private val lock = Any()
    private var connected = false
    private val seedMutex = Mutex()

    /** Whether the simulator is "connected". */
    val isConnected: Boolean get() = synchronized(lock) { connected }

    /** The simulated device while connected. */
    val device: DeviceDTO? get() = if (isConnected) MockDataProvider.simulatorDevice(clock.instant()) else null

    /**
     * Simulates connecting to the simulator device after a brief delay. Swift's `try? await Task.sleep`
     * swallows cancellation and connects anyway; here cancellation propagates and leaves the mode
     * disconnected.
     */
    suspend fun connect() {
        logger.info("Simulator: connecting to mock device")
        delay(connectDelay)
        synchronized(lock) { connected = true }
        logger.info("Simulator: connected")
    }

    /** Simulates disconnecting. */
    suspend fun disconnect() {
        logger.info("Simulator: disconnecting")
        synchronized(lock) { connected = false }
    }

    /**
     * Seeds the data store with mock data. Each message is saved before its link-preview, reaction, and repeat
     * rows: `saveMessage` does not persist the link-preview or `reactionSummary` columns, and
     * `saveMessageRepeat` needs the parent present. Re-seeding upserts on each row's unique `id`, so it is
     * idempotent. Store failures and cancellation propagate unchanged.
     */
    suspend fun seedDataStore(dataStore: SimulatorSeedStore) {
        seedMutex.withLock { seed(dataStore, clock.instant()) }
    }

    private suspend fun seed(dataStore: SimulatorSeedStore, now: Instant) {
        val radioId = MockDataProvider.simulatorRadioId
        dataStore.saveDevice(MockDataProvider.simulatorDevice(now))

        // Mock contacts omit avatarImageData; copy any existing value onto the upsert so saveContact does not
        // apply null.
        val contacts = MockDataProvider.contacts(now)
        for (contact in contacts) {
            val existingAvatar = dataStore.fetchContact(EntityKey(radioId, contact.id))?.avatarImageData
            dataStore.saveContact(if (existingAvatar != null) contact.withAvatar(existingAvatar) else contact)
        }

        val channels = MockDataProvider.channels(now)
        for (channel in channels) dataStore.saveChannel(channel)

        for (contact in contacts) {
            for (message in MockDataProvider.messages(contact.id, now)) dataStore.saveMessage(message)
        }
        for (channel in channels) {
            for (message in MockDataProvider.channelMessages(channel.index, now)) dataStore.saveMessage(message)
        }

        // Link previews render offline from the message-owned columns, which saveMessage drops.
        for (seed in MockDataProvider.linkPreviewSeeds) {
            dataStore.updateMessageLinkPreview(
                EntityKey(radioId, seed.messageID), seed.url, seed.title, seed.imageData, iconData = null, fetched = true,
            )
        }

        // Reaction rows feed the reactor-detail list; the summary drives the badge.
        for (reacted in MockDataProvider.reactedMessages) {
            for (reaction in MockDataProvider.reactions(reacted.messageID, now)) dataStore.saveReaction(reaction)
            dataStore.updateMessageReactionSummary(EntityKey(radioId, reacted.messageID), reacted.summary)
        }

        for (messageID in MockDataProvider.messagesWithRepeats) {
            for (repeatRow in MockDataProvider.messageRepeats(messageID, now)) dataStore.saveMessageRepeat(radioId, repeatRow)
        }

        seedRxLogEntries(dataStore, now)
        seedNodeStatusSnapshots(dataStore, now)

        logger.info("Simulator: seeded ${contacts.size} contacts and ${channels.size} channels with messages")
    }

    /** Inserts the two flood-region RX-log fixtures when they are missing. `saveRxLogEntry` is insert-only. */
    private suspend fun seedRxLogEntries(dataStore: SimulatorSeedStore, now: Instant) {
        val existingIDs = dataStore.fetchRxLogEntries(MockDataProvider.simulatorRadioId).map { it.id }.toSet()
        for (entry in MockDataProvider.rxLogEntries(now)) {
            if (entry.id !in existingIDs) dataStore.saveRxLogEntry(entry)
        }
    }

    /**
     * Seeds a node's GPS track once so the location History list and map have content to render. Skipped when
     * the node already has a snapshot, so the now-relative timestamps aren't restacked into a duplicate track on
     * every reconnect. Snapshot rows are keyed by node key plus timestamp, so re-seeding would otherwise append.
     */
    private suspend fun seedNodeStatusSnapshots(dataStore: SimulatorSeedStore, now: Instant) {
        val nodeKey = MockDataProvider.locationHistoryNodePublicKey
        if (dataStore.fetchLatestNodeStatusSnapshot(nodeKey) != null) return
        val existingKeys = dataStore.existingNodeStatusSnapshotKeys()
        dataStore.batchInsertNodeStatusSnapshots(MockDataProvider.nodeStatusSnapshots(now), existingKeys)
    }

    companion object {
        /** Swift's brief connect delay. */
        val DEFAULT_CONNECT_DELAY: Duration = 200.milliseconds

        private val logger: Logger = Logger.getLogger("com.mc1.SimulatorConnectionMode")
    }
}
