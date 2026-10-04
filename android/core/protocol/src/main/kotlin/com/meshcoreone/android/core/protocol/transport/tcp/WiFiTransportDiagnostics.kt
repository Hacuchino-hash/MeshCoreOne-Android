// AndroidOnly: WP-108 Native typed exception diagnostics, not app prose or localized recovery UI.
package com.meshcoreone.android.core.protocol.transport.tcp

import java.io.IOException

class WiFiTransportException(val error: WiFiTransportError, cause: Throwable? = null) :
    IOException(error.diagnosticMessage(), cause)

class WiFiReceiveException(cause: IOException) :
    IOException("wifi.receive_failed: ${cause.message ?: cause.javaClass.simpleName}", cause)

private fun WiFiTransportError.diagnosticMessage(): String = when (this) {
    is WiFiTransportError.ConnectionFailed -> "wifi.connection_failed: $reason"
    WiFiTransportError.ConnectionTimeout -> "wifi.connection_timeout"
    WiFiTransportError.NotConnected -> "wifi.not_connected"
    is WiFiTransportError.SendFailed -> "wifi.send_failed: $reason"
    WiFiTransportError.SendTimeout -> "wifi.send_timeout"
    WiFiTransportError.InvalidHost -> "wifi.invalid_host"
    WiFiTransportError.InvalidPort -> "wifi.invalid_port"
    WiFiTransportError.NotConfigured -> "wifi.not_configured"
}
