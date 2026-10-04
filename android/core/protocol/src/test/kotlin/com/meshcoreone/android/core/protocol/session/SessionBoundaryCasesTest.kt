// AndroidOnly: WP-107 Measurable correlation, lifecycle, cancellation, capability and malformed-input boundaries.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.*
import com.meshcoreone.android.core.protocol.model.*
import com.meshcoreone.android.core.protocol.transport.mock.MockTransportException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionBoundaryCasesTest {
    @TestFactory
    fun capabilityMatrix() = listOf(false, true).flatMap { pipeline ->
        listOf(false, true).map { without ->
            nativeCase("pipeline=$pipeline writeWithoutResponse=$without are independent actual capabilities") {
                val f = fixture(transport = SessionRadioTransport(without, pipeline)); start(f)
                val task = checkedRequest { f.session.getChannels(listOf(0u, 1u)) }; runCurrent()
                assertEquals(if (pipeline) 3 else 2, f.transport.sent.size)
                f.transport.receive(channelPacket(0)); runCurrent()
                assertEquals(3, f.transport.sent.size)
                f.transport.receive(channelPacket(1)); runCurrent()
                if (pipeline) { advanceTimeBy(20); runCurrent() }
                assertEquals(listOf<UByte>(0u, 1u), task.await().received.map { it.index })
                assertEquals(if (pipeline && without) 2 else 0, f.transport.unacknowledgedWrites)
                assertEquals(if (pipeline && without) 1 else 3, f.transport.acknowledgedWrites)
                f.session.stop()
            }
        }
    }

    @TestFactory
    fun boundaries() = listOf(
        nativeCase("synchronous responses and unsolicited bursts have one lossless receive drain") {
            val f = fixture(); start(f)
            val acknowledgements = f.session.events(EventFilter.anyAcknowledgement)
            f.transport.onSend = {
                assertEquals(hex("14"), it)
                repeat(1000) { f.transport.receive(raw(0x80, filled(it % 256, 32))) }
                f.transport.error(99)
                f.transport.receive(ackPacket(hex("11223344")))
                f.transport.receive(batteryPacket(4018))
            }
            assertEquals(4018L, f.session.getBattery().level)
            val received = checkedRequest { acknowledgements.toList() }; runCurrent()
            f.session.stop(); runCurrent()
            assertEquals(listOf<MeshEvent>(MeshEvent.Acknowledgement(hex("11223344"))), received.await())
            assertEquals(1, f.transport.maximumCollectors)
            assertEquals(0, f.transport.activeCollectors)
        },
        nativeCase("tracked finish drains pending events and closed-generation subscriptions end immediately") {
            val f = fixture(); start(f)
            val tracked = f.session.eventsTracked()
            f.transport.receive(ackPacket(hex("01020304")))
            f.transport.receive(ackPacket(hex("05060708"))); runCurrent()
            f.session.finishEvents(tracked.id)
            f.transport.receive(ackPacket(hex("090a0b0c"))); runCurrent()
            val events = tracked.stream.toList()
            assertEquals(listOf(hex("01020304"), hex("05060708")), events.filterIsInstance<MeshEvent.Acknowledgement>().map { it.code })
            f.session.stop()
            assertTrue(f.session.events().toList().isEmpty())
            assertTrue(f.session.events(EventFilter.anyAcknowledgement).toList().isEmpty())
        },
        nativeCase("cancelling a public waiter propagates cancellation and removes its subscription") {
            val f = fixture(); start(f)
            val waiter = checkedRequest { f.session.waitForEvent(EventFilter.anyAcknowledgement) }; runCurrent()
            assertEquals(1, f.session.core.generation().dispatcher.subscriberCount)
            waiter.cancel(); runCurrent()
            assertFailsWith<CancellationException> { waiter.await() }
            assertEquals(0, f.session.core.generation().dispatcher.subscriberCount)
            f.session.stop()
        },
        nativeCase("uncorrelated late reply after expiry cannot satisfy a new same-family operation") {
            val f = fixture(); start(f)
            val first = checkedRequest { f.session.getBattery() }; runCurrent()
            advanceTimeBy(1000); runCurrent()
            assertFailsWith<MeshCoreException.Timeout> { first.await() }
            f.transport.receive(batteryPacket(1111)); runCurrent()
            val failure = assertFailsWith<MeshCoreException.ConnectionLost> { f.session.getBattery() }
            assertEquals("battery", generateSequence<Throwable>(failure) { it.cause }
                .filterIsInstance<SessionCorrelationException.UnresolvedReply>().single().responseFamily)
            assertEquals(2, f.transport.sent.size)
            f.session.stop()
            start(f)
            val fresh = command(f, hex("14"), batteryPacket(2222)) { f.session.getBattery() }
            assertEquals(2222L, fresh.level)
            f.session.stop()
        },
        nativeCase("cancellation during a suspended send does not commit a frame and records uncertainty") {
            val f = fixture(); start(f)
            val gate = CompletableDeferred<Unit>()
            f.transport.beforeSend = { if (it == hex("14")) gate.await() }
            val task = checkedRequest { f.session.getBattery() }; runCurrent()
            assertEquals(1, f.transport.sent.size)
            task.cancel(); runCurrent()
            assertFailsWith<CancellationException> { task.await() }
            gate.complete(Unit); runCurrent()
            assertEquals(1, f.transport.sent.size)
            assertEquals(0, f.session.core.generation().pending.size)
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.getBattery() }
            f.session.stop()
        },
        nativeCase("post-write send failure does not leave a success-shaped new exchange") {
            val f = fixture(); start(f)
            f.transport.onSend = { throw MeshTransportError.SendFailed("test send failed after writing") }
            assertFailsWith<MeshTransportError.SendFailed> { f.session.getBattery() }
            assertEquals(2, f.transport.sent.size)
            f.transport.receive(batteryPacket(1111)); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.getBattery() }
            assertEquals(2, f.transport.sent.size)
            f.session.stop()
        },
        nativeCase("EOF promptly fails waiters and ends all per-connection streams") {
            val f = fixture(); start(f)
            val events = f.session.eventsTracked()
            val listener = checkedRequest { events.stream.toList() }; runCurrent()
            val battery = checkedRequest { f.session.getBattery() }; runCurrent()
            f.transport.disconnect(); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { battery.await() }
            assertTrue(listener.isCompleted)
            assertIs<MeshEvent.ConnectionStateChanged>(listener.await().last())
            assertNull(f.session.currentSelfInfo)
            assertEquals(0, f.transport.activeCollectors)
            f.session.stop()
        },
        nativeCase("receive-child failure retains its actual cause rather than timing out") {
            val f = fixture(); start(f)
            val failure = MeshTransportError.ConnectionFailed("deterministic reader failure")
            f.transport.onReceive = { if (it == hex("fe")) throw failure }
            val states = mutableListOf<ConnectionState>()
            backgroundScope.launch { f.session.connectionState.collect { states += it } }; runCurrent()
            val battery = checkedRequest { f.session.getBattery() }; runCurrent()
            f.transport.receive(hex("fe")); runCurrent()
            assertSame(failure, assertFailsWith<MeshCoreException.ConnectionLost> { battery.await() }.cause)
            assertSame(failure, assertIs<ConnectionState.Failed>(states.last()).error)
            assertTrue(f.diagnostics.any { it is SessionDiagnostic.StreamEnded && it.cause === failure })
            assertEquals(0, f.transport.activeCollectors)
            f.session.stop()
        },
        nativeCase("rapid reconnect joins old drains and isolates listeners contacts time and ACK state") {
            val f = fixture()
            repeat(10) { index ->
                start(f, selfPacket(filled(index + 1, 32)))
                val generation = f.session.core.generation()
                val oldEvents = checkedRequest { f.session.events().toList() }; runCurrent()
                f.transport.receive(contactPacket(filled(0x44, 32)))
                f.transport.receive(raw(0x09, little32(123))); runCurrent()
                assertEquals(1, f.session.cachedContacts.size)
                assertEquals(Instant.ofEpochSecond(123), f.session.deviceTime)
                val oldStream = f.transport.mock.receivedData()
                f.session.stop(); runCurrent()
                assertTrue(generation.job.isCompleted)
                assertTrue(oldEvents.isCompleted)
                assertTrue(oldStream.toList().isEmpty())
                assertTrue(f.session.cachedContacts.isEmpty())
                assertNull(f.session.deviceTime)
                assertNull(f.session.currentSelfInfo)
                assertEquals(0, f.transport.activeCollectors)
            }
            assertEquals(1, f.transport.maximumCollectors)
        },
        nativeCase("transport-retaining teardown cannot silently reuse an unprovable receive generation") {
            val f = fixture(); start(f)
            f.session.stop(false)
            assertTrue(f.transport.isConnected())
            assertEquals(0, f.transport.disconnects)
            val failure = assertFailsWith<MeshCoreException.ConnectionLost> { f.session.start() }
            assertIs<SessionCorrelationException.RetainedTransport>(failure.cause)
            f.session.stop(true)
            assertEquals(1, f.transport.disconnects)
        },
        nativeCase("binary mismatch duplicate and retired tag reuse are explicit rather than cached heuristics") {
            val f = fixture(); start(f)
            val key = filled(0x33, 32)
            val first = checkedRequest { f.session.requestOwnerInfo(key) }; runCurrent()
            f.transport.receive(sentPacket(hex("01020304")))
            f.transport.receive(sentPacket(hex("01020304")))
            f.transport.receive(binaryPacket(hex("11223344"), Bytes.utf8("wrong"))); runCurrent()
            assertFalse(first.isCompleted)
            f.transport.receive(binaryPacket(hex("01020304"), Bytes.utf8("1\nNode\nOwner"))); runCurrent()
            assertEquals("Node", first.await().nodeName)
            val second = checkedRequest { f.session.requestOwnerInfo(key) }; runCurrent()
            f.transport.receive(sentPacket(hex("01020304"))); runCurrent()
            val failure = assertFailsWith<MeshCoreException.ConnectionLost> { second.await() }
            assertEquals(hex("01020304"), generateSequence<Throwable>(failure) { it.cause }
                .filterIsInstance<SessionCorrelationException.ReusedTag>().single().tag)
            assertEquals(0, f.session.core.generation().pending.size)
            f.session.stop()
        },
        nativeCase("binary response before messageSent cannot be guessed into a caller context") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.requestOwnerInfo(filled(0x33, 32)) }; runCurrent()
            f.transport.receive(binaryPacket(hex("01020304"), Bytes.utf8("wrong"))); runCurrent()
            assertFalse(task.isCompleted)
            f.transport.receive(sentPacket(hex("01020304"))); runCurrent()
            assertFalse(task.isCompleted)
            f.transport.receive(binaryPacket(hex("01020304"), Bytes.utf8("1\nright\nOwner"))); runCurrent()
            assertEquals("right", task.await().nodeName)
            f.session.stop()
        },
        nativeCase("binary errors after messageSent remain diagnosed while the earlier request can answer") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.requestOwnerInfo(filled(0x33, 32)) }; runCurrent()
            f.transport.receive(sentPacket()); f.transport.error(6); runCurrent()
            assertFalse(task.isCompleted)
            assertTrue(f.diagnostics.any { it is SessionDiagnostic.BackgroundFailure && it.operation == "binary-device-error-after-send" })
            f.transport.receive(binaryPacket(hex("aabbccdd"), Bytes.utf8("1\nNode\nOwner"))); runCurrent()
            assertEquals("Node", task.await().nodeName)
            f.session.stop()
        },
        nativeCase("contact stream hard cap wins even when every inactivity window makes progress") {
            val configuration = SessionConfiguration(clientIdentifier = "MCore", contactStreamInactivityTimeout = 0.1, contactStreamHardTimeout = 0.35)
            val f = fixture(configuration); start(f)
            val task = checkedRequest { f.session.getContactsReportingTotal() }; runCurrent()
            f.transport.receive(contactsStart(100)); runCurrent()
            repeat(4) { index ->
                advanceTimeBy(80); f.transport.receive(contactPacket(filled(index + 1, 32))); runCurrent()
            }
            assertFalse(task.isCompleted)
            advanceTimeBy(30); runCurrent()
            assertFailsWith<MeshCoreException.Timeout> { task.await() }
            assertEquals(4L, f.session.lastContactFetchProgress?.receivedCount)
            assertEquals(100L, f.session.lastContactFetchProgress?.reportedTotal)
            assertEquals(4, f.session.cachedContacts.size)
            assertTrue(f.session.isContactsDirty)
            f.session.stop()
        },
        nativeCase("partial contact device error preserves received contacts without claiming completion") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.getContactsReportingTotal() }; runCurrent()
            f.transport.receive(contactsStart(2)); f.transport.receive(contactPacket()); f.transport.error(3); runCurrent()
            assertEquals(3u.toUByte(), assertFailsWith<MeshCoreException.DeviceError> { task.await() }.code)
            assertEquals(1, f.session.cachedContacts.size)
            assertEquals(1L, f.session.lastContactFetchProgress?.receivedCount)
            assertEquals(false, f.session.lastContactFetchProgress?.completed)
            f.session.stop()
        },
        nativeCase("pipeline refill failure is thrown and unresolved indexes cannot steal orphan replies") {
            val configuration = SessionConfiguration(clientIdentifier = "MCore", channelPipelineWindow = 2)
            val f = fixture(configuration, SessionRadioTransport(true)); start(f)
            f.transport.mock.failSends(4)
            val task = checkedRequest { f.session.getChannels(listOf(0u, 1u, 2u)) }; runCurrent()
            f.transport.receive(channelPacket(0)); runCurrent()
            assertFailsWith<MockTransportException.SendFailed> { task.await() }
            assertEquals(3, f.transport.sent.size)
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.getChannel(1u) }
            assertEquals(0, f.session.core.generation().pending.size)
            f.session.stop()
        },
        nativeCase("missing channel reconciliation requires a fresh connection when an old reply remains possible") {
            val f = fixture(transport = SessionRadioTransport(true)); start(f)
            val task = checkedRequest { f.session.getChannels(listOf(0u, 1u)) }; runCurrent()
            f.transport.receive(channelPacket(0)); runCurrent()
            advanceTimeBy(200); runCurrent()
            assertEquals(listOf<UByte>(1u), task.await().missing)
            f.transport.receive(channelPacket(1, "orphan")); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { f.session.getChannel(1u) }
            f.session.stop()
        },
        nativeCase("export contact correlation checks the leading key not a key embedded in another card") {
            val f = fixture(); start(f)
            val key = filled(0x11, 32)
            val task = checkedRequest { f.session.exportContact(key) }; runCurrent()
            f.transport.receive(raw(0x0b, filled(0x22, 32) + key)); runCurrent()
            assertFalse(task.isCompleted)
            f.transport.receive(raw(0x0b, key + hex("abcd"))); runCurrent()
            assertEquals("meshcore://${key.hexString}abcd", task.await())
            f.session.stop()
        },
        nativeCase("region restoration failure preserves original firmware error and is independently observable") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.requestRegions(testContact()) }; runCurrent()
            f.transport.ok(); runCurrent()
            f.transport.error(10); runCurrent()
            assertEquals(0x0du.toUByte(), f.transport.sent.last()[0])
            f.transport.error(4); runCurrent()
            val failure = assertFailsWith<MeshCoreException.DeviceError> { task.await() }
            assertEquals(10u.toUByte(), failure.code)
            assertEquals(4u.toUByte(), assertIs<MeshCoreException.DeviceError>(failure.suppressed.single()).code)
            assertTrue(f.diagnostics.any { it is SessionDiagnostic.RestoreFailure })
            f.session.stop()
        },
        nativeCase("successful region data does not pretend that a failed restoration succeeded") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.requestRegions(testContact()) }; runCurrent()
            f.transport.ok(); runCurrent()
            f.transport.receive(sentPacket())
            f.transport.receive(binaryPacket(hex("aabbccdd"), little32(1) + Bytes.utf8("Europe"))); runCurrent()
            f.transport.error(4); runCurrent()
            assertEquals(4u.toUByte(), assertFailsWith<MeshCoreException.DeviceError> { task.await() }.code)
            f.session.stop()
        },
        nativeCase("cancelling a region request after the route write still performs bounded owned restoration") {
            val f = fixture(); start(f)
            val task = checkedRequest { f.session.requestRegions(testContact()) }; runCurrent()
            f.transport.ok(); runCurrent()
            task.cancel(); runCurrent()
            assertFailsWith<CancellationException> { task.await() }
            f.transport.receive(sentPacket())
            f.transport.receive(binaryPacket(hex("aabbccdd"), little32(1) + Bytes.utf8("Europe"))); runCurrent()
            assertEquals(0x0du.toUByte(), f.transport.sent.last()[0])
            f.transport.ok(); runCurrent()
            assertEquals(0, f.session.core.generation().pending.size)
            f.session.stop()
        },
        nativeCase("stop unblocks queued requests auto drains and contact refresh exactly once") {
            val f = fixture(); start(f)
            f.session.startAutoMessageFetching(); f.session.setAutoUpdateContacts(true)
            f.transport.receive(raw(0x83)); f.transport.receive(raw(0x80, filled(1, 32))); runCurrent()
            val generation = f.session.core.generation()
            val queued = checkedRequest { f.session.getBattery() }; runCurrent()
            f.session.stop(); runCurrent()
            assertFailsWith<MeshCoreException.ConnectionLost> { queued.await() }
            assertTrue(generation.job.isCompleted)
            assertEquals(0, generation.pending.size)
            assertEquals(0, generation.dispatcher.subscriberCount)
            assertEquals(1, f.transport.disconnects)
            f.session.stop()
            assertEquals(1, f.transport.disconnects)
        },
        nativeCase("malformed and empty raw packets are observable and never satisfy a typed request") {
            val f = fixture(); start(f)
            val tracked = f.session.eventsTracked()
            val task = checkedRequest { f.session.getBattery() }; runCurrent()
            f.transport.receive(Bytes.EMPTY); f.transport.receive(hex("0c01")); runCurrent()
            assertFalse(task.isCompleted)
            f.transport.receive(batteryPacket(4018)); runCurrent()
            assertEquals(4018L, task.await().level)
            f.session.finishEvents(tracked.id)
            assertEquals(2, tracked.stream.toList().filterIsInstance<MeshEvent.ParseFailure>().size)
            assertEquals(2, f.diagnostics.filterIsInstance<SessionDiagnostic.ParseFailure>().size)
            f.session.stop()
        },
    )

    @TestFactory
    fun signatureBoundaries() = listOf(0, 1, 31, 32, 63, 65).map { size ->
        nativeCase("signature length $size is an explicit invalid response") {
            val f = fixture(); start(f)
            assertFailsWith<MeshCoreException.InvalidResponse> {
                command(f, hex("23"), raw(0x14, filled(1, size))) { f.session.signFinish() }
            }
            assertEquals(0, f.session.core.generation().pending.size)
            f.session.stop()
        }
    } + nativeCase("missing signature uses the exact three-times-default deadline") {
        val f = fixture(); start(f)
        val task = checkedRequest { f.session.signFinish() }; runCurrent()
        advanceTimeBy(2999); runCurrent(); assertFalse(task.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertFailsWith<MeshCoreException.Timeout> { task.await() }
        assertEquals(3000L, testScheduler.currentTime)
        f.session.stop()
    }

    @TestFactory
    fun invalidInputs() = listOf<suspend MeshCoreSession.() -> Unit>(
        { sendMessage(filled(1, 5), "text") },
        { sendLogin(filled(1, 31), "password") },
        { sendLogout(filled(1, 31)) },
        { sendKeepAlive(filled(1, 31), 0u) },
        { sendPathDiscovery(filled(1, 31)) },
        { sendChannelData(0u, 0u, Bytes.EMPTY) },
        { sendChannelData(0u, 1u, Bytes.EMPTY, 0xc0u, Bytes.EMPTY) },
        { sendChannelData(0u, 1u, Bytes.EMPTY, 3u, hex("1122")) },
        { setChannel(0u, "short", filled(1, 15)) },
        { setFloodScope(filled(1, 15)) },
        { changeContactPath(testContact(), hex("1122"), 3u) },
        { sign(Bytes.EMPTY, 0) },
        { sign(Bytes.EMPTY, -1) },
        { sendMessageWithRetry(filled(1, 32), "text", maxAttempts = 257) },
    ).mapIndexed { index, operation ->
        nativeCase("invalid session input $index rejects before any write") {
            val f = fixture()
            assertFailsWith<MeshCoreException.InvalidInput> { f.session.operation() }
            assertTrue(f.transport.sent.isEmpty())
            f.session.stop()
        }
    }
}
