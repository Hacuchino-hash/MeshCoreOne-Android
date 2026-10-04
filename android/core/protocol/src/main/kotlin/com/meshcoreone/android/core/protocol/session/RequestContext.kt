// PortedFrom: MeshCore/Sources/MeshCore/Session/RequestContext.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RequestContext(val caller: CoroutineContext) {
    private val sendLock = Any()
    private var currentSend: Deferred<Unit>? = null
    private var currentSendIsCleanup = false
    val entered = AtomicBoolean(false)
    val mayHaveWritten = AtomicBoolean(false)
    fun beforeSend(cleanup: Boolean = false) {
        if (!cleanup) caller.ensureActive()
        mayHaveWritten.set(true)
    }
    fun attachSend(send: Deferred<Unit>, cleanup: Boolean) = synchronized(sendLock) {
        currentSend = send
        currentSendIsCleanup = cleanup
        if (!cleanup && !caller.isActive) send.cancel()
    }
    fun detachSend(send: Deferred<Unit>) = synchronized(sendLock) {
        if (currentSend === send) currentSend = null
    }
    fun cancelInProgressSend() = synchronized(sendLock) { if (!currentSendIsCleanup) currentSend?.cancel(); Unit }
}

class RequestResponseSerializer(
    private val scope: CoroutineScope,
    private val onOrphanFailure: (Throwable) -> Unit = {
        java.util.logging.Logger.getLogger("MeshCore.Session").warning("session.orphanedExchangeFailed ${it.javaClass.name}")
    },
) {
    private val mutex = Mutex()

    suspend fun acquire() = mutex.lock()
    fun release() = mutex.unlock()

    suspend fun <T> withSerialization(operation: suspend () -> T): T = withOwnedSerialization {
        mayHaveWritten.set(true)
        operation()
    }

    internal suspend fun <T> withOwnedSerialization(operation: suspend RequestContext.() -> T): T {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val context = RequestContext(caller)
        val task = scope.async {
            mutex.withLock {
                caller.ensureActive()
                context.entered.set(true)
                context.operation()
            }
        }
        task.invokeOnCompletion { cause ->
            if (!caller.isActive && cause != null && cause !is CancellationException) onOrphanFailure(cause)
        }
        try {
            return task.await()
        } catch (cancelled: CancellationException) {
            // A written request still owns its reply; cancellation never hands that reply to the next caller.
            if (!context.mayHaveWritten.get()) task.cancel()
            else context.cancelInProgressSend()
            throw cancelled
        }
    }
}
