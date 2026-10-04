// PortedFrom: MeshCore/Sources/MeshCore/Events/DevicePayloads.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.model

data class AutoAddConfig(val bitmask: UByte, val maxHops: UByte = 0u) {
    companion object {
        val OVERWRITE_OLDEST_BIT: UByte = 0x01u
        val CONTACTS_BIT: UByte = 0x02u
        val REPEATERS_BIT: UByte = 0x04u
        val ROOM_SERVERS_BIT: UByte = 0x08u
        val SENSORS_BIT: UByte = 0x10u
    }
}
