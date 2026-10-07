// AndroidOnly: WP-207 Source second break-glass: a forced connect abandons a transport auto-reconnect the coordinator no longer owns.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BreakGlassTest {
    @TestFactory
    fun breakGlassCases() = listOf(
        nativeCase("forced connect abandons a stuck transport auto-reconnect and makes a fresh attempt") {
            withFixture {
                platform.state = platform.state.copy(autoReconnecting = true, connectedDeviceId = platform.target.deviceId)
                assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                manager.connect(platform.target, forceFullSync = false, forceReconnect = true)
                runCurrent()
                assertEquals(DeviceConnectionState.READY, manager.connectionState, "Must not defer to the stuck reconnect again")
                assertEquals(1, links.first().callbackClosures, "The abandoned restoration route must be torn down")
                assertNull(manager.reconnectionCoordinator.reconnectingDeviceId)
            }
        },
        nativeCase("an unforced connect still defers to a transport auto-reconnect of the same device") {
            withFixture {
                platform.state = platform.state.copy(autoReconnecting = true, connectedDeviceId = platform.target.deviceId)
                manager.connect(platform.target)
                runCurrent()
                assertEquals(DeviceConnectionState.CONNECTING, manager.connectionState)
                assertEquals(platform.target.deviceId, manager.reconnectionCoordinator.reconnectingDeviceId)
                assertTrue(radios.all { it.collectors == 0 }, "Deferral must not start a competing session")
            }
        },
    )
}
