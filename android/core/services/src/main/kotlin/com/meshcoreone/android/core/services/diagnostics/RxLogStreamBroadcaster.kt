// AndroidOnly: WP-212 Multicast stream for RX log entries and region updates; core:services cannot depend on core:runtime's EventBroadcaster.
package com.meshcoreone.android.core.services.diagnostics

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Unbounded multicast with the source `EventBroadcaster` contract the RX log service relies on:
 * registration happens synchronously inside [subscribe], so values yielded after it returns are
 * never dropped; every live subscriber receives every value; [finish] delivers buffered values
 * and then completes every subscriber, and later subscribers complete immediately.
 */
internal class RxLogStreamBroadcaster<T> {
    private val lock = Any()
    private val subscribers = LinkedHashMap<Long, Channel<T>>()
    private var nextId = 0L
    private var finished = false

    val subscriberCount: Int get() = synchronized(lock) { subscribers.size }

    fun subscribe(): Flow<T> {
        val channel = Channel<T>(Channel.UNLIMITED)
        val id = synchronized(lock) {
            if (finished) {
                channel.close()
                null
            } else {
                val id = nextId++
                subscribers[id] = channel
                id
            }
        }
        val collected = AtomicBoolean()
        return flow {
            check(collected.compareAndSet(false, true)) { "An RX log stream has exactly one consumer" }
            try {
                for (value in channel) emit(value)
            } finally {
                if (id != null) synchronized(lock) { subscribers.remove(id) }
                channel.cancel()
            }
        }
    }

    fun yield(value: T) {
        synchronized(lock) {
            val stale = subscribers.filterValues { channel -> channel.trySend(value).isClosed }.keys
            stale.forEach(subscribers::remove)
        }
    }

    fun finish() {
        synchronized(lock) {
            if (finished) return
            finished = true
            subscribers.values.forEach { it.close() }
            subscribers.clear()
        }
    }
}
