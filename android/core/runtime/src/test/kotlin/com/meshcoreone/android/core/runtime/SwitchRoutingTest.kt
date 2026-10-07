// AndroidOnly: WP-207 connect(to:) to a different connected radio routes through switchDevice, as the source does.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchRoutingTest {
    @TestFactory
    fun switchCases() = listOf(
        nativeCase("connect to a different radio while ready switches with a forced full sync") {
            withFixture {
                connect()
                assertEquals(listOf(false), services.single().syncForces)
                val other = target()
                createRadio = { TestRadio().also { it.key = Bytes(ByteArray(32) { 0x5a }) } }
                manager.connect(other)
                runCurrent()
                assertEquals(DeviceConnectionState.READY, manager.connectionState)
                assertEquals(other.deviceId, manager.connectedDevice!!.id)
                assertEquals(listOf(true), services.last().syncForces, "Swift's switchDevice forces a full sync")
                assertEquals(0, lossCount)
            }
        },
        nativeCase("a failed switch to a different radio reports the connection loss") {
            withFixture {
                connect()
                createRadio = { TestRadio().also { it.connectFailure = IllegalStateException("new radio unreachable") } }
                val work = backgroundScope.async { runCatching { manager.connect(target()) } }
                advanceUntilIdle()
                assertTrue(work.await().isFailure)
                assertEquals(1, lossCount, "Swift's switchDevice catch fires onConnectionLost")
            }
        },
        nativeCase("connect to the same ready radio stays a no-op") {
            withFixture {
                connect()
                manager.connect(platform.target)
                runCurrent()
                assertEquals(1, radios.size); assertEquals(1, services.size); assertEquals(0, lossCount)
            }
        },
    )
}
