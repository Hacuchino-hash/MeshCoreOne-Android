// AndroidOnly: WP-207 An explicit disconnect's intent is durable before teardown finishes, as the source persists it first.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectIntentTest {
    @TestFactory
    fun intentCases() = listOf(
        nativeCase("explicit disconnect persists the user intent while teardown is still in progress") {
            withFixture {
                connect()
                val closeGate = CompletableDeferred<Unit>()
                radios.single().beforeClose = { closeGate.await() }
                val work = backgroundScope.async { manager.disconnect() }
                runCurrent()
                val duringTeardown = try {
                    assertFalse(work.isCompleted, "Teardown must still be blocked on the radio close")
                    last.restoredIntent()
                } finally {
                    closeGate.complete(Unit); runCurrent()
                }
                // A process death during teardown must not auto-reconnect on next launch.
                assertEquals(ConnectionIntent.UserDisconnected, duringTeardown)
                work.await()
                assertEquals(ConnectionIntent.UserDisconnected, last.restoredIntent())
                assertEquals(1, radios.single().closes)
            }
        },
        nativeCase("a non-clearing disconnect leaves the persisted intent wanting a connection") {
            withFixture {
                connect()
                manager.disconnect(RuntimeDisconnectReason.WIFI_RECONNECT_PREP)
                runCurrent()
                assertNotEquals(ConnectionIntent.UserDisconnected, last.restoredIntent())
            }
        },
    )
}
