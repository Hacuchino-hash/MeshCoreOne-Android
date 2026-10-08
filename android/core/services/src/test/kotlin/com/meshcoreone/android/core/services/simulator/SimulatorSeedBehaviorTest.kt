// AndroidOnly: WP-217 native seed checks: determinism, clock injection, write order, linkage, skips and failures.
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.canonicalString
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.blocking
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.native
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.oracleNow
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.seededStore
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class SimulatorSeedBehaviorTest {
    private val radioId = MockDataProvider.simulatorRadioId

    @TestFactory
    fun determinismTests(): List<DynamicTest> = listOf(
        native("seed is deterministic for a fixed clock") {
            val first = seededStore()
            val second = seededStore()
            assertEquals(first.allMessages().sortedBy { it.id }, second.allMessages().sortedBy { it.id })
            assertEquals(first.allReactions().sortedBy { it.id }, second.allReactions().sortedBy { it.id })
            assertEquals(first.allRepeats().sortedBy { it.id }, second.allRepeats().sortedBy { it.id })
            assertEquals(first.allRxEntries(), second.allRxEntries())
            val key = MockDataProvider.locationHistoryNodePublicKey
            assertEquals(first.fetchNodeStatusSnapshots(key, null), second.fetchNodeStatusSnapshots(key, null))
            assertEquals(first.fetchContacts(radioId), second.fetchContacts(radioId))
            assertEquals(first.device(MockDataProvider.simulatorDeviceID), second.device(MockDataProvider.simulatorDeviceID))
        },
        native("seed timestamps move with the injected clock") {
            val shift = 3_600L
            val base = seededStore(oracleNow)
            val later = seededStore(oracleNow.plusSeconds(shift))
            for (message in base.allMessages()) {
                val moved = assertNotNull(later.fetchMessage(message.id))
                assertEquals(message.createdAt.plusSeconds(shift), moved.createdAt, message.text)
                assertEquals(message.timestamp + shift.toUInt(), moved.timestamp, message.text)
                assertEquals(message.senderTimestamp?.plus(shift.toUInt()), moved.senderTimestamp, message.text)
            }
            for (contact in base.fetchContacts(radioId)) {
                val moved = later.fetchContacts(radioId).first { it.id == contact.id }
                assertEquals(contact.lastAdvertTimestamp + shift.toUInt(), moved.lastAdvertTimestamp)
                assertEquals(contact.lastMessageDate?.plusSeconds(shift), moved.lastMessageDate)
            }
            for (repeat in base.allRepeats()) {
                assertEquals(repeat.receivedAt.plusSeconds(shift), later.allRepeats().first { it.id == repeat.id }.receivedAt)
            }
            assertEquals(oracleNow.plusSeconds(shift), later.device(MockDataProvider.simulatorDeviceID)?.lastConnected)
        },
        native("every seeded row stays in the simulator radio") {
            val store = seededStore()
            assertTrue(store.allMessages().all { it.radioId == radioId })
            assertTrue(store.allReactions().all { it.radioId == radioId })
            assertTrue(store.allRxEntries().all { it.radioId == radioId })
            assertTrue(store.fetchChannels(radioId).size == 4 && store.fetchContacts(radioId).size == 11)
        },
    )

    @TestFactory
    fun writeOrderTests(): List<DynamicTest> = listOf(
        native("seed writes in Swift order with messages before their dependent rows") {
            val store = seededStore()
            val now = oracleNow
            val contacts = MockDataProvider.contacts(now)
            val channels = MockDataProvider.channels(now)
            val messageCount = contacts.sumOf { MockDataProvider.messages(it.id, now).size } +
                channels.sumOf { MockDataProvider.channelMessages(it.index, now).size }
            val expected = buildList {
                add("saveDevice")
                repeat(contacts.size) { add("fetchContact"); add("saveContact") }
                repeat(channels.size) { add("saveChannel") }
                repeat(messageCount) { add("saveMessage") }
                repeat(MockDataProvider.linkPreviewSeeds.size) { add("updateMessageLinkPreview") }
                for (reacted in MockDataProvider.reactedMessages) {
                    repeat(MockDataProvider.reactions(reacted.messageID, now).size) { add("saveReaction") }
                    add("updateMessageReactionSummary")
                }
                for (id in MockDataProvider.messagesWithRepeats) {
                    repeat(MockDataProvider.messageRepeats(id, now).size) { add("saveMessageRepeat") }
                }
                add("fetchRxLogEntries")
                repeat(2) { add("saveRxLogEntry") }
                add("fetchLatestNodeStatusSnapshot")
                add("existingNodeStatusSnapshotKeys")
                add("batchInsertNodeStatusSnapshots")
            }
            assertEquals(expected, store.recordedCalls)
        },
    )

    @TestFactory
    fun linkageTests(): List<DynamicTest> = listOf(
        native("every reaction summary matches its seeded reaction rows") {
            val store = seededStore()
            for (reacted in MockDataProvider.reactedMessages) {
                val message = assertNotNull(store.fetchMessage(reacted.messageID))
                assertEquals(reacted.summary, message.reactionSummary)
                val rows = MockDataProvider.reactions(reacted.messageID, oracleNow)
                val counted = rows.groupBy { it.emoji }.map { (emoji, group) -> "$emoji:${group.size}" }.joinToString(",")
                assertEquals(reacted.summary, counted, "summary must count the seeded rows in first-appearance order")
                for (row in rows) {
                    assertEquals(reacted.messageID.canonicalString().take(8), row.messageHash)
                    assertEquals(row.emoji, row.rawText)
                    assertEquals(message.contactID, row.contactID, "reaction must target the message's DM")
                    assertEquals(message.channelIndex, row.channelIndex, "reaction must target the message's channel")
                }
                assertEquals(rows.size, store.fetchReactions(reacted.messageID).size)
            }
        },
        native("every repeated message has heardRepeats equal to its repeat rows heard after it") {
            val store = seededStore()
            for (messageID in MockDataProvider.messagesWithRepeats) {
                val message = assertNotNull(store.fetchMessage(messageID))
                val repeats = store.fetchMessageRepeats(messageID)
                assertEquals(message.heardRepeats, repeats.size.toLong(), message.text)
                assertTrue(repeats.all { it.receivedAt.isAfter(message.createdAt) }, message.text)
                assertEquals(repeats.size, repeats.map { it.pathNodes }.toSet().size, "routes must be distinct")
            }
            val linkedRepeats = MockDataProvider.messagesWithRepeats.sumOf { store.fetchMessageRepeats(it).size }
            assertEquals(store.allRepeats().size, linkedRepeats, "no orphan repeat rows")
        },
        native("Mesh HQ backlog puts the first unread message past one page") {
            val messages = MockDataProvider.channelMessages(MockDataProvider.meshHQChannelIndex, oracleNow)
            assertEquals(MESH_HQ_TOTAL_MESSAGES, messages.size)
            val unread = messages.count { !it.isRead }
            assertEquals(MESH_HQ_UNREAD_COUNT, unread)
            assertTrue(unread > 50, "unread must exceed a 50-row page")
            val channel = MockDataProvider.channels(oracleNow).first { it.index == MockDataProvider.meshHQChannelIndex }
            assertEquals(unread.toLong(), channel.unreadCount)
            assertEquals(messages.sortedBy { it.createdAt }, messages, "backlog is chronological")
            assertEquals(MESH_HQ_TOTAL_MESSAGES / 8, messages.count { it.isOutgoing })
        },
    )

    @TestFactory
    fun skipAndFailureTests(): List<DynamicTest> = listOf(
        native("seeding inserts only the RX log entries that are missing") {
            val store = SimulatorInMemorySeedStore()
            store.insertRxEntry(MockDataProvider.rxLogEntries(oracleNow).first())
            blocking { SimulatorTestSupport.mode().seedDataStore(store) }
            assertEquals(2, store.allRxEntries().size)
            assertEquals(1, store.recordedCalls.count { it == "saveRxLogEntry" })
        },
        native("node snapshots are not restacked once the node has history") {
            val store = SimulatorInMemorySeedStore()
            val existing = NodeStatusSnapshotDTO(timestamp = oracleNow.minus(Duration.ofDays(400)), nodePublicKey = MockDataProvider.locationHistoryNodePublicKey)
            store.insertSnapshot(existing)
            blocking { SimulatorTestSupport.mode().seedDataStore(store) }
            assertEquals(listOf(existing), store.fetchNodeStatusSnapshots(MockDataProvider.locationHistoryNodePublicKey, null))
            assertTrue("batchInsertNodeStatusSnapshots" !in store.recordedCalls)
        },
        native("snapshot ids follow their store key and never repeat across seed instants") {
            val first = MockDataProvider.nodeStatusSnapshots(oracleNow)
            val again = MockDataProvider.nodeStatusSnapshots(oracleNow)
            val later = MockDataProvider.nodeStatusSnapshots(oracleNow.plusSeconds(90))
            assertEquals(first.map { it.id }, again.map { it.id }, "fixed clock, fixed ids")
            assertEquals(first.size, first.map { it.id }.toSet().size, "ids are unique within a pass")
            assertTrue(first.map { it.id }.toSet().intersect(later.map { it.id }.toSet()).isEmpty(), "a later pass reuses no id")
            for (row in first) assertEquals(nodeStatusSnapshotID(NodeStatusSnapshotKey.of(row)), row.id)
        },
        native("re-seeding reapplies the preview blobs that saveMessage resets") {
            val clock = SettableClock(oracleNow)
            val store = SimulatorInMemorySeedStore()
            val mode = SimulatorTestSupport.mode(clock)
            blocking { mode.seedDataStore(store) }
            clock.advance(90)
            blocking { mode.seedDataStore(store) }
            val message = assertNotNull(store.fetchMessage(MockDataProvider.aliceLinkPreviewMessageID))
            assertEquals(MockDataProvider.demoImageData, message.linkPreviewImageData)
            assertEquals(true, message.linkPreviewFetched)
            assertEquals("https://meshcoreone.com/trails/skyline", message.linkPreviewURL)
        },
        native("snapshot keys truncate to whole milliseconds like Swift") {
            val key = MockDataProvider.locationHistoryNodePublicKey
            assertEquals(1_000L, NodeStatusSnapshotKey.of(key, java.time.Instant.ofEpochSecond(1, 999_999)).milliseconds)
            assertEquals(0L, NodeStatusSnapshotKey.of(key, java.time.Instant.ofEpochSecond(-1, 999_500_000)).milliseconds)
            assertEquals(-500L, NodeStatusSnapshotKey.of(key, java.time.Instant.ofEpochSecond(-1, 500_000_000)).milliseconds)
        },
        native("store failures propagate out of the seed") {
            val store = SimulatorInMemorySeedStore()
            store.failOn("saveChannel")
            assertFailsWith<PersistenceStoreException> { blocking { SimulatorTestSupport.mode().seedDataStore(store) } }
            assertTrue("saveMessage" !in store.recordedCalls, "seed must stop at the first failure")
        },
        native("store cancellation propagates out of the seed") {
            val store = SimulatorInMemorySeedStore()
            store.failOn("saveReaction") { CancellationException("store cancelled") }
            assertFailsWith<CancellationException> { blocking { SimulatorTestSupport.mode().seedDataStore(store) } }
            assertTrue("updateMessageReactionSummary" !in store.recordedCalls)
        },
        native("concurrent seed passes are serialized") {
            val store = SimulatorInMemorySeedStore()
            val gate = SimulatorInMemorySeedStore.RxGate()
            store.rxGate = gate
            val mode = SimulatorTestSupport.mode()
            blocking {
                val first = launch { mode.seedDataStore(store) }
                gate.reached.await()
                val second = launch { mode.seedDataStore(store) }
                repeat(SCHEDULER_TURNS) { yield() }
                assertEquals(1, store.recordedCalls.count { it == "saveDevice" }, "second pass must wait for the first")
                gate.release.complete(Unit)
                first.join()
                second.join()
            }
            assertEquals(2, store.recordedCalls.count { it == "saveDevice" })
            assertEquals(2, store.allRxEntries().size, "RX fixtures must not be inserted twice")
        },
    )

    private companion object {
        const val SCHEDULER_TURNS = 50
    }
}
