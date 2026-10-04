// AndroidOnly: WP-205 Independent review regressions for queue deadlines, unissued timeouts, teardown pacing and status domains.
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.meshcoreone.android.core.ble

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.testing.TestClock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BleReviewRegressionTest {
    @Test fun `RSSI timeout before submission leaves the ready link usable and no pending timer`() = runTest {
        val fixture = BleFixture()
        val clock = EagerDeadlineClock(fixture.clock)
        val transport = BleTransport(fixture.facade, clock = clock)
        transport.connect()
        val generation = transport.diagnostics.value.generation
        clock.expireNextSleep = true

        val failure = assertFailsWith<BleTransportException> { transport.readRssi() }
        assertEquals(BleError.OperationTimeout, failure.error)
        assertEquals(GattOperationKind.Rssi, failure.operation)
        assertEquals(6.seconds, clock.now)
        assertEquals(0, fixture.connection.operations.values.filterIsInstance<GattOperation.Rssi>().size)
        assertTrue(transport.isConnected())
        assertEquals(BlePhase.Connected, transport.diagnostics.value.phase)
        assertEquals(generation, transport.diagnostics.value.generation)
        assertEquals(0, fixture.connection.closeCalls.values.size)
        val connection = requireNotNull(BleTransport::class.java.getDeclaredField("active").apply {
            isAccessible = true
        }.get(transport))
        assertNull(connection.javaClass.getDeclaredField("pending").apply {
            isAccessible = true
        }.get(connection))
        assertEquals(0, fixture.clock.sleeperCount)
        assertTrue(clock.timerJobs.all { it.isCompleted })

        fixture.pause = GattOperationKind.Rssi
        val next = async { transport.readRssi() }
        runCurrent()
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Rssi>().size)
        assertEquals(1, fixture.clock.sleeperCount)
        assertFalse(next.isCompleted)
        fixture.complete()
        runCurrent()
        assertEquals(-50, next.await())
        assertTrue(transport.isConnected())
        assertEquals(generation, transport.diagnostics.value.generation)
        assertEquals(0, fixture.connection.closeCalls.values.size)
        assertEquals(0, fixture.clock.sleeperCount)
        assertTrue(clock.timerJobs.all { it.isCompleted })
        transport.disconnect()
    }

    @Test fun `write timeout before submission does not invalidate the ready link`() = runTest {
        val fixture = BleFixture()
        val clock = EagerDeadlineClock(fixture.clock)
        val transport = BleTransport(fixture.facade, clock = clock)
        transport.connect()
        clock.expireNextSleep = true
        val frame = Bytes.of(0x16, 0x03)

        val failure = assertFailsWith<BleTransportException> { transport.send(frame) }
        assertEquals(BleError.OperationTimeout, failure.error)
        assertEquals(GattOperationKind.Write, failure.operation)
        assertEquals(0, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        assertTrue(transport.isConnected())
        assertEquals(0, fixture.connection.closeCalls.values.size)
        assertEquals(0, fixture.clock.sleeperCount)
        assertTrue(clock.timerJobs.all { it.isCompleted })

        transport.send(frame)
        assertEquals(frame, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().single().data)
        assertTrue(transport.isConnected())
        assertEquals(0, fixture.connection.closeCalls.values.size)
        assertEquals(0, fixture.clock.sleeperCount)
        assertTrue(clock.timerJobs.all { it.isCompleted })
        transport.disconnect()
    }

    @Test fun `unsubmitted startup timeout still closes every incomplete setup phase`() = runTest {
        val phases = listOf(
            GattOperationKind.Connect, GattOperationKind.DiscoverServices,
            GattOperationKind.Mtu, GattOperationKind.Subscribe,
        )
        for ((index, kind) in phases.withIndex()) {
            val fixture = BleFixture()
            val clock = EagerDeadlineClock(fixture.clock)
            val transport = BleTransport(fixture.facade, clock = clock)
            clock.expireNextSleep = index == 0
            val create = fixture.facade.onCreate
            fixture.facade.onCreate = { connection ->
                create(connection)
                val submit = connection.onSubmit
                connection.onSubmit = { operation ->
                    submit(operation)
                    if (index > 0 && operation.kind == phases[index - 1]) clock.expireNextSleep = true
                }
            }

            val failure = assertFailsWith<BleTransportException> { transport.connect() }
            assertEquals(BleError.ConnectionTimeout, failure.error)
            assertEquals(kind, failure.operation)
            assertEquals(phases.take(index), fixture.connection.operations.values.map { it.kind })
            assertFalse(transport.isConnected())
            assertEquals(1, fixture.connection.closeCalls.values.size)
            assertEquals(0, fixture.clock.sleeperCount)
            assertTrue(clock.timerJobs.all { it.isCompleted })
        }
    }

    @Test fun `RSSI deadline starts after two valid queued write callbacks complete`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val first = async { fixture.transport.send(Bytes.of(1)) }
        runCurrent()
        val firstRequest = requireNotNull(fixture.pending)
        val second = async { fixture.transport.send(Bytes.of(2)) }
        val rssi = async { fixture.transport.readRssi() }
        runCurrent()
        fixture.clock.advanceBy(3.seconds)
        fixture.complete(operation = firstRequest)
        runCurrent()
        first.await()
        val secondRequest = requireNotNull(fixture.pending)
        assertFalse(rssi.isCompleted)
        fixture.clock.advanceBy(3.seconds)
        fixture.complete(operation = secondRequest)
        runCurrent()
        second.await()
        assertEquals(-50, rssi.await())
        assertEquals(6.seconds, fixture.clock.now)
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Rssi>().size)
        assertTrue(fixture.transport.isConnected())
        assertEquals(0, fixture.connection.closeCalls.values.size)
        fixture.transport.disconnect()
    }

    @Test fun `disconnect wakes acknowledged pacing and new connect issues before the old deadline`() = runTest {
        val fixture = BleFixture(BleConfiguration(writePacing = 20.seconds))
        fixture.transport.connect()
        fixture.transport.send(Bytes.of(1))
        val old = fixture.connection
        val paced = async { assertFailsWith<BleTransportException> { fixture.transport.send(Bytes.of(2)) } }
        runCurrent()
        assertEquals(1, fixture.clock.sleeperCount)
        assertEquals(1, old.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.transport.disconnect()
        val reconnect = async { fixture.transport.connect() }
        runCurrent()
        assertEquals(BleError.NotConnected, paced.await().error)
        reconnect.await()
        assertEquals(0.seconds, fixture.clock.now)
        assertEquals(0, fixture.clock.sleeperCount)
        assertEquals(2, fixture.facade.connections.size)
        assertEquals(GattOperationKind.Connect, fixture.connection.operations.values.first().kind)
        assertTrue(fixture.transport.isConnected())
        assertEquals(1, old.closeCalls.values.size)
        fixture.transport.disconnect()
    }

    @Test fun `connection HCI status is not reinterpreted as ATT by the pending operation`() {
        for (kind in GattOperationKind.entries) {
            val timeout = connectionStateFailure(kind, 8)
            assertEquals(BleError.ConnectionTimeout, timeout.error)
            assertEquals(GattStatusDomain.ConnectionState, timeout.statusDomain)
            assertEquals(BleRecovery.RetryWithNewConnection, timeout.recovery)
            assertEquals(8, timeout.status)
            assertEquals(kind, timeout.operation)
            for (status in listOf(12, 15)) {
                assertTrue(connectionStateFailure(kind, status).error is BleError.ConnectionFailed)
            }
        }
        for (status in listOf(5, 8, 12, 15)) {
            assertEquals(BleError.AuthenticationFailed, gattFailure(GattOperationKind.Write, status).error)
            assertEquals(GattStatusDomain.Att, gattFailure(GattOperationKind.Write, status).statusDomain)
        }
    }

    private class EagerDeadlineClock(private val clock: TestClock) : BleClock {
        override val now: Duration get() = clock.now
        var expireNextSleep = false
        val timerJobs = mutableListOf<Job>()

        override suspend fun sleepUntil(deadline: Duration) {
            timerJobs.add(currentCoroutineContext().job)
            if (expireNextSleep) {
                expireNextSleep = false
                clock.advanceBy(deadline - now + 1.seconds)
            }
            clock.sleepUntil(deadline)
        }
    }
}
