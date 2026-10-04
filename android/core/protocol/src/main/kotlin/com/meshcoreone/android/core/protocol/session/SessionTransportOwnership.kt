// AndroidOnly: WP-107 Exclusive physical-transport admission and retained-link uncertainty across session instances.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.transport.MeshTransport
import java.lang.ref.WeakReference

internal object SessionTransportOwnership {
    private class Claim(
        val transport: WeakReference<MeshTransport>,
        var owner: WeakReference<SessionCore>,
        var generation: Long,
        var active: Boolean,
        var retained: Boolean,
    )
    private val claims = mutableListOf<Claim>()

    @Synchronized
    fun acquire(transport: MeshTransport, owner: SessionCore, generation: Long, disconnected: Boolean) {
        claims.removeAll { it.transport.get() == null }
        val existing = claims.firstOrNull { it.transport.get() === transport }
        if (existing != null) {
            if (existing.active && (existing.owner.get() !== owner || existing.generation != generation)) {
                throw MeshCoreException.ConnectionLost(SessionCorrelationException.ConcurrentTransportOwner())
            }
            if (existing.retained && !disconnected) throw MeshCoreException.ConnectionLost(SessionCorrelationException.RetainedTransport())
            existing.owner = WeakReference(owner)
            existing.generation = generation
            existing.active = true
            existing.retained = false
        } else {
            claims += Claim(WeakReference(transport), WeakReference(owner), generation, active = true, retained = false)
        }
    }

    @Synchronized
    fun reserveClose(transport: MeshTransport, owner: SessionCore, generation: Long, disconnected: Boolean) {
        val claim = claims.firstOrNull { it.transport.get() === transport }
        if (claim == null) {
            if (!disconnected) throw MeshCoreException.ConnectionLost(SessionCorrelationException.RetainedTransport())
            claims += Claim(WeakReference(transport), WeakReference(owner), generation, active = true, retained = false)
        } else {
            if (claim.owner.get() !== owner || claim.generation != generation) {
                throw MeshCoreException.ConnectionLost(SessionCorrelationException.ConcurrentTransportOwner())
            }
            claim.active = true
        }
    }

    @Synchronized
    fun release(transport: MeshTransport, owner: SessionCore, generation: Long, retained: Boolean) {
        val claim = claims.firstOrNull { it.transport.get() === transport } ?: return
        if (claim.owner.get() !== owner || claim.generation != generation) return
        claim.active = false
        claim.retained = retained
        if (!retained) claims.remove(claim)
    }
}
