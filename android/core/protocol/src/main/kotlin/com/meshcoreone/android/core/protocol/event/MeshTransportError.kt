// PortedFrom: MeshCore/Sources/MeshCore/Events/MeshTransportError.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

sealed class MeshTransportError(message: String) : Exception(message) {
    data object NotConnected : MeshTransportError("Transport is not connected")
    data class ConnectionFailed(val reason: String) : MeshTransportError("Connection failed: $reason")
    data class SendFailed(val reason: String) : MeshTransportError("Send failed: $reason")
    data object DeviceNotFound : MeshTransportError("Device was not found")
    data object ServiceNotFound : MeshTransportError("Required device service was not found")
    data object CharacteristicNotFound : MeshTransportError("Required device characteristic was not found")
}
