// AndroidOnly: WP-207 Source keeps .connecting across connect retries; only the terminal failure publishes disconnected.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RetryStateTest {
    @TestFactory
    fun retryStateCases() = listOf(
        nativeCase("failed attempts between retries never publish DISCONNECTED until the terminal failure") {
            withFixture {
                val subscription = manager.subscribeTransitions()
                val states = mutableListOf<DeviceConnectionState>()
                val collector = backgroundScope.launch { subscription.transitions.collect { states += it.state } }
                createRadio = { TestRadio().also { it.connectFailure = IllegalStateException("out of range") } }
                val work = backgroundScope.async { runCatching { manager.connect(platform.target) } }
                advanceUntilIdle()
                assertTrue(work.await().isFailure)
                assertEquals(4, radios.size, "All four BLE attempts must run")
                val firstConnecting = states.indexOf(DeviceConnectionState.CONNECTING)
                assertTrue(firstConnecting >= 0)
                val afterStart = states.drop(firstConnecting)
                assertEquals(DeviceConnectionState.DISCONNECTED, afterStart.last(), "The terminal failure ends DISCONNECTED")
                assertEquals(1, afterStart.count { it == DeviceConnectionState.DISCONNECTED },
                    "No DISCONNECTED flicker between retries: $afterStart")
                collector.cancel(); subscription.close()
            }
        },
    )
}
