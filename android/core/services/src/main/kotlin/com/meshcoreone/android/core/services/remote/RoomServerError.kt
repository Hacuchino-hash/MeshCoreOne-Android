// PortedFrom: MC1Services/Sources/MC1Services/Services/RoomServerService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.config.MeshCoreException

/**
 * Room-server failures. `message` carries the Swift `errorDescription` text verbatim; [SessionError]
 * passes the wrapped mesh error's description through, like Swift's `e.localizedDescription`.
 * Each case is a class (not an object) so every throw gets its own stack trace.
 */
sealed class RoomServerError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : RoomServerError("Not connected to device.")
    class SessionNotFound : RoomServerError("Room session not found.")
    class SendFailed(val reason: String) : RoomServerError("Send failed: $reason")
    class PermissionDenied : RoomServerError("Permission denied.")
    class InvalidResponse : RoomServerError("Invalid response from device.")
    class SessionError(val error: MeshCoreException) : RoomServerError(error.message ?: error.toString(), error)

    val errorDescription: String get() = message ?: ""

    override fun toString(): String = when (this) {
        is SendFailed -> "RoomServerError.sendFailed(\"$reason\")"
        is SessionError -> "RoomServerError.sessionError(${error.javaClass.simpleName})"
        else -> "RoomServerError.${javaClass.simpleName.replaceFirstChar(Char::lowercaseChar)}"
    }
}
