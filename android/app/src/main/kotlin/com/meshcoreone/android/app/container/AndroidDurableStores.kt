// AndroidOnly: WP-303 Process-death-surviving association records and scan-fallback endpoints (WP-206 C-07).
package com.meshcoreone.android.app.container

import android.content.Context
import com.meshcoreone.android.core.connectivity.pairing.AssociationRecordStore
import com.meshcoreone.android.core.connectivity.pairing.BluetoothEndpoint
import com.meshcoreone.android.core.connectivity.pairing.KnownEndpointStore
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

/** Device id to advertised name, kept in a private preference file so Settings removals made while dead are still diffed. */
class SharedPreferencesAssociationRecords(context: Context) : AssociationRecordStore {
    private val preferences = context.applicationContext.getSharedPreferences("mc1.association-records", Context.MODE_PRIVATE)
    private val lock = Any()

    override fun records(): Map<UUID, String> = synchronized(lock) {
        buildMap {
            for ((key, value) in preferences.all) {
                val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                (value as? String)?.let { put(id, it) }
            }
        }
    }

    override fun remember(deviceId: UUID, name: String) = synchronized(lock) { preferences.edit().putString(deviceId.toString(), name).apply() }
    override fun forget(deviceId: UUID) = synchronized(lock) { preferences.edit().remove(deviceId.toString()).apply() }
}

/** Scan-fallback endpoints (`address` and optional association id) so a picked radio is reachable after a restart. */
class SharedPreferencesKnownEndpoints(context: Context) : KnownEndpointStore {
    private val preferences = context.applicationContext.getSharedPreferences("mc1.known-endpoints", Context.MODE_PRIVATE)
    private val lock = Any()

    override suspend fun endpoint(deviceId: UUID): BluetoothEndpoint? = synchronized(lock) {
        val text = preferences.getString(deviceId.toString(), null) ?: return null
        try {
            val json = JSONObject(text)
            BluetoothEndpoint(deviceId, json.getString("address"), if (json.has("association")) json.getInt("association") else null)
        } catch (malformed: JSONException) {
            null
        } catch (invalid: IllegalArgumentException) {
            null
        }
    }

    override suspend fun remember(endpoint: BluetoothEndpoint) = synchronized(lock) {
        val json = JSONObject().put("address", endpoint.address)
        endpoint.associationId?.let { json.put("association", it) }
        preferences.edit().putString(endpoint.deviceId.toString(), json.toString()).apply()
    }

    override suspend fun forget(deviceId: UUID) = synchronized(lock) { preferences.edit().remove(deviceId.toString()).apply() }
}
