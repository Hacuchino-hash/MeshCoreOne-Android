// PortedFrom: MC1Services/Sources/MC1Services/Services/ContactService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.protocol.config.MeshCoreException

/**
 * Errors surfaced by [ContactService]. Mirrors the Swift `ContactServiceError` enum; each case is a
 * distinct exception class so a fresh stack trace is captured per throw, and [errorDescription]
 * carries the Swift `LocalizedError.errorDescription` text verbatim.
 */
sealed class ContactServiceError(
    val errorDescription: String,
    cause: Throwable? = null,
) : Exception(errorDescription, cause) {
    class NotConnected : ContactServiceError("Not connected to radio")
    class SendFailed : ContactServiceError("Failed to send message")
    class InvalidResponse : ContactServiceError("Invalid response from device")
    class SyncInterrupted : ContactServiceError("Sync was interrupted")
    class ContactNotFound : ContactServiceError("Contact not found on device")
    class ContactTableFull : ContactServiceError("Device node list is full")
    class ShareContactUnavailable :
        ContactServiceError("Unable to share node. The node's advertisement may be missing or too old.")

    /** Wraps a session-level failure; the description is the session error's own message. */
    class SessionError(val error: MeshCoreException) : ContactServiceError(error.message.orEmpty(), error)

    override fun toString(): String = when (this) {
        is SessionError -> "ContactServiceError.sessionError(${error.javaClass.simpleName})"
        else -> "ContactServiceError.${javaClass.simpleName.replaceFirstChar(Char::lowercaseChar)}"
    }
}

/** Reason for contact cleanup (deletion or blocking). */
enum class ContactCleanupReason {
    DELETED,
    BLOCKED,
    UNBLOCKED,
}
