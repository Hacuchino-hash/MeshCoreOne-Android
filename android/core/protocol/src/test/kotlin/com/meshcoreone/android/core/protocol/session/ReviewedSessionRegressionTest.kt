// AndroidOnly: WP-107 Executable regressions for the eight independently traced lifecycle/correlation/cache review findings.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.MeshTransportError
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewedSessionRegressionTest {
    @TestFactory
    fun eightFindings() = listOf(
        nativeCase("R1 cancelled owning job cannot cancel the physical stop receipt") {
            val owner = Job(backgroundScope.coroutineContext[Job])
            val f = fixture(owningJob = owner)
            try {
                start(f)
                owner.cancelAndJoin()
                runCurrent()
                assertTrue(f.transport.isConnected())
                val stop = checkedRequest { f.session.stop() }; runCurrent()
                stop.await()
                assertEquals(1, f.transport.disconnects)
                assertFalse(f.transport.isConnected())
                val next = fixture(transport = f.transport)
                start(next, selfPacket(filled(0x44, 32)))
                assertEquals(filled(0x44, 32), next.session.currentSelfInfo?.publicKey)
                next.session.stop()
                assertEquals(2, f.transport.disconnects)
            } finally {
                owner.cancelAndJoin()
                f.transport.disconnect()
            }
        },
        nativeCase("R2 unresolved public matcher cannot feed a late battery into a typed successor") {
            val f = fixture(); start(f)
            val custom = checkedRequest {
                f.session.sendAndWait(hex("14"), timeout = 0.1) { (it as? MeshEvent.Battery)?.info }
            }; runCurrent()
            advanceTimeBy(100); runCurrent()
            assertFailsWith<MeshCoreException.Timeout> { custom.await() }
            val next = checkedRequest { f.session.getBattery() }; runCurrent()
            f.transport.receive(batteryPacket(1111)); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { next.await() }
            assertEquals(2, f.transport.sent.size, "An unresolved arbitrary predicate cannot admit another wire exchange")
            f.session.stop()
        },
        nativeCase("R4 incomplete full contacts cannot advance an incremental cursor past unchanged missing C") {
            val f = fixture(); start(f)
            val first = checkedRequest { f.session.getContactsReportingTotal() }; runCurrent()
            f.transport.receive(contactsStart(3))
            f.transport.receive(contactPacket(filled(0x11, 32), "A"))
            f.transport.receive(contactPacket(filled(0x22, 32), "B"))
            f.transport.receive(contactsEnd(1000)); runCurrent()
            assertEquals(2, first.await().contacts.size)
            assertEquals(3L, first.await().reportedTotal)
            assertTrue(f.session.isContactsDirty)
            f.transport.onSend = {
                assertEquals(hex("04"), it, "Unknown completeness requires a full fetch, not since1000")
                f.transport.receive(contactsStart(3))
                f.transport.receive(contactPacket(filled(0x11, 32), "A"))
                f.transport.receive(contactPacket(filled(0x22, 32), "B"))
                f.transport.receive(contactPacket(filled(0x33, 32), "C"))
                f.transport.receive(contactsEnd(1000))
            }
            assertEquals(listOf("A", "B", "C"), f.session.ensureContacts().map { it.advertisedName })
            assertEquals(3, f.session.cachedContacts.size)
            assertFalse(f.session.isContactsDirty)
            f.session.stop()
        },
        nativeCase("R4 a late contactsEnd cannot clear a timed-out fetch invalidation") {
            val f = fixture(); start(f)
            val fetch = checkedRequest { f.session.getContacts() }; runCurrent()
            advanceTimeBy(100); runCurrent()
            assertFailsWith<MeshCoreException.Timeout> { fetch.await() }
            f.transport.receive(contactsEnd(5000)); runCurrent()
            assertTrue(f.session.isContactsDirty)
            assertNull(f.session.core.generation().contacts.contactsLastModified)
            assertFalse(assertNotNull(f.session.lastContactFetchProgress).completed)
            f.session.stop()
        },
        nativeCase("R6 lost binary send receipt does not suppress the required resetPath rollback") {
            val f = fixture(SessionConfiguration(
                defaultTimeout = 1.0, clientIdentifier = "MCore",
                binaryRequestOverallTimeout = 0.2, binaryRequestRetransmitInterval = null,
            )); start(f)
            val contact = testContact()
            val request = checkedRequest { f.session.requestRegions(contact) }; runCurrent()
            f.transport.ok(); runCurrent()
            assertEquals(raw(0x39, contact.publicKey + hex("0100")), f.transport.sent.last())
            advanceTimeBy(200); runCurrent()
            assertEquals(raw(0x0d, contact.publicKey), f.transport.sent.last())
            assertFalse(request.isCompleted)
            f.transport.error(10); runCurrent()
            assertFalse(request.isCompleted, "Old untagged binary error is not a rollback rejection")
            f.transport.ok(); runCurrent()
            val original = assertFailsWith<MeshCoreException.Timeout> { request.await() }
            assertTrue(original.suppressed.isEmpty(), "A confirmed bare OK completed the real rollback")
            assertEquals(listOf(1, 9, 0x39, 0x0d), f.transport.sent.map { it[0].toInt() })
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.setName("new") }
            f.session.stop()
        },
        nativeCase("R7 refused logical restart cannot discard an earlier retained physical close receipt") {
            val f = fixture(); start(f)
            val failure = MeshTransportError.ConnectionFailed("reviewed receive failure")
            f.transport.onReceive = { if (it == hex("fe")) throw failure }
            f.transport.receive(hex("fe")); runCurrent()
            assertTrue(f.transport.isConnected())
            repeat(2) { assertFailsWith<MeshCoreException.ConnectionLost> { f.session.start() }; runCurrent() }
            f.session.stop()
            assertEquals(1, f.transport.disconnects)
            assertFalse(f.transport.isConnected())
            f.transport.onReceive = {}
            val next = fixture(transport = f.transport)
            start(next)
            next.session.stop()
            assertEquals(2, f.transport.disconnects)
        },
    )

    @TestFactory
    fun streamedSendUncertainty() = listOf("failure", "cancel").map { outcome ->
        nativeCase("R3 committed contact send $outcome quarantines contacts and errors before another fetch") {
            val f = fixture(); start(f)
            val gate = CompletableDeferred<Unit>()
            f.transport.onSend = {
                assertEquals(hex("04"), it)
                if (outcome == "failure") throw MeshTransportError.SendFailed("after contacts write")
                gate.await()
            }
            val first = checkedRequest { f.session.getContacts() }; runCurrent()
            assertEquals(2, f.transport.sent.size)
            if (outcome == "cancel") { first.cancel(); runCurrent() }
            if (outcome == "cancel") assertFailsWith<CancellationException> { first.await() }
            else assertFailsWith<MeshTransportError.SendFailed> { first.await() }
            f.transport.onSend = {}
            val next = checkedRequest { f.session.getContactsReportingTotal() }; runCurrent()
            f.transport.receive(contactsStart(1))
            f.transport.receive(contactPacket(filled(0x99, 32), "orphan"))
            f.transport.receive(contactsEnd(2000)); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { next.await() }
            assertEquals(2, f.transport.sent.size)
            assertTrue(f.session.isContactsDirty)
            assertEquals(0, f.session.core.generation().pending.size)
            gate.complete(Unit)
            f.session.stop()
        }
    }

    @TestFactory
    fun binarySendUncertainty() = listOf("failure", "cancel").flatMap { outcome ->
        listOf("setter", "message-poll").map { successor ->
            nativeCase("R5 committed binary $outcome cannot attribute a late error to a following $successor") {
                val f = fixture(); start(f)
                val gate = CompletableDeferred<Unit>()
                f.transport.onSend = {
                    assertEquals(raw(0x1b, filled(0x31, 32)), it)
                    if (outcome == "failure") throw MeshTransportError.SendFailed("after status write")
                    gate.await()
                }
                val request = checkedRequest { f.session.requestStatus(filled(0x31, 32)) }; runCurrent()
                assertEquals(2, f.transport.sent.size)
                if (outcome == "cancel") { request.cancel(); runCurrent() }
                if (outcome == "cancel") assertFailsWith<CancellationException> { request.await() }
                else assertFailsWith<MeshTransportError.SendFailed> { request.await() }
                f.transport.onSend = {}
                val next = checkedRequest {
                    if (successor == "setter") f.session.setName("new") else f.session.getMessage()
                }; runCurrent()
                f.transport.error(10); runCurrent()
                val failure = assertFailsWith<MeshCoreException.ConnectionLost> { next.await() }
                assertEquals("error", generateSequence<Throwable>(failure) { it.cause }
                    .filterIsInstance<SessionCorrelationException.UnresolvedReply>().single().responseFamily)
                assertEquals(2, f.transport.sent.size)
                gate.complete(Unit)
                f.session.stop()
            }
        }
    }

    @TestFactory
    fun constructorValidation() = listOf(
        "default-zero" to SessionConfiguration(defaultTimeout = 0.0),
        "default-negative" to SessionConfiguration(defaultTimeout = -1.0),
        "default-NaN" to SessionConfiguration(defaultTimeout = Double.NaN),
        "binary-overall" to SessionConfiguration(binaryRequestOverallTimeout = 0.0),
        "binary-resend" to SessionConfiguration(binaryRequestRetransmitInterval = Double.NaN),
        "contacts-idle" to SessionConfiguration(contactStreamInactivityTimeout = -1.0),
        "contacts-hard" to SessionConfiguration(contactStreamHardTimeout = 0.0),
        "channel-idle" to SessionConfiguration(channelPipelineIdleTimeout = 0.0),
        "channel-hard" to SessionConfiguration(channelPipelineHardTimeout = Double.POSITIVE_INFINITY),
        "channel-grace" to SessionConfiguration(channelPipelinePostDrainGrace = -1.0),
    ).map { (name, configuration) ->
        nativeCase("R8 invalid $name constructor does not attach inaccessible jobs to its structured caller") {
            val transport = SessionRadioTransport()
            withTimeout(1000) {
                coroutineScope {
                    val owner = assertNotNull(currentCoroutineContext()[Job])
                    assertTrue(owner.children.none())
                    assertFailsWith<MeshCoreException.InvalidInput> {
                        MeshCoreSession(transport, configuration, SchedulerClock(testScheduler), currentCoroutineContext())
                    }
                    assertTrue(owner.children.none(), "Invalid construction must not leak a lifecycle/generation child")
                }
            }
            assertEquals(0, transport.connects)
            assertEquals(0, transport.disconnects)
            assertTrue(transport.sent.isEmpty())
        }
    }
}
