// PortedFrom: MC1/State/PendingExternalURL.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.deeplinks

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PendingExternalRoute(
    private val route: suspend (String) -> DeepLinkRouteOutcome,
) {
    data class Delivery(val id: String, val uri: String)

    sealed interface SubmitOutcome {
        data object Staged : SubmitOutcome
        data object Duplicate : SubmitOutcome
        data class Routed(val outcome: DeepLinkRouteOutcome) : SubmitOutcome
    }

    private val mutex = Mutex()
    private val consumed = LinkedHashSet<String>()
    private var ready = false
    private var pending: Delivery? = null
    private var inFlight: String? = null

    suspend fun submit(delivery: Delivery): SubmitOutcome {
        mutex.withLock {
            if (delivery.id in consumed || delivery.id == inFlight) return SubmitOutcome.Duplicate
            pending = delivery
            if (!ready) return SubmitOutcome.Staged
        }
        return drain()
    }

    suspend fun markReady(): SubmitOutcome {
        mutex.withLock { ready = true }
        return drain()
    }

    suspend fun retry(): SubmitOutcome = drain()

    suspend fun snapshot(): Delivery? = mutex.withLock { pending }

    private suspend fun drain(): SubmitOutcome {
        val delivery = mutex.withLock {
            val next = pending ?: return SubmitOutcome.Staged
            if (!ready || inFlight != null) return SubmitOutcome.Staged
            inFlight = next.id
            next
        }
        return try {
            val outcome = route(delivery.uri)
            mutex.withLock {
                if (pending?.id == delivery.id) pending = null
                inFlight = null
                consumed += delivery.id
                while (consumed.size > MAX_CONSUMED_IDS) consumed.remove(consumed.first())
            }
            SubmitOutcome.Routed(outcome)
        } catch (failure: Throwable) {
            mutex.withLock { if (inFlight == delivery.id) inFlight = null }
            throw failure
        }
    }

    private companion object {
        const val MAX_CONSUMED_IDS = 64
    }
}
