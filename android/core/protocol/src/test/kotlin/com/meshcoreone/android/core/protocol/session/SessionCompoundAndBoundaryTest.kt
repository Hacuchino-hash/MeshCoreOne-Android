// AndroidOnly: WP-107 Source-supported compound ownership, unsigned/path/text families and bounded post-commit completion.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.crypto.CryptoFixtures
import com.meshcoreone.android.core.protocol.crypto.Ed25519Crypto
import com.meshcoreone.android.core.protocol.event.*
import com.meshcoreone.android.core.protocol.parser.BinaryParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCompoundAndBoundaryTest {
    @TestFactory
    fun lifecycleCompounds() = listOf(
        nativeCase("a second session cannot distribute a live transport drain or disconnect its actual owner") {
            val f = fixture(); start(f)
            val other = MeshCoreSession(f.transport, clock = SchedulerClock(testScheduler),
                coroutineContext = backgroundScope.coroutineContext, onDiagnostic = f.diagnostics::add)
            val failure = assertFailsWith<MeshCoreException.ConnectionLost> { other.start() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SessionCorrelationException.ConcurrentTransportOwner })
            assertTrue(f.transport.isConnected())
            assertEquals(0, f.transport.disconnects)
            assertEquals(1, f.transport.maximumCollectors)
            other.stop()
            assertTrue(f.transport.isConnected())
            f.session.stop()
            assertEquals(1, f.transport.disconnects)
        },
        nativeCase("retained physical-link uncertainty is not lost by constructing another session") {
            val f = fixture(); start(f); f.session.stop(false)
            val other = MeshCoreSession(f.transport, SessionConfiguration(clientIdentifier = "MCore"),
                SchedulerClock(testScheduler), backgroundScope.coroutineContext, onDiagnostic = f.diagnostics::add)
            val failure = assertFailsWith<MeshCoreException.ConnectionLost> { other.start() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SessionCorrelationException.RetainedTransport })
            assertTrue(f.transport.isConnected())
            assertEquals(0, f.transport.disconnects)
            f.transport.disconnect()
            val startup = checkedRequest { other.start() }; runCurrent()
            f.transport.receive(selfPacket(filled(0x44, 32))); runCurrent()
            startup.await()
            assertEquals(filled(0x44, 32), other.currentSelfInfo?.publicKey)
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.stop(true) }
            assertTrue(f.transport.isConnected(), "The obsolete session cannot close a new physical owner")
            other.stop()
        },
        nativeCase("concurrent starts share one real handshake") {
            val f = fixture()
            val first = checkedRequest { f.session.start() }
            val second = checkedRequest { f.session.start() }; runCurrent()
            assertEquals(1, f.transport.connects)
            assertEquals(1, f.transport.sent.size)
            f.transport.receive(selfPacket()); runCurrent()
            first.await(); second.await()
            assertEquals(1, f.transport.maximumCollectors)
            f.session.stop()
        },
        nativeCase("cancelled startup during connect never sends appStart or pretends to be connected") {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.transport.beforeConnect = { gate.await() }
            val task = checkedRequest { f.session.start() }; runCurrent()
            task.cancel(); runCurrent()
            assertFailsWith<CancellationException> { task.await() }
            assertFalse(f.transport.isConnected())
            assertTrue(f.transport.sent.isEmpty())
            assertEquals(1, f.transport.disconnects)
            gate.complete(Unit)
            f.session.stop()
        },
        nativeCase("new startup cannot overtake old-generation physical disconnect") {
            val f = fixture()
            val first = checkedRequest { f.session.start() }; runCurrent()
            val gate = CompletableDeferred<Unit>()
            f.transport.beforeDisconnect = { gate.await() }
            val stopping = checkedRequest { f.session.stop() }; runCurrent()
            val successor = checkedRequest { f.session.start() }; runCurrent()
            assertEquals(1, f.transport.connects)
            assertEquals(1, f.transport.sent.size)
            assertFalse(successor.isCompleted)
            gate.complete(Unit); runCurrent()
            stopping.await()
            assertFailsWith<MeshCoreException.ConnectionLost> { first.await() }
            assertEquals(2, f.transport.connects)
            assertEquals(2, f.transport.sent.size)
            f.transport.beforeDisconnect = {}
            f.transport.receive(selfPacket(filled(0x44, 32), "Fresh")); runCurrent()
            successor.await()
            assertEquals(filled(0x44, 32), f.session.currentSelfInfo?.publicKey)
            assertTrue(f.transport.isConnected())
            f.session.stop()
        },
        nativeCase("cancelled post-commit other-params refresh still commits the actual cache before the next mutation") {
            val f = fixture(); start(f)
            val refreshGate = CompletableDeferred<Unit>()
            var manual = false
            var acks = 0
            val written = mutableListOf<Bytes>()
            f.transport.beforeSend = { if (it[0] == 1u.toUByte()) refreshGate.await() }
            f.transport.onSend = {
                if (it[0] == 0x26u.toUByte()) {
                    written += it; manual = it[1] != 0.toUByte(); acks = it[4].toInt(); f.transport.ok()
                } else f.transport.receive(selfPacket(manual = manual, multiAcks = acks))
            }
            val first = checkedRequest { f.session.setManualAddContacts(true) }; runCurrent()
            first.cancel(); runCurrent()
            assertFailsWith<CancellationException> { first.await() }
            val second = checkedRequest { f.session.setMultiAcks(7u) }; runCurrent()
            assertEquals(listOf(hex("2601000000")), written)
            refreshGate.complete(Unit); runCurrent()
            second.await()
            assertEquals(listOf(hex("2601000000"), hex("2601000007")), written)
            assertEquals(true, f.session.currentSelfInfo?.manualAddContacts)
            assertEquals(7u.toUByte(), f.session.currentSelfInfo?.multiAcks)
            f.session.stop()
        },
        nativeCase("manual sends and config reads cannot interleave a compound signing workflow") {
            val f = fixture()
            val vector = CryptoFixtures.signatures.single { it.id == "rfc8032-3" }
            start(f, selfPacket(vector.publicKey))
            val signing = checkedRequest { f.session.sign(hex("af82"), 1) }; runCurrent()
            val sending = checkedRequest { f.session.sendChannelMessage(0u, "hi", testEpoch) }; runCurrent()
            val battery = checkedRequest { f.session.getBattery() }; runCurrent()
            assertEquals(2, f.transport.sent.size)
            f.transport.receive(raw(0x13, hex("0002000000"))); runCurrent()
            assertEquals(hex("22af"), f.transport.sent.last())
            f.transport.ok(); runCurrent(); assertEquals(hex("2282"), f.transport.sent.last())
            f.transport.ok(); runCurrent(); assertEquals(hex("23"), f.transport.sent.last())
            f.transport.receive(raw(0x14, vector.signature)); runCurrent()
            assertTrue(Ed25519Crypto.verify(vector.message, signing.await(), vector.publicKey))
            assertEquals(6, f.transport.sent.size)
            assertEquals(3u.toUByte(), f.transport.sent.last()[0])
            f.transport.ok(); runCurrent(); sending.await()
            assertEquals(hex("14"), f.transport.sent.last())
            f.transport.receive(batteryPacket(4018)); runCurrent(); assertEquals(4018L, battery.await().level)
            f.session.stop()
        },
        nativeCase("default signing chunks are exactly 120 bytes and a missing final signature cannot succeed") {
            val f = fixture(); start(f)
            val data = filled(0xa5, 125)
            f.transport.onSend = {
                when (it[0].toInt()) {
                    0x21 -> f.transport.receive(raw(0x13, hex("00ff000000")))
                    0x22 -> f.transport.ok()
                    0x23 -> Unit
                    else -> fail("Unexpected signing step")
                }
            }
            val task = checkedRequest { f.session.sign(data) }; runCurrent()
            assertEquals(listOf(hex("21"), raw(0x22, filled(0xa5, 120)), raw(0x22, filled(0xa5, 5)), hex("23")), f.transport.sent.drop(1))
            advanceTimeBy(3000); runCurrent()
            assertFailsWith<MeshCoreException.Timeout> { task.await() }
            f.session.stop()
        },
        nativeCase("coalesced fetch callers do not reinterpret the leader's timeout override") {
            val f = fixture(); start(f)
            val leader = checkedRequest { f.session.getMessage(0.5) }; runCurrent()
            val follower = checkedRequest { f.session.getMessage(Double.NaN) }; runCurrent()
            assertEquals(2, f.transport.sent.size)
            f.transport.receive(hex("0a")); runCurrent()
            assertEquals(MessageResult.NoMoreMessages, leader.await())
            assertEquals(MessageResult.NoMoreMessages, follower.await())
            f.session.stop()
        },
        nativeCase("reused ACK code cannot report a later retry operation delivered") {
            val f = fixture(); start(f)
            val key = filled(1, 32)
            f.transport.onSend = {
                f.transport.receive(sentPacket(hex("aabbccdd"), 0))
                f.transport.receive(ackPacket(hex("aabbccdd")))
            }
            assertNotNull(f.session.sendMessageWithRetry(key, "text", testEpoch))
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.sendMessageWithRetry(key, "text", testEpoch) }
            f.session.stop()
        },
        nativeCase("UInt32 maximum contact total is retained without preallocating untrusted capacity") {
            val f = fixture(); start(f)
            f.transport.onSend = { f.transport.receive(contactsStart(4_294_967_295)); f.transport.receive(contactsEnd()) }
            val result = f.session.getContactsReportingTotal()
            assertEquals(4_294_967_295L, result.reportedTotal)
            assertTrue(result.contacts.isEmpty())
            assertEquals(false, f.session.lastContactFetchProgress?.completed)
            assertTrue(f.session.isContactsDirty)
            f.session.stop()
        },
        nativeCase("cached raw contact deletion and full-table pushes pass through the actual receive drain") {
            val f = fixture(); start(f)
            val key = filled(0x11, 32)
            f.transport.onSend = {
                f.transport.receive(contactsStart(1)); f.transport.receive(contactPacket(key)); f.transport.receive(contactsEnd())
            }
            f.session.getContacts()
            f.transport.onSend = {}
            assertNotNull(f.session.getContactByKeyPrefix(key))
            assertFalse(f.session.isContactsDirty)
            f.transport.receive(raw(0x8f, key)); runCurrent()
            assertNull(f.session.getContactByKeyPrefix(key))
            assertTrue(f.session.isContactsDirty)
            f.transport.onSend = { f.transport.receive(contactsStart(0)); f.transport.receive(contactsEnd()) }
            f.session.getContacts()
            f.transport.onSend = {}
            f.transport.receive(raw(0x90)); runCurrent()
            assertTrue(f.session.isContactsDirty)
            f.session.stop()
        },
    )

    @TestFactory
    fun neighbourWidths() = listOf(0, 1, 4, 6, 8, 32, 255).map { width ->
        nativeCase("neighbour caller prefix width $width survives the real session decoder") {
            val f = fixture(); start(f)
            val key = filled(0x44, 32)
            f.transport.onSend = {
                assertEquals(raw(0x32, key + hex("0600ff000000") + Bytes.of(width) + hex("ddccbbaa")), it)
                f.transport.receive(sentPacket())
                f.transport.receive(binaryPacket(hex("aabbccdd"), hex("01000100") + filled(0x55, width) + hex("ffffff7f7f")))
            }
            val response = f.session.requestNeighbours(key, pubkeyPrefixLength = width.toUByte())
            assertEquals(filled(0x55, width), response.neighbours.single().publicKeyPrefix)
            assertEquals(2_147_483_647L, response.neighbours.single().secondsAgo)
            assertEquals(31.75, response.neighbours.single().snr)
            f.session.stop()
        }
    }

    @TestFactory
    fun incompleteBinary() = listOf("ACL", "MMA", "Neighbours").map { operation ->
        nativeCase("truncated $operation is not an empty complete response") {
            val f = fixture(); start(f)
            f.transport.onSend = {
                f.transport.receive(sentPacket())
                f.transport.receive(binaryPacket(hex("aabbccdd"), hex("01")))
            }
            assertFailsWith<MeshCoreException.ParseError> {
                when (operation) {
                    "ACL" -> f.session.requestACL(filled(1, 32))
                    "MMA" -> f.session.requestMMA(filled(1, 32), testEpoch, testEpoch)
                    else -> f.session.requestNeighbours(filled(1, 32))
                }
            }
            assertTrue(f.diagnostics.any { it is SessionDiagnostic.BackgroundFailure && it.cause is BinaryParseException })
            f.session.stop()
        }
    } + nativeCase("partial ACL preserves the real record and explicit truncation diagnostic") {
        val f = fixture(); start(f)
        f.transport.onSend = {
            f.transport.receive(sentPacket())
            f.transport.receive(binaryPacket(hex("aabbccdd"), hex("1122334455660201")))
        }
        assertEquals(listOf(ACLEntry(hex("112233445566"), 2u)), f.session.requestACL(filled(1, 32)).entries)
        assertTrue(f.diagnostics.any { it is SessionDiagnostic.BackgroundFailure && it.cause is BinaryParseException })
        f.session.stop()
    }

    @TestFactory
    fun utf8Names() = listOf(
        ("a".repeat(28) + "\uD83D\uDE00") to "a".repeat(28),
        ("a".repeat(30) + "e\u0301") to "a".repeat(30),
        "\u4e2d".repeat(11) to "\u4e2d".repeat(10),
        ("a".repeat(30) + "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67") to "a".repeat(30),
    ).mapIndexed { index, (name, expected) ->
        nativeCase("source grapheme/UTF8 name boundary $index is exact on the session wire") {
            val f = fixture(); start(f)
            command(f, raw(0x08, Bytes.utf8(expected)), hex("00")) { f.session.setName(name) }
            assertTrue(f.transport.sent.last().size <= 32)
            f.session.stop()
        }
    }
}
