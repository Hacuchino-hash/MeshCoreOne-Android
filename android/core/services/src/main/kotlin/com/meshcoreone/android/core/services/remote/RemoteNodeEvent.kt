// PortedFrom: MC1Services/Sources/MC1Services/Services/RemoteNodeEvent.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey

/**
 * Remote-node session notifications broadcast by [RemoteNodeService.events].
 * The stream is multicast: every subscriber receives every event.
 */
sealed interface RemoteNodeEvent {
    /**
     * A remote-node session's connection state changed (login, logout, keep-alive failure, or link loss).
     * Swift keys the event by the session UUID; the Android store partitions sessions by radio, so the
     * full [EntityKey] is carried and [EntityKey.id] is the Swift `sessionID`.
     */
    data class SessionStateChanged(val session: EntityKey, val isConnected: Boolean) : RemoteNodeEvent
}
