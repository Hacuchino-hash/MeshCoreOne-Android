// PortedFrom: MC1Services/Sources/MC1Services/Services/BinaryProtocolService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.config.MeshCoreException

/**
 * Binary-protocol failures. `message` carries the Swift `errorDescription` text verbatim; [SessionError]
 * passes the wrapped mesh error's description through, like Swift's `e.localizedDescription`.
 * Each case is a class (not an object) so every throw gets its own stack trace.
 */
sealed class BinaryProtocolError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : BinaryProtocolError("Not connected to device.")
    class SendFailed : BinaryProtocolError("Failed to send request.")
    class Timeout : BinaryProtocolError("Request timed out.")
    class InvalidResponse : BinaryProtocolError("Invalid response from device.")
    class SessionError(val error: MeshCoreException) : BinaryProtocolError(error.message ?: error.toString(), error)

    val errorDescription: String get() = message ?: ""

    override fun toString(): String = when (this) {
        is SessionError -> "BinaryProtocolError.sessionError(${error.javaClass.simpleName})"
        else -> "BinaryProtocolError.${javaClass.simpleName.replaceFirstChar(Char::lowercaseChar)}"
    }
}
