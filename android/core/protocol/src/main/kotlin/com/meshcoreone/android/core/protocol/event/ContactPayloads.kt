// PortedFrom: MeshCore/Sources/MeshCore/Events/ContactPayloads.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

import com.meshcoreone.android.core.protocol.bytes.Bytes

data class DiscoverResponse(
    val nodeType: UByte,
    val snrIn: Double,
    val snr: Double,
    val rssi: Long,
    val pathLength: UByte,
    val tag: Bytes,
    val publicKey: Bytes,
) {
    private val fields get() = arrayOf(nodeType, snrIn, snr, rssi, pathLength, tag, publicKey)
    override fun equals(other: Any?): Boolean = other is DiscoverResponse && eventFieldsEqual(fields, other.fields)
    override fun hashCode(): Int = eventFieldsHash(fields)
}

data class AdvertPathResponse(val recvTimestamp: UInt, val pathLength: UByte, val path: Bytes)
