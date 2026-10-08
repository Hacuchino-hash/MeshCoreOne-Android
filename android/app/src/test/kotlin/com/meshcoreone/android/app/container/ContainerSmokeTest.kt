// AndroidOnly: WP-303 Native assembly smoke test: the real graph connects over the protocol-speaking fake radio.
package com.meshcoreone.android.app.container

import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.data.repository.RoomPersistenceStore
import com.meshcoreone.android.core.database.MeshCoreDatabase
import com.meshcoreone.android.core.services.diagnostics.DebugLogBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
abstract class RoomProcessTest {
    internal lateinit var db: MeshCoreDatabase
    internal lateinit var store: RoomPersistenceStore
    private lateinit var owner: CoroutineScope

    @Before fun openStore() {
        db = openMemoryDatabase()
        owner = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        store = RoomPersistenceStore(db, owner)
    }

    @After fun closeStore() {
        try { kotlinx.coroutines.runBlocking { store.close() } } finally {
            DebugLogBuffer.shared = null
            owner.cancel()
            db.close()
        }
    }
}

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
