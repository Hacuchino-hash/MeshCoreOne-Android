// PortedFrom: MeshCore/Sources/MeshCore/Session/MeshCoreSession+Signing.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MeshEvent

internal suspend fun ExchangeOwner.signingStart(): Long =
    query(PacketBuilder.signStart(), "signStart") { (it as? MeshEvent.SignStart)?.maxLength }

internal suspend fun ExchangeOwner.signingFinish(timeout: Double?): Bytes =
    query(PacketBuilder.signFinish(), "signature", timeout ?: core.configuration.defaultTimeout * 3) {
        val signature = (it as? MeshEvent.Signature)?.signature
        if (signature != null && signature.size != 64) {
            throw MeshCoreException.InvalidResponse("64-byte Ed25519 signature", "${signature.size} bytes")
        }
        signature
    }

internal suspend fun SessionCore.sign(data: Bytes, chunkSize: Long, timeout: Double?): Bytes {
    if (chunkSize <= 0) throw MeshCoreException.InvalidInput("Signing chunk size must be positive")
    timeout?.let { timeoutDuration(it) }
    return exchange {
        val maximum = signingStart()
        if (data.size.toLong() > maximum) throw MeshCoreException.DataTooLarge(maximum, data.size.toLong())
        var offset = 0
        while (offset < data.size) {
            val length = minOf(chunkSize, (data.size - offset).toLong()).toInt()
            simple(PacketBuilder.signData(data.slice(offset, offset + length)))
            offset += length
        }
        signingFinish(timeout)
    }
}
