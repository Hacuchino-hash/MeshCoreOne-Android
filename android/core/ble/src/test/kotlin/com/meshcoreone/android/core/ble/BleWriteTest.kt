// PortedFrom: MC1Services/Tests/MC1ServicesTests/Transport/BLEStateMachineWriteWithoutResponseTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Tests/MC1ServicesTests/Transport/BLEStateMachineRestorationAndTeardownTests.swift@db14559b39d32322b06477c6ae676112f583db50
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.meshcoreone.android.core.ble

import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BleWriteTest {
    @Test fun `without response capability requires characteristic and verified firmware evidence`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        assertFalse(fixture.transport.supportsWriteWithoutResponse())
        fixture.verifyFirmware(commands = true)
        assertTrue(fixture.transport.supportsWriteWithoutResponse())
        assertFalse(fixture.transport.supportsPipelinedReads())
        fixture.transport.disconnect()
        assertFalse(fixture.transport.supportsWriteWithoutResponse())
    }

    @Test fun `write only ESP32 characteristic stays on acknowledged writes`() = runTest {
        val fixture = BleFixture()
        fixture.services = listOf(GattService(NusUuid.SERVICE, listOf(
            GattCharacteristic(NusUuid.TX, setOf(GattProperty.Write), emptyList()), fixture.rx,
        )))
        fixture.transport.connect()
        fixture.verifyFirmware(commands = true, pipelining = true)
        assertFalse(fixture.transport.supportsWriteWithoutResponse())
        assertFalse(fixture.transport.supportsPipelinedReads())
        fixture.transport.sendWithoutResponse(Bytes.of(0x16, 0x03))
        assertEquals(GattWriteMode.WithResponse, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().single().mode)
        fixture.transport.disconnect()
    }

    @Test fun `unverified without response falls back to acknowledged send`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.transport.sendWithoutResponse(Bytes.of(0x16, 0x03))
        assertEquals(GattWriteMode.WithResponse, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().single().mode)
        fixture.transport.disconnect()
    }

    @Test fun `pipelining is independent firmware opt in not inferred from MTU`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.verifyFirmware(commands = true, pipelining = true)
        assertTrue(fixture.transport.supportsWriteWithoutResponse())
        assertTrue(fixture.transport.supportsPipelinedReads())
        fixture.transport.disconnect()
    }

    @Test fun `larger unverified firmware frames fail before issuing or truncating`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        val failure = assertFailsWith<BleTransportException> { fixture.transport.send(Bytes(ByteArray(21) { 0x7f })) }
        assertEquals(BleError.FirmwareCapabilityUnverified(21, 20), failure.error)
        assertTrue(fixture.connection.operations.values.none { it.kind == GattOperationKind.Write })
        assertTrue(fixture.transport.isConnected())
        fixture.transport.disconnect()
    }

    @Test fun `MTU minus three and the firmware limit both constrain complete writes`() = runTest {
        val fixture = BleFixture()
        fixture.mtu = 185
        fixture.transport.connect()
        fixture.verifyFirmware(maximum = 256)
        fixture.transport.send(Bytes(ByteArray(182) { 0x55 }))
        assertEquals(BleError.FrameTooLarge(183, 182),
            assertFailsWith<BleTransportException> { fixture.transport.send(Bytes(ByteArray(183))) }.error)
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.transport.disconnect()
    }

    @Test fun `ATT attribute maximum is 512 and oversized commands are never split`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.verifyFirmware()
        val maximum = Bytes(ByteArray(512) { (it and 0xff).toByte() })
        fixture.transport.send(maximum)
        assertEquals(BleError.FrameTooLarge(513, 512),
            assertFailsWith<BleTransportException> { fixture.transport.send(Bytes(ByteArray(513))) }.error)
        assertEquals(listOf(maximum), fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().map { it.data })
        fixture.transport.disconnect()
    }

    @Test fun `firmware maximum can be smaller than ATT capability`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.verifyFirmware(maximum = 40)
        assertEquals(40, fixture.transport.diagnostics.value.maximumCommandBytes)
        assertEquals(BleError.FrameTooLarge(41, 40),
            assertFailsWith<BleTransportException> { fixture.transport.send(Bytes(ByteArray(41))) }.error)
        fixture.transport.disconnect()
    }

    @Test fun `firmware capabilities cannot be replaced or inherited by a new generation`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        val old = fixture.transport.diagnostics.value.generation
        fixture.verifyFirmware(maximum = 40)
        assertEquals(BleError.FirmwareCapabilitiesAlreadyVerified,
            assertFailsWith<BleTransportException> { fixture.verifyFirmware(maximum = 512) }.error)
        fixture.transport.disconnect()
        fixture.transport.connect()
        assertFalse(fixture.transport.diagnostics.value.firmwareVerified)
        assertEquals(BleError.StaleGeneration(old, old + 1),
            assertFailsWith<BleTransportException> {
                fixture.transport.updateFirmwareCapabilities(old, FirmwareFrameCapabilities(512, evidence = "old peer"))
            }.error)
        fixture.transport.disconnect()
    }

    @Test fun `concurrent writes are serialized through completion not just initial calls`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val first = async { fixture.transport.send(Bytes.of(0x01, 0x80)) }
        runCurrent()
        val firstRequest = requireNotNull(fixture.pending)
        val second = async { fixture.transport.send(Bytes.of(0x02, 0xff)) }
        runCurrent()
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.complete(operation = firstRequest)
        runCurrent()
        first.await()
        assertFalse(second.isCompleted)
        assertEquals(2, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.complete()
        runCurrent()
        second.await()
        assertEquals(listOf(Bytes.of(0x01, 0x80), Bytes.of(0x02, 0xff)),
            fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().map { it.data })
        fixture.transport.disconnect()
    }

    @Test fun `write commands and acknowledged writes share the entire operation queue`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.verifyFirmware(commands = true)
        fixture.pause = GattOperationKind.Write
        val command = async { fixture.transport.sendWithoutResponse(Bytes.of(1)) }
        runCurrent()
        val commandRequest = requireNotNull(fixture.pending)
        val acknowledged = async { fixture.transport.send(Bytes.of(2)) }
        runCurrent()
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.complete(operation = commandRequest)
        runCurrent()
        command.await()
        assertFalse(acknowledged.isCompleted)
        fixture.complete()
        runCurrent()
        acknowledged.await()
        assertEquals(listOf(GattWriteMode.WithoutResponse, GattWriteMode.WithResponse),
            fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().map { it.mode })
        fixture.transport.disconnect()
    }

    @Test fun `write readiness timeout is exactly five seconds and invalidates its GATT`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val result = async { assertFailsWith<BleTransportException> { fixture.transport.sendWithoutResponse(Bytes.of(1)) } }
        runCurrent()
        fixture.clock.advanceBy(4_999.milliseconds)
        runCurrent()
        assertFalse(result.isCompleted)
        fixture.clock.advanceBy(1.milliseconds)
        runCurrent()
        assertEquals(BleError.OperationTimeout, result.await().error)
        assertEquals(1, fixture.connection.closeCalls.values.size)
        assertFalse(fixture.transport.isConnected())
        assertEquals(0, fixture.clock.sleeperCount)
    }

    @Test fun `cancelling a queued send does not cancel the unrelated active write`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val first = async { fixture.transport.send(Bytes.of(1)) }
        runCurrent()
        val queued = async { fixture.transport.send(Bytes.of(2)) }
        runCurrent()
        queued.cancelAndJoin()
        assertTrue(fixture.transport.isConnected())
        assertEquals(0, fixture.connection.closeCalls.values.size)
        fixture.complete()
        runCurrent()
        first.await()
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.transport.disconnect()
    }

    @Test fun `queued old generation send cannot write to a rapid reconnection`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val first = async { assertFailsWith<BleTransportException> { fixture.transport.send(Bytes.of(1)) } }
        runCurrent()
        val queued = async { assertFailsWith<BleTransportException> { fixture.transport.send(Bytes.of(2)) } }
        runCurrent()
        val old = fixture.connection
        fixture.transport.disconnect()
        fixture.pause = null
        fixture.transport.connect()
        runCurrent()
        assertEquals(BleError.NotConnected, first.await().error)
        assertEquals(BleError.NotConnected, queued.await().error)
        assertTrue(fixture.connection.operations.values.none { it.kind == GattOperationKind.Write })
        assertEquals(1, old.closeCalls.values.size)
        fixture.transport.disconnect()
    }

    @Test fun `stale write callback is dropped instead of resuming the newer write`() = runTest {
        val fixture = BleFixture()
        fixture.transport.connect()
        fixture.pause = GattOperationKind.Write
        val first = async { fixture.transport.send(Bytes.of(1)) }
        runCurrent()
        val previous = requireNotNull(fixture.pending)
        fixture.complete()
        runCurrent()
        first.await()
        val next = async { fixture.transport.send(Bytes.of(2)) }
        runCurrent()
        fixture.connection.reply(previous, GattReply.Written)
        runCurrent()
        assertFalse(next.isCompleted)
        assertEquals(1L, fixture.transport.diagnostics.value.rejectedCallbacks)
        fixture.complete()
        runCurrent()
        next.await()
        fixture.transport.disconnect()
    }

    @Test fun `write pacing applies to sequential and queued acknowledged writes`() = runTest {
        val fixture = BleFixture(BleConfiguration(writePacing = 1.seconds))
        fixture.transport.connect()
        fixture.transport.send(Bytes.of(1))
        val second = async { fixture.transport.send(Bytes.of(2)) }
        runCurrent()
        fixture.clock.advanceBy(999.milliseconds)
        runCurrent()
        assertFalse(second.isCompleted)
        assertEquals(1, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.clock.advanceBy(1.milliseconds)
        runCurrent()
        second.await()
        assertEquals(2, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.transport.disconnect()
    }

    @Test fun `verified write commands skip acknowledged write pacing as in the source`() = runTest {
        val fixture = BleFixture(BleConfiguration(writePacing = 1.seconds))
        fixture.transport.connect()
        fixture.verifyFirmware(commands = true)
        fixture.transport.send(Bytes.of(1))
        fixture.transport.sendWithoutResponse(Bytes.of(2))
        assertEquals(0.milliseconds, fixture.clock.now)
        assertEquals(2, fixture.connection.operations.values.filterIsInstance<GattOperation.Write>().size)
        fixture.transport.disconnect()
    }
}
