// AndroidOnly: WP-205 Independent review regressions for queue deadlines, teardown pacing and status domains.
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.meshcoreone.android.core.ble

import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BleReviewRegressionTest {
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
}
