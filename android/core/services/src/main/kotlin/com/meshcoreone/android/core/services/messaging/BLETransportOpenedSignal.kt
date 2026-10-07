// PortedFrom: MC1Services/Sources/MC1Services/Services/BLETransportOpenedSignal.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class BLETransportOpenedSignal {
    private val lock = Any()
    private var armed = false
    private var nextID = 0uL
    private val waiters = linkedMapOf<ULong, CompletableDeferred<Unit>>()
    private var terminal: CancellationException? = null
    val waiterCount: Int get() = synchronized(lock) { waiters.size }

    suspend fun wait() {
        currentCoroutineContext().ensureActive()
        val (id, waiter) = synchronized(lock) {
            terminal?.let { throw it }
            if (armed) { armed = false; return }
            val id = nextID++
            id to CompletableDeferred<Unit>().also { waiters[id] = it }
        }
        try { waiter.await() }
        finally {
            synchronized(lock) { waiters.remove(id) }
            waiter.cancel()
        }
    }

    fun fire() {
        synchronized(lock) {
            if (terminal != null) return
            if (waiters.isEmpty()) armed = true
            else {
                waiters.values.forEach { it.complete(Unit) }
                waiters.clear()
            }
        }
    }

    fun clear() { synchronized(lock) { armed = false } }

    fun finish() {
        synchronized(lock) {
            if (terminal != null) return
            val cause = CancellationException("Messaging generation ended")
            terminal = cause
            armed = false
            waiters.values.forEach { it.cancel(cause) }
            waiters.clear()
        }
    }
}
