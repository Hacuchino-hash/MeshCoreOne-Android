// AndroidOnly: WP-303 Process-owned connection preferences over the preference DataStore.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.datastore.PreferenceKey
import com.meshcoreone.android.core.datastore.PreferenceStore
import com.meshcoreone.android.core.datastore.PreferenceValue
import com.meshcoreone.android.core.model.PersistenceKeys
import com.meshcoreone.android.core.runtime.ProcessConnectionPreferences
import com.meshcoreone.android.core.runtime.RuntimePreferenceSnapshot
import com.meshcoreone.android.core.runtime.RuntimePreferenceValue
import java.time.Instant

/**
 * The runtime's last-connection preferences. Only the keys in [KEYS] are managed: text values map to strings,
 * the explicit-disconnect flag to a boolean and dates to epoch milliseconds (an Android-only encoding; the keys
 * are not part of the backup/restore contract). One `PreferenceStore.update` carries each edit, so the
 * read-modify-write the runtime performs is atomic.
 */
class DataStoreConnectionPreferences(private val store: PreferenceStore) : ProcessConnectionPreferences {
    override suspend fun read(): RuntimePreferenceSnapshot =
        RuntimePreferenceSnapshot(managed(store.snapshot().storedValues))

    override suspend fun update(
        transform: (MutableMap<String, RuntimePreferenceValue>) -> Unit,
    ): RuntimePreferenceSnapshot {
        var result: Map<String, RuntimePreferenceValue> = emptyMap()
        store.update {
            val current = managed(snapshot.storedValues).toMutableMap()
            transform(current)
            for (key in KEYS) {
                when (val next = current[key]) {
                    null -> remove(PreferenceKey.StringKey(key, null))
                    is RuntimePreferenceValue.Text -> this[PreferenceKey.StringKey(key, null)] = next.value
                    is RuntimePreferenceValue.Flag -> this[PreferenceKey.BooleanKey(key, null)] = next.value
                    is RuntimePreferenceValue.Date -> this[PreferenceKey.IntegerKey(key, 0L)] = next.value.toEpochMilli()
                }
            }
            result = current
        }
        return RuntimePreferenceSnapshot(result)
    }

    private fun managed(stored: Map<String, PreferenceValue>): Map<String, RuntimePreferenceValue> =
        KEYS.mapNotNull { key -> stored[key]?.let { key to decode(it) } }.toMap()

    private fun decode(value: PreferenceValue): RuntimePreferenceValue = when (value) {
        is PreferenceValue.StringValue -> RuntimePreferenceValue.Text(value.value)
        is PreferenceValue.BooleanValue -> RuntimePreferenceValue.Flag(value.value)
        is PreferenceValue.IntegerValue -> RuntimePreferenceValue.Date(Instant.ofEpochMilli(value.value))
        else -> throw IllegalStateException("Unsupported stored connection preference type")
    }

    companion object {
        val KEYS: List<String> = listOf(
            PersistenceKeys.LAST_CONNECTED_DEVICE_ID, PersistenceKeys.LAST_CONNECTED_DEVICE_NAME,
            PersistenceKeys.LAST_CONNECTED_RADIO_ID, PersistenceKeys.LAST_DISCONNECT_DIAGNOSTIC,
            PersistenceKeys.LAST_BOND_VERIFIED_DEVICE_ID, PersistenceKeys.LAST_BOND_VERIFIED_DATE,
            PersistenceKeys.USER_EXPLICITLY_DISCONNECTED,
        )
    }
}
