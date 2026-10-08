// PortedFrom: MC1Services/Sources/MC1Services/Services/RemoteNodeService+PathRecovery.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import kotlinx.coroutines.CancellationException

// MARK: - Direct-path flood recovery

/**
 * CLI path recovery: run [operation], and on a direct-path mesh timeout reset the contact path to flood
 * and run once more. Already-flood contacts skip the reset. A failed reset rethrows the original timeout.
 * Binary admin waits (status / telemetry / owner) do not use this; they never reset the path.
 */
internal suspend fun <T> RemoteNodeService.performWithDirectPathFloodRecovery(
    radioId: RadioId,
    publicKey: Bytes,
    operationName: String,
    operation: suspend () -> T,
): T {
    val firstTimeout = try {
        return operation()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        if (!remoteMeshTimeout(error) || !isDirectRouted(radioId, publicKey)) throw error
        error
    }

    logger.info { "$operationName: direct-path timeout; resetting path to flood and retrying once" }
    try {
        resetContactPathToFlood(radioId, publicKey)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger.warning { "$operationName: path reset failed (${error.message}); not retrying" }
        throw firstTimeout
    }

    try {
        return operation()
    } catch (error: Exception) {
        if (error !is CancellationException && remoteMeshTimeout(error)) {
            logger.warning { "$operationName: flood retry also timed out" }
        }
        throw error
    }
}

/** True when the error is a mesh wait timeout (session or outer wrapper). */
internal fun remoteMeshTimeout(error: Throwable): Boolean = when (error) {
    is RemoteOperationTimeoutError -> true
    is RemoteNodeError.Timeout -> true
    is MeshCoreException.Timeout -> true
    is RemoteNodeError.SessionError -> error.error is MeshCoreException.Timeout
    else -> false
}

/** True when the local contact is not flood-routed. Missing contacts count as direct so `resetPath` can still clear a radio-side path. */
private suspend fun RemoteNodeService.isDirectRouted(radioId: RadioId, publicKey: Bytes): Boolean {
    val contact = try {
        dataStore.fetchContact(radioId, publicKey)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    } ?: return true
    return !contact.isFloodRouted
}

/** Clears the companion out-path via `resetPath` and mirrors flood on the local contact. */
private suspend fun RemoteNodeService.resetContactPathToFlood(radioId: RadioId, publicKey: Bytes) {
    session.resetPath(publicKey)
    val contact = dataStore.fetchContact(radioId, publicKey) ?: return
    val frame = contact.floodedContactFrame(RemoteCLICommandRewriter.epochSeconds32(clock.wallClock.instant()))
    try {
        dataStore.saveContact(radioId, frame)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // The radio path is already flood; a local save failure must not abort the retry.
        logger.warning { "Path reset on radio but failed to sync local contact: $error" }
    }
}
