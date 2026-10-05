// AndroidOnly: WP-207 A losing runtime cannot disconnect another runtime's physical transport.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.protocol.transport.MeshTransport
import java.lang.ref.WeakReference
import java.util.UUID

internal object PhysicalOwnership {
    private data class Claim(val transport: WeakReference<MeshTransport>, val owner: UUID)
    private val claims = mutableListOf<Claim>()

    @Synchronized
    fun acquire(transport: MeshTransport, owner: UUID) {
        claims.removeAll { it.transport.get() == null }
        val claim = claims.firstOrNull { it.transport.get() === transport }
        if (claim != null && claim.owner != owner) throw ConnectionError.ForeignPhysicalOwner()
        if (claim == null) claims += Claim(WeakReference(transport), owner)
    }

    @Synchronized
    fun release(transport: MeshTransport, owner: UUID) {
        claims.removeAll { it.transport.get() === transport && it.owner == owner }
    }
}
