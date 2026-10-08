// AndroidOnly: WP-302 Actual seeded Room/process-store lookup for original notification consumer assertions.
package com.meshcoreone.android.app.navigation.cases

import android.content.Context
import androidx.room.Room
import com.meshcoreone.android.app.navigation.PersistenceNavigationLookup
import com.meshcoreone.android.core.data.repository.RoomPersistenceStore
import com.meshcoreone.android.core.database.MeshCoreDatabase
import com.meshcoreone.android.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor

class NavigationPersistenceFixture(context: Context) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val executor = Executor { it.run() }
    private val database = Room.inMemoryDatabaseBuilder(context, MeshCoreDatabase::class.java)
        .allowMainThreadQueries().setQueryExecutor(executor).setTransactionExecutor(executor).build()
    private val store = RoomPersistenceStore(database, scope, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    private val lookup = PersistenceNavigationLookup(store)

    fun seeded(contact: ContactDTO?, channel: ChannelDTO?, room: RemoteNodeSessionDTO?): PersistenceNavigationLookup {
        runBlocking {
            contact?.let { store.saveContact(it) }
            channel?.let { store.saveChannel(it) }
            room?.let { store.saveRemoteNodeSessionDTO(it) }
        }
        return lookup
    }

    override fun close() {
        try {
            runBlocking { store.close() }
        } finally {
            try { database.close() } finally { scope.cancel() }
        }
    }
}
