// AndroidOnly: WP-207 Combined switch-routing and owned-teardown observer regression assertions.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchFailureIntegrationTest {
    @TestFactory
    fun failureCases() = listOf(
        nativeCase("a routed switch reports one connection loss after the old services and transport stop") {
            withFixture {
                connect()
                val oldRadio = radios.single()
                val oldServices = services.single()
                onLost = {
                    assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                    assertNull(manager.connectedDevice)
                    assertEquals(1, oldRadio.closes)
                    assertEquals(1, oldServices.teardowns)
                }
                createRadio = { TestRadio().also { it.connectFailure = LinkFailure.AuthenticationFailed() } }

                assertFailsWith<LinkFailure.AuthenticationFailed> { manager.connect(target()) }

                assertEquals(1, lossCount)
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
            }
        },
        nativeCase("a throwing loss observer cannot replace the original switch failure or run twice") {
            withFixture {
                connect()
                val observerFailure = IllegalStateException("loss observer failed")
                onLost = { throw observerFailure }
                createRadio = { TestRadio().also { it.connectFailure = LinkFailure.AuthenticationFailed() } }

                val failure = assertFailsWith<LinkFailure.AuthenticationFailed> { manager.connect(target()) }

                assertEquals(1, lossCount)
                assertTrue(failure.suppressedExceptions.any { it === observerFailure })
                assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                assertNull(manager.connectedDevice)
            }
        },
        nativeCase("a loss observer can connect a successor without stale switch cleanup disconnecting it") {
            withFixture {
                connect()
                val successor = target()
                onLost = {
                    assertEquals(DeviceConnectionState.DISCONNECTED, manager.connectionState)
                    createRadio = { TestRadio() }
                    manager.connect(successor)
                }
                createRadio = { TestRadio().also { it.connectFailure = LinkFailure.AuthenticationFailed() } }

                assertFailsWith<LinkFailure.AuthenticationFailed> { manager.connect(target()) }

                assertEquals(1, lossCount)
                assertEquals(DeviceConnectionState.READY, manager.connectionState)
                assertEquals(successor.deviceId, manager.connectedDevice?.id)
                assertEquals(1, radios.last().collectors)
            }
        },
    )
}
