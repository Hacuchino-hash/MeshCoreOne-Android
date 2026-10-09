// AndroidOnly: WP-303 Native assembly smoke test: the real graph connects over the protocol-speaking fake radio.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContainerSmokeTest : RoomProcessTest() {
    @Test
    fun realGraphReachesReadyOverTheFakeRadio() = runTest {
        val h = ContainerHarness(this, store)
        try {
            h.manager.connect(h.target())
            h.settle()
            assertEquals(DeviceConnectionState.READY, h.manager.connectionState)
            assertNotNull(h.sessions.current)
        } finally {
            h.container.close()
        }
    }
}
