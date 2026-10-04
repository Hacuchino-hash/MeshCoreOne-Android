// AndroidOnly: WP-107 Typed correlation uncertainty and observable native background/restore diagnostics.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant
import java.util.logging.Logger

sealed class SessionCorrelationException(message: String) : Exception(message) {
    class UnresolvedReply(val generation: Long, val responseFamily: String) :
        SessionCorrelationException("session.unresolvedReply generation=$generation family=$responseFamily")
    class ReusedTag(val generation: Long, val tag: Bytes) :
        SessionCorrelationException("session.reusedTag generation=$generation")
    class RetainedTransport :
        SessionCorrelationException("session.retainedTransport requires a fresh transport connection before reuse")
    class ConcurrentTransportOwner :
        SessionCorrelationException("session.concurrentTransportOwner receive and send ownership is exclusive")
}

data class ContactStreamProgress(
    val generation: Long,
    val reportedTotal: Long?,
    val receivedCount: Long,
    val lastModified: Instant?,
    val completed: Boolean,
)

sealed interface SessionDiagnostic {
    data class ParseFailure(val generation: Long, val size: Int, val reason: String) : SessionDiagnostic
    data class BackgroundFailure(val generation: Long, val operation: String, val cause: Throwable) : SessionDiagnostic
    data class CorrelationUncertain(val generation: Long, val responseFamily: String) : SessionDiagnostic
    data class RestoreFailure(val generation: Long, val cause: Throwable) : SessionDiagnostic
    data class StreamEnded(val generation: Long, val cause: Throwable?) : SessionDiagnostic
}

internal fun logSessionDiagnostic(diagnostic: SessionDiagnostic) {
    val message = when (diagnostic) {
        is SessionDiagnostic.ParseFailure -> "session.parseFailure generation=${diagnostic.generation} size=${diagnostic.size} reason=${diagnostic.reason}"
        is SessionDiagnostic.BackgroundFailure -> "session.backgroundFailure generation=${diagnostic.generation} operation=${diagnostic.operation} cause=${diagnostic.cause.javaClass.name}"
        is SessionDiagnostic.CorrelationUncertain -> "session.correlationUncertain generation=${diagnostic.generation} family=${diagnostic.responseFamily.substringBefore(':')}"
        is SessionDiagnostic.RestoreFailure -> "session.restoreFailure generation=${diagnostic.generation} cause=${diagnostic.cause.javaClass.name}"
        is SessionDiagnostic.StreamEnded -> "session.streamEnded generation=${diagnostic.generation} cause=${diagnostic.cause?.javaClass?.name ?: "eof"}"
    }
    Logger.getLogger("MeshCore.Session").warning(message)
}
