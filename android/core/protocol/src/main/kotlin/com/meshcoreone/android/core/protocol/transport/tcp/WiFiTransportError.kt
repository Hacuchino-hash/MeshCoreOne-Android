// PortedFrom: MeshCore/Sources/MeshCore/Transport/WiFiTransport.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Errors/WiFiTransportError+LocalizedError.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.transport.tcp

import java.io.IOException

sealed interface WiFiTransportError {
    data class ConnectionFailed(val reason: String) : WiFiTransportError
    data object ConnectionTimeout : WiFiTransportError
    data object NotConnected : WiFiTransportError
    data class SendFailed(val reason: String) : WiFiTransportError
    data object SendTimeout : WiFiTransportError
    data object InvalidHost : WiFiTransportError
    data object InvalidPort : WiFiTransportError
    data object NotConfigured : WiFiTransportError

    val description: String
        get() = when (this) {
            is ConnectionFailed -> "Connection failed: $reason"
            ConnectionTimeout -> "Connection timed out. Check the hostname or IP address and ensure the device is reachable."
            NotConnected -> "Not connected to device."
            is SendFailed -> "Failed to send data: $reason"
            SendTimeout -> "Send operation timed out."
            InvalidHost -> "Invalid hostname or IP address."
            InvalidPort -> "Invalid port number."
            NotConfigured -> "Connection not configured."
        }
}

class WiFiTransportException(val error: WiFiTransportError, cause: Throwable? = null) :
    IOException(error.description, cause)

class WiFiReceiveException(cause: IOException) :
    IOException("TCP receive failed: ${cause.message ?: cause.javaClass.simpleName}", cause)
