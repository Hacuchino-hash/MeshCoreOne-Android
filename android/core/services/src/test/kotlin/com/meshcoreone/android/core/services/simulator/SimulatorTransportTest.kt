// AndroidOnly: WP-217 native checks for the simulator transport and connection mode (no real radio or network).
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.MeshTransportError
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.blocking
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.native
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.oracleNow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class SimulatorTransportTest {
    private val frame = Bytes.of(0x16, 0x03, 0x01)

    @TestFactory
    fun transportTests(): List<DynamicTest> = listOf(
        native("simulator transport rejects sends until connected") {
            val transport = SimulatorMockTransport()
            assertFalse(blocking { transport.isConnected() })
            val error = assertFailsWith<MeshTransportError> { blocking { transport.send(frame) } }
            assertSame(MeshTransportError.NotConnected, error)
        },
        native("connected simulator transport accepts sends and never replies") {
            val transport = SimulatorMockTransport()
            val received = blocking {
                transport.connect()
                assertTrue(transport.isConnected())
                // A collector is already waiting, so any reply or echo would be delivered to it.
                val collector = async(start = CoroutineStart.UNDISPATCHED) { transport.receivedData().toList() }
                repeat(3) { transport.send(frame) }
                transport.sendWithoutResponse(frame)
                assertFalse(transport.supportsWriteWithoutResponse())
                transport.disconnect()
                collector.await()
            }
            assertEquals(emptyList(), received)
        },
        native("disconnect finishes the received stream for good") {
            val transport = SimulatorMockTransport()
            blocking {
                transport.connect()
                val collector = async(start = CoroutineStart.UNDISPATCHED) { transport.receivedData().toList() }
                transport.disconnect()
                assertEquals(emptyList(), collector.await())
                assertFailsWith<MeshTransportError> { transport.send(frame) }

                transport.connect()
                transport.send(frame)
                assertEquals(emptyList(), transport.receivedData().toList(), "a reconnect must not reopen the stream")
            }
        },
    )

    @TestFactory
    fun connectionModeTests(): List<DynamicTest> = listOf(
        native("connection mode exposes the simulated device only while connected") {
            val clock = SettableClock(oracleNow)
            val mode = SimulatorConnectionMode(clock, connectDelay = 0.milliseconds)
            assertFalse(mode.isConnected)
            assertNull(mode.device)

            blocking { mode.connect() }
            assertTrue(mode.isConnected)
            clock.advance(30)
            val device = assertNotNull(mode.device)
            assertEquals(MockDataProvider.simulatorDeviceID, device.id)
            assertEquals(MockDataProvider.simulatorRadioId, device.radioId)
            assertEquals("Sim", device.nodeName)
            assertEquals(oracleNow.plusSeconds(30), device.lastConnected)
            assertTrue(device.isActive)

            blocking { mode.disconnect() }
            assertFalse(mode.isConnected)
            assertNull(mode.device)
        },
        native("cancelled connect propagates and leaves the mode disconnected") {
            val mode = SimulatorConnectionMode(SettableClock(oracleNow), connectDelay = 1.hours)
            blocking {
                val connecting = launch(start = CoroutineStart.UNDISPATCHED) { mode.connect() }
                connecting.cancel()
                connecting.join()
                assertTrue(connecting.isCancelled)
            }
            assertFalse(mode.isConnected)
        },
        native("default connect delay matches Swift's brief pause") {
            assertEquals(200.milliseconds, SimulatorConnectionMode.DEFAULT_CONNECT_DELAY)
        },
    )
}
