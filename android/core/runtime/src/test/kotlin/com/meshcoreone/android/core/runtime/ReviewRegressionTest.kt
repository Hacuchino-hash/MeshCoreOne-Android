// AndroidOnly: WP-207 Regression assertions for independent-review findings on breaker scope, owned-link fallthrough and watchdog ownership.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.model.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRegressionTest {
    @TestFactory
    fun reviewCases() = listOf(
        nativeCase("a failed WiFi connect leaves the BLE circuit breaker closed and the next WiFi attempt reaches the radio") {
            withFixture {
                val wifi = ConnectionTarget.WiFi("localhost", 5000u)
                createRadio = { TestRadio().also { it.connectFailure = IllegalStateException("wifi unreachable") } }
                assertFails { manager.connect(wifi) }
                runCurrent()
                assertTrue(manager.shouldAllowConnection(false), "Swift never records WiFi failures in the BLE breaker")
                val before = radios.size
                assertFails { manager.connect(wifi) }
                runCurrent()
                assertEquals(before + 1, radios.size, "The second WiFi attempt must reach the transport, not the breaker")
                assertTrue(manager.shouldAllowConnection(false))
            }
        },
        nativeCase("a failed BLE connect still opens the circuit breaker") {
            withFixture {
                createRadio = { TestRadio().also { it.connectFailure = IllegalStateException("ble unreachable") } }
                assertFails { manager.connect(platform.target) }
                runCurrent()
                assertFalse(manager.shouldAllowConnection(false))
            }
        },
        nativeCase("an owned system-connected link that cannot be adopted falls through to a normal connect") {
            withFixture {
                last.persist(platform.target.deviceId, RadioId(UUID.randomUUID()), "Stored")
                platform.state = platform.state.copy(systemConnected = true)
                platform.adoptionSucceeds = false
                manager.connect(platform.target)
                runCurrent()
                assertEquals(1, platform.adopts)
                assertEquals(DeviceConnectionState.READY, manager.connectionState)
                assertEquals(1, links.first().callbackClosures, "The abandoned adoption route must release its callbacks")
                assertEquals(1, radios.last().collectors)
                assertTrue(radios.dropLast(1).all { it.collectors == 0 })
            }
        },
        nativeCase("an owned system-connected link in a non-idle phase connects normally without adoption") {
            withFixture {
                last.persist(platform.target.deviceId, RadioId(UUID.randomUUID()), "Stored")
                platform.state = platform.state.copy(systemConnected = true, phase = "discovering")
                manager.connect(platform.target)
                runCurrent()
                assertEquals(0, platform.adopts)
                assertEquals(DeviceConnectionState.READY, manager.connectionState)
            }
        },
        nativeCase("a system-connected link that is not ours is still rejected as connected to another app") {
            withFixture {
                platform.state = platform.state.copy(systemConnected = true)
                assertFailsWith<LinkFailure.DeviceConnectedToOtherApp> { manager.connect(platform.target) }
                assertEquals(0, platform.adopts)
            }
        },
        nativeCase("a watchdog whose own reconnect fails finishes instead of looping on untracked") {
            withFixture {
                connect(); manager.disconnect(RuntimeDisconnectReason.WIFI_RECONNECT_PREP)
                assertTrue(manager.connectionIntent.wantsConnection)
                createRadio = { TestRadio().also { it.connectFailure = IllegalStateException("out of range") } }
                manager.startReconnectionWatchdog()
                runCurrent()
                val connectsBefore = radios.size
                advanceTimeBy(45_000); runCurrent()
                assertTrue(radios.size > connectsBefore, "The first watchdog tick must attempt a reconnect")
                assertFalse(manager.isReconnectionWatchdogRunning)
                val afterFailure = radios.size
                advanceTimeBy(10 * 60_000); runCurrent()
                assertEquals(afterFailure, radios.size, "No untracked watchdog loop may keep reconnecting")
            }
        },
    )
}
