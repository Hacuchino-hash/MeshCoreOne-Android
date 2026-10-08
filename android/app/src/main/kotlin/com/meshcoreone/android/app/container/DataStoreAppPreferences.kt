// AndroidOnly: WP-303 DataStore-backed region selection and stale-node cleanup preferences for app state.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.app.state.RegionSelectionJson
import com.meshcoreone.android.app.state.RegionSelectionStore
import com.meshcoreone.android.app.state.StaleCleanupPreferences
import com.meshcoreone.android.core.datastore.AppStorageKey
import com.meshcoreone.android.core.datastore.BackupPreferenceKeys
import com.meshcoreone.android.core.datastore.PreferenceStore
import com.meshcoreone.android.core.datastore.PreferenceValue
import com.meshcoreone.android.core.model.RegionSelection
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant
import kotlinx.coroutines.launch

/** Region selection under the backup-contract key; an undecodable stored value is cleared and reported as absent. */
class DataStoreRegionSelectionStore(private val store: PreferenceStore) : RegionSelectionStore {
    override suspend fun load(): RegionSelection? {
        val stored = store.snapshot().storedValues[BackupPreferenceKeys.regionSelection.rawValue] ?: return null
        val bytes = (stored as? PreferenceValue.BinaryValue)?.value
        val decoded = bytes?.let { RegionSelectionJson.decode(String(it.toByteArray(), Charsets.UTF_8)) }
        if (decoded == null) store.remove(BackupPreferenceKeys.regionSelection)
        return decoded
    }

    override suspend fun persist(selection: RegionSelection?) {
        if (selection == null) store.remove(BackupPreferenceKeys.regionSelection)
        else store.set(BackupPreferenceKeys.regionSelection, Bytes.utf8(RegionSelectionJson.encode(selection)))
    }
}

/**
 * `autoDeleteStaleNodesDays` and `lastStaleCleanupDate`. The last-run date is stored as seconds since the Apple
 * reference date (2001-01-01T00:00:00Z) because the key is part of the backup contract; 0 means "never".
 */
class DataStoreStaleCleanupPreferences(private val store: PreferenceStore) : StaleCleanupPreferences {
    override suspend fun thresholdDays(): Int = store.get(AppStorageKey.autoDeleteStaleNodesDays).toInt()

    override suspend fun lastRun(): Instant? {
        val seconds = store.get(AppStorageKey.lastStaleCleanupDate)
        return if (seconds > 0) fromReferenceSeconds(seconds) else null
    }

    override suspend fun recordRun(at: Instant) {
        store.set(AppStorageKey.lastStaleCleanupDate, toReferenceSeconds(at))
    }

    companion object {
        /** Seconds between 1970-01-01 and the Apple reference date 2001-01-01 (Foundation `timeIntervalSinceReferenceDate`). */
        const val REFERENCE_DATE_EPOCH_SECONDS: Long = 978_307_200

        fun toReferenceSeconds(instant: Instant): Double =
            (instant.epochSecond - REFERENCE_DATE_EPOCH_SECONDS).toDouble() + instant.nano / 1_000_000_000.0

        fun fromReferenceSeconds(seconds: Double): Instant {
            val whole = kotlin.math.floor(seconds).toLong()
            val nanos = ((seconds - whole) * 1_000_000_000.0).toLong()
            return Instant.ofEpochSecond(whole + REFERENCE_DATE_EPOCH_SECONDS, nanos)
        }
    }
}

/**
 * The synchronous boolean port demo mode reads and writes. Reads are served from a snapshot loaded before install;
 * each write updates the snapshot at once and persists in order on [scope], so the in-memory value is never stale.
 */
class DataStoreDemoModeDefaults private constructor(
    private val store: PreferenceStore,
    private val scope: kotlinx.coroutines.CoroutineScope,
    initial: Map<String, Boolean>,
    private val onFailure: (Throwable) -> Unit,
) : com.meshcoreone.android.core.services.simulator.DemoModeDefaults {
    private val lock = Any()
    private var values: Map<String, Boolean> = initial

    // One consumer drains the queue, so writes persist in the order they were made (concurrent launches would not).
    private val writes = kotlinx.coroutines.channels.Channel<Pair<com.meshcoreone.android.core.datastore.PreferenceKey.BooleanKey, Boolean>>(
        kotlinx.coroutines.channels.Channel.UNLIMITED,
    )

    init {
        scope.launch {
            for ((key, value) in writes) {
                try {
                    store.set(key, value)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onFailure(failure)
                }
            }
        }
    }

    override fun bool(forKey: String): Boolean = synchronized(lock) { values[forKey] ?: false }

    override fun set(value: Boolean, forKey: String) {
        val key = KEYS[forKey] ?: throw IllegalArgumentException("Unknown demo-mode key: $forKey")
        synchronized(lock) {
            values = values + (forKey to value)
            writes.trySend(key to value)
        }
    }

    companion object {
        private val KEYS = mapOf(
            com.meshcoreone.android.core.services.simulator.DemoModeManager.IS_DEMO_MODE_UNLOCKED_KEY to AppStorageKey.isDemoModeUnlocked,
            com.meshcoreone.android.core.services.simulator.DemoModeManager.IS_DEMO_MODE_ENABLED_KEY to AppStorageKey.isDemoModeEnabled,
        )

        suspend fun load(
            store: PreferenceStore,
            scope: kotlinx.coroutines.CoroutineScope,
            onFailure: (Throwable) -> Unit = {},
        ): DataStoreDemoModeDefaults =
            DataStoreDemoModeDefaults(store, scope, KEYS.mapValues { (_, key) -> store.get(key) }, onFailure)
    }
}
