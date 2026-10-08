// AndroidOnly: WP-303 Native tests: the DataStore-backed process roles over the real merged storage module.
package com.meshcoreone.android.app.container

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.datastore.MeshCoreStorage
import com.meshcoreone.android.core.model.PersistenceKeys
import com.meshcoreone.android.core.model.RegionSelection
import com.meshcoreone.android.core.runtime.RuntimePreferenceValue
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DataStoreAdaptersTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val storage get() = MeshCoreStorage.get(context)

    @After fun close() = runBlocking { storage.close() }

    @Test fun connectionPreferencesRoundTripEveryValueKindAndRemoveOnDelete() = runBlocking<Unit> {
        val preferences = DataStoreConnectionPreferences(storage.preferences)
        val id = UUID.randomUUID().toString()
        val at = Instant.ofEpochMilli(1_704_067_200_123)
        val written = preferences.update {
            it[PersistenceKeys.LAST_CONNECTED_DEVICE_ID] = RuntimePreferenceValue.Text(id)
            it[PersistenceKeys.USER_EXPLICITLY_DISCONNECTED] = RuntimePreferenceValue.Flag(true)
            it[PersistenceKeys.LAST_BOND_VERIFIED_DATE] = RuntimePreferenceValue.Date(at)
        }
        assertEquals(id, written.text(PersistenceKeys.LAST_CONNECTED_DEVICE_ID))
        val read = preferences.read()
        assertEquals(id, read.text(PersistenceKeys.LAST_CONNECTED_DEVICE_ID))
        assertEquals(true, read.flag(PersistenceKeys.USER_EXPLICITLY_DISCONNECTED))
        assertEquals(at, read.date(PersistenceKeys.LAST_BOND_VERIFIED_DATE), "dates keep millisecond precision")
        preferences.update { it.remove(PersistenceKeys.USER_EXPLICITLY_DISCONNECTED); it.remove(PersistenceKeys.LAST_BOND_VERIFIED_DATE) }
        val after = preferences.read()
        assertNull(after.flag(PersistenceKeys.USER_EXPLICITLY_DISCONNECTED))
        assertNull(after.date(PersistenceKeys.LAST_BOND_VERIFIED_DATE))
        assertEquals(id, after.text(PersistenceKeys.LAST_CONNECTED_DEVICE_ID), "unrelated keys are untouched")
        preferences.update { it.remove(PersistenceKeys.LAST_CONNECTED_DEVICE_ID) }
    }

    @Test fun regionSelectionPersistsClearsAndDropsACorruptValue() = runBlocking<Unit> {
        val store = DataStoreRegionSelectionStore(storage.preferences)
        assertNull(store.load())
        val region = RegionSelection("US", RegionSelection.Source.LOCATION, "US-CA", "los angeles")
        store.persist(region)
        assertEquals(region, DataStoreRegionSelectionStore(storage.preferences).load())
        store.persist(null)
        assertNull(store.load())
        // A stored value that is not valid JSON is cleared and reported absent (the Swift loader removes the key).
        storage.preferences.set(com.meshcoreone.android.core.datastore.BackupPreferenceKeys.regionSelection,
            com.meshcoreone.android.core.protocol.bytes.Bytes.utf8("not json"))
        assertNull(store.load())
        assertNull(storage.preferences.snapshot().storedValues[com.meshcoreone.android.core.datastore.BackupPreferenceKeys.regionSelection.rawValue])
    }

    @Test fun staleCleanupPreferencesStoreThresholdAndReferenceDate() = runBlocking<Unit> {
        val preferences = DataStoreStaleCleanupPreferences(storage.preferences)
        assertEquals(0, preferences.thresholdDays())
        assertNull(preferences.lastRun())
        val at = Instant.ofEpochSecond(1_704_067_200, 500_000_000)
        preferences.recordRun(at)
        assertEquals(at, preferences.lastRun())
        assertEquals(725_760_000.5, storage.preferences.get(com.meshcoreone.android.core.datastore.AppStorageKey.lastStaleCleanupDate))
    }
}
