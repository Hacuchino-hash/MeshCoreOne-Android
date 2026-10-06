// AndroidOnly: WP-216 behavior checks for HeardRepeatsService guards, store-failure containment, cancellation, events and the WP-208 correlation stand-in.
package com.meshcoreone.android.core.services.reactions

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.DecryptStatus
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.protocol.event.PayloadType
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.TEST_NODE_NAME
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.blocking
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.bytes
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.incomingChannelMessage
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.makeEcho
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.newRadioId
import com.meshcoreone.android.core.services.reactions.ReactionsFixtures.testChannelMessage
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class HeardRepeatsServiceBehaviorTest {
    private val stamp: UInt = 1_704_067_300u

    /** A configured service whose store holds one sent channel message "hello" on channel 0. */
    private class SentFixture {
        val store = ReactionsInMemoryHeardRepeatStore()
        val service = HeardRepeatsService(store)
        val radioId = newRadioId()
        val messageID: UUID = UUID.randomUUID()

        init {
            store.saveMessage(testChannelMessage(id = messageID, radioId = radioId, channelIndex = 0u, text = "hello", timestamp = 1_704_067_300u))
            service.configure(radioId)
        }
    }

    @TestFactory
    fun guards(): List<DynamicTest> = listOf(
        test("an unconfigured service ignores RX entries without touching the store") {
            blocking {
                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                assertNull(service.configuredRadioId)
                assertNull(service.processForRepeats(makeEcho(newRadioId(), 0u, stamp, "hello")))
                assertTrue(store.recordedCalls.isEmpty())
            }
        },
        test("only decrypted group text with channel, timestamp and parseable text is processed") {
            blocking {
                val fixture = SentFixture()
                val echoes = listOf(
                    makeEcho(fixture.radioId, 0u, stamp, "hello", payloadType = PayloadType.TEXT_MESSAGE),
                    makeEcho(fixture.radioId, 0u, stamp, "hello", decryptStatus = DecryptStatus.HMAC_FAILED),
                    makeEcho(fixture.radioId, 0u, stamp, "hello", decodedText = null),
                    makeEcho(fixture.radioId, 0u, stamp, "hello").copy(channelIndex = null),
                    makeEcho(fixture.radioId, 0u, stamp, "hello").copy(senderTimestamp = null),
                    makeEcho(fixture.radioId, 0u, stamp, "hello", decodedText = "no colon here"),
                    makeEcho(fixture.radioId, 0u, stamp, "hello", decodedText = ":hello"),
                )
                echoes.forEach { assertNull(fixture.service.processForRepeats(it)) }
                assertTrue(fixture.store.recordedCalls.isEmpty(), "${fixture.store.recordedCalls}")
                assertEquals(1L, fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")))
            }
        },
        test("sent echoes match the stored body after the first colon, trimmed, under any air name") {
            blocking {
                val fixture = SentFixture()
                val echo = makeEcho(fixture.radioId, 0u, stamp, "", decodedText = "  Renamed Node \u200B:\u3000hello\t")
                assertEquals(1L, fixture.service.processForRepeats(echo))
                assertNull(fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "", decodedText = "Node:\u0301hello")))
            }
        },
        test("sent echoes are scoped to the configured radio") {
            blocking {
                val fixture = SentFixture()
                fixture.service.configure(newRadioId())
                assertNull(fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")))
            }
        },
    )

    @TestFactory
    fun storeFailures(): List<DynamicTest> = listOf(
        test("a failing duplicate check is treated as a duplicate and records nothing") {
            blocking {
                val fixture = SentFixture()
                fixture.store.failOn("messageRepeatExists")
                assertNull(fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")))
                assertEquals(listOf("messageRepeatExists"), fixture.store.recordedCalls)
            }
        },
        test("store failures while matching or recording return nil and emit no event") {
            blocking {
                for (failing in listOf("findSentChannelMessage", "saveMessageRepeat", "incrementMessageHeardRepeats")) {
                    val fixture = SentFixture()
                    val events = fixture.service.events()
                    fixture.store.failOn(failing)
                    assertNull(fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")), failing)
                    fixture.service.finishEvents()
                    assertTrue(events.toList().isEmpty(), failing)
                }
            }
        },
        test("cancellation from the store propagates out of processForRepeats and harvest") {
            blocking {
                val fixture = SentFixture()
                fixture.store.failOn("findSentChannelMessage") { CancellationException("cancelled") }
                assertFailsWith<CancellationException> { fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")) }

                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                val radioId = newRadioId()
                val message = incomingChannelMessage(
                    radioId = radioId, channelIndex = 0u, text = "x", senderName = TEST_NODE_NAME,
                    wireTimestamp = stamp, pathNodes = null, pathLength = 0u,
                )
                store.saveMessage(message)
                store.failOn("adoptIncomingPathIfUnknown") { CancellationException("cancelled") }
                assertFailsWith<CancellationException> {
                    service.harvestIncomingPaths(message, listOf(makeEcho(radioId, 0u, stamp, "x")))
                }
            }
        },
        test("a failing path adoption is contained and harvest continues with the next match") {
            blocking {
                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                val radioId = newRadioId()
                val message = incomingChannelMessage(
                    radioId = radioId, channelIndex = 0u, text = "x", senderName = TEST_NODE_NAME,
                    wireTimestamp = stamp, pathNodes = null, pathLength = 0u,
                )
                store.saveMessage(message)
                store.failOn("adoptIncomingPathIfUnknown")
                val early = makeEcho(radioId, 0u, stamp, "x", pathNodes = listOf(0x01), receivedAt = Instant.ofEpochSecond(10))
                val late = makeEcho(radioId, 0u, stamp, "x", pathNodes = listOf(0x02), receivedAt = Instant.ofEpochSecond(20))
                service.harvestIncomingPaths(message, listOf(late, early))
                assertEquals(2, store.recordedCalls.count { it == "adoptIncomingPathIfUnknown" })
                assertNull(store.fetchMessage(EntityKey(radioId, message.id))?.pathNodes)
            }
        },
        test("a failing refresh after adoption keeps harvesting from the previous snapshot") {
            blocking {
                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                val radioId = newRadioId()
                val message = incomingChannelMessage(
                    radioId = radioId, channelIndex = 0u, text = "x", senderName = TEST_NODE_NAME,
                    wireTimestamp = stamp, pathNodes = null, pathLength = 0u,
                )
                store.saveMessage(message)
                store.failOn("fetchMessageByDeduplicationKey")
                val early = makeEcho(radioId, 0u, stamp, "x", pathNodes = listOf(0x01), receivedAt = Instant.ofEpochSecond(10))
                val late = makeEcho(radioId, 0u, stamp, "x", pathNodes = listOf(0x02), receivedAt = Instant.ofEpochSecond(20))
                service.harvestIncomingPaths(message, listOf(late, early))
                // The stale snapshot still has an unknown path, so the later entry is offered for adoption (a no-op).
                val key = EntityKey(radioId, message.id)
                assertEquals(bytes(0x01), store.fetchMessage(key)?.pathNodes)
                assertTrue(store.fetchMessageRepeats(key).isEmpty())
            }
        },
        test("refreshRepeats returns stored repeats oldest first and empty on store error") {
            blocking {
                val fixture = SentFixture()
                val late = makeEcho(fixture.radioId, 0u, stamp, "hello", receivedAt = Instant.ofEpochSecond(20))
                val early = makeEcho(fixture.radioId, 0u, stamp, "hello", receivedAt = Instant.ofEpochSecond(10))
                fixture.service.processForRepeats(late)
                fixture.service.processForRepeats(early)
                val key = EntityKey(fixture.radioId, fixture.messageID)
                assertEquals(listOf(early.id, late.id), fixture.service.refreshRepeats(key).map { it.rxLogEntryID })
                fixture.store.failOn("fetchMessageRepeats")
                assertTrue(fixture.service.refreshRepeats(key).isEmpty())
            }
        },
    )

    @TestFactory
    fun paths(): List<DynamicTest> = listOf(
        test("an RX entry already recorded is not stored again as an extra") {
            blocking {
                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                val radioId = newRadioId()
                val message = incomingChannelMessage(
                    radioId = radioId, channelIndex = 0u, text = "x", senderName = TEST_NODE_NAME,
                    wireTimestamp = stamp, pathNodes = bytes(0xAA), pathLength = 1u,
                )
                store.saveMessage(message)
                val rxID = UUID.randomUUID()
                assertEquals(1L, service.recordDistinctPathIfNeeded(message, bytes(0xBB), 1u, null, null, Instant.now(), rxID))
                assertNull(service.recordDistinctPathIfNeeded(message, bytes(0xCC), 1u, null, null, Instant.now(), rxID))
                assertEquals(2L, service.recordDistinctPathIfNeeded(message, bytes(0xCC), 1u, -3.5, -90, Instant.now(), null))
            }
        },
        test("harvest skips outgoing and direct messages") {
            blocking {
                val store = ReactionsInMemoryHeardRepeatStore()
                val service = HeardRepeatsService(store)
                val radioId = newRadioId()
                val incoming = incomingChannelMessage(
                    radioId = radioId, channelIndex = 0u, text = "x", senderName = TEST_NODE_NAME,
                    wireTimestamp = stamp, pathNodes = null, pathLength = 0u,
                )
                val rx = listOf(makeEcho(radioId, 0u, stamp, "x"))
                service.harvestIncomingPaths(incoming.copy(direction = MessageDirection.OUTGOING), rx)
                service.harvestIncomingPaths(incoming.copy(channelIndex = null), rx)
                service.harvestIncomingPaths(incoming.copy(deduplicationKey = null), rx)
                assertTrue(store.recordedCalls.isEmpty())
            }
        },
    )

    @TestFactory
    fun events(): List<DynamicTest> = listOf(
        test("events are multicast to every subscriber registered before the yield") {
            blocking {
                val fixture = SentFixture()
                val first = fixture.service.events()
                val second = fixture.service.events()
                fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello"))
                fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello"))
                val expected = listOf(HeardRepeatEvent(fixture.messageID, 1), HeardRepeatEvent(fixture.messageID, 2))
                assertEquals(expected, first.take(2).toList())
                assertEquals(expected, second.take(2).toList())
            }
        },
        test("finishEvents ends live subscriptions and later subscriptions are empty") {
            blocking {
                val fixture = SentFixture()
                val live = fixture.service.events()
                val collector = async(start = CoroutineStart.UNDISPATCHED) { live.toList() }
                fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello"))
                fixture.service.finishEvents()
                assertEquals(listOf(HeardRepeatEvent(fixture.messageID, 1)), collector.await())
                assertTrue(fixture.service.events().toList().isEmpty())
                assertEquals(0, fixture.service.eventBroadcaster.subscriberCount)
            }
        },
        test("each events() stream is single-collection and a second collection fails fast") {
            blocking {
                val fixture = SentFixture()
                val events = fixture.service.events()
                fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello"))
                assertEquals(HeardRepeatEvent(fixture.messageID, 1), events.first())
                assertFailsWith<IllegalStateException> { events.first() }
                fixture.service.finishEvents()
                val afterFinish = fixture.service.events()
                assertTrue(afterFinish.toList().isEmpty())
                assertFailsWith<IllegalStateException> { afterFinish.toList() }
            }
        },
        test("a cancelled collector unregisters its subscription") {
            blocking {
                val fixture = SentFixture()
                val events = fixture.service.events()
                assertEquals(1, fixture.service.eventBroadcaster.subscriberCount)
                fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello"))
                events.first()
                assertEquals(0, fixture.service.eventBroadcaster.subscriberCount)
                assertFalse(fixture.service.processForRepeats(makeEcho(fixture.radioId, 0u, stamp, "hello")) == null)
            }
        },
    )

    @TestFactory
    fun correlation(): List<DynamicTest> = listOf(
        test("ChannelRXCorrelation keeps decrypted group-text rows rebuilding the key, oldest first") {
            val radioId = newRadioId()
            val key = DeduplicationKey.contentBased(null, 1u, "Alice", stamp, "hi there")
            val t = { seconds: Long -> Instant.ofEpochSecond(seconds) }
            val newer = makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice", receivedAt = t(30))
            val older = makeEcho(radioId, 1u, stamp, "  hi there ", senderName = "Alice ", receivedAt = t(10))
            val tie = makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice", receivedAt = t(30))
            val rejects = listOf(
                makeEcho(radioId, 2u, stamp, "hi there", senderName = "Alice"),
                makeEcho(radioId, 1u, stamp + 1u, "hi there", senderName = "Alice"),
                makeEcho(radioId, 1u, stamp, "hi there", senderName = "Bob"),
                makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice", decryptStatus = DecryptStatus.PENDING),
                makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice", payloadType = PayloadType.GROUP_DATA),
                makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice", decodedText = null),
                makeEcho(radioId, 1u, stamp, "hi there", senderName = "Alice").copy(senderTimestamp = null),
            )
            val matched = ChannelRXCorrelation.matching(rejects + listOf(newer, older, tie), key)
            assertEquals(listOf(older.id, newer.id, tie.id), matched.map { it.id })
            assertTrue(ChannelRXCorrelation.matching(listOf(newer), null).isEmpty())
        },
        test("ChannelRXCorrelation compares keys by canonical equivalence like Swift String ==") {
            val radioId = newRadioId()
            val nfdKey = DeduplicationKey.contentBased(null, 0u, "Jose\u0301", stamp, "hola")
            val nfcEcho = makeEcho(radioId, 0u, stamp, "hola", senderName = "Jos\u00E9")
            assertEquals(listOf(nfcEcho.id), ChannelRXCorrelation.matching(listOf(nfcEcho), nfdKey).map { it.id })
        },
    )

    private fun test(name: String, body: () -> Unit): DynamicTest = DynamicTest.dynamicTest("WP-216::$name", body)
}
