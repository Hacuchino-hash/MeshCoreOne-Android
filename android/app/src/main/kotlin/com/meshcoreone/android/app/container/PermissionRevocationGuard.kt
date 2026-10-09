// AndroidOnly: WP-303 Foreground permission snapshot: a revoked grant tears down only the transport that needs it.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.connectivity.permissions.ConnectivityPermission
import com.meshcoreone.android.core.connectivity.permissions.ConnectivityPermissionPolicy
import com.meshcoreone.android.core.connectivity.permissions.PermissionSnapshot

/** The transports the guard can tear down, and which one is live. */
interface TransportTeardown {
    val liveTransport: LiveTransport
    suspend fun disconnectTransport()
}

enum class LiveTransport { NONE, BLUETOOTH, LAN }

/**
 * Compares each foreground permission snapshot with the previous one. A revoked `BLUETOOTH_CONNECT` closes a live
 * Bluetooth transport, and a revoked `ACCESS_LOCAL_NETWORK` closes a live LAN transport; a revocation that does not
 * concern the live transport (or an unrelated permission, such as notifications) leaves the connection alone. The first
 * snapshot only becomes the baseline.
 */
class PermissionRevocationGuard(
    private val read: () -> PermissionSnapshot,
    private val teardown: TransportTeardown,
    private val onRevoked: (Set<ConnectivityPermission>) -> Unit = {},
) {
    private val lock = Any()
    private var previous: PermissionSnapshot? = null

    /** Returns the set of permissions revoked since the last check; closes the affected transport. */
    suspend fun check(): Set<ConnectivityPermission> {
        val current = read()
        val before = synchronized(lock) { previous.also { previous = current } } ?: return emptySet()
        val revoked = ConnectivityPermissionPolicy.revoked(before, current)
        if (revoked.isEmpty()) return revoked
        onRevoked(revoked)
        val affected = when (teardown.liveTransport) {
            LiveTransport.BLUETOOTH -> ConnectivityPermission.BLUETOOTH_CONNECT in revoked
            LiveTransport.LAN -> ConnectivityPermission.ACCESS_LOCAL_NETWORK in revoked
            LiveTransport.NONE -> false
        }
        if (affected) teardown.disconnectTransport()
        return revoked
    }
}
