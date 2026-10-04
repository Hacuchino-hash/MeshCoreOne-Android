// PortedFrom: MeshCore/Sources/MeshCore/Transport/WiFiTransport.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.transport.tcp

sealed interface WiFiTransportError {
    data class ConnectionFailed(val reason: String) : WiFiTransportError
    data object ConnectionTimeout : WiFiTransportError
    data object NotConnected : WiFiTransportError
    data class SendFailed(val reason: String) : WiFiTransportError
    data object SendTimeout : WiFiTransportError
    data object InvalidHost : WiFiTransportError
    data object InvalidPort : WiFiTransportError
    data object NotConfigured : WiFiTransportError
}
