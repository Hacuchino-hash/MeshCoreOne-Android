// AndroidOnly: WP-311 Clock-driven bound for radio commands (the Swift `withTimeout` + `RadioCommandTimeout.delete` the nodes delete uses).
package com.meshcoreone.android.feature.nodes.deps

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/** Shared bounds for radio-backed list commands (`RadioCommandTimeout`, WP-304 owns the shared copy). */
object RadioCommandTimeout {
    /** Upper bound for a delete that round-trips to the radio. */
    val delete: Duration = 7.seconds
}

/** A bounded command outlived its deadline (`TimeoutError`). */
class CommandTimeoutException(val operationName: String, val timeout: Duration) :
    Exception("$operationName timed out after $timeout")

/**
 * Races [operation] against [timeout] on [clock]; the loser is cancelled. A deadline that fires first
 * throws [CommandTimeoutException]; the operation's own failures (and cancellation) propagate unchanged.
 */
suspend fun <T> withCommandTimeout(
    clock: NodesClock,
    timeout: Duration,
    operationName: String,
    operation: suspend () -> T,
): T = coroutineScope {
    val work = async { operation() }
    val deadline = async { clock.sleep(timeout) }
    try {
        select {
            work.onAwait { it }
            deadline.onAwait { throw CommandTimeoutException(operationName, timeout) }
        }
    } finally {
        work.cancel()
        deadline.cancel()
    }
}

/** Production clock: wall time from the system, monotonic time from `System.nanoTime`. */
object SystemNodesClock : NodesClock {
    private val origin = System.nanoTime()
    override val wallNow: Instant get() = Instant.now()
    override val elapsed: Duration get() = (System.nanoTime() - origin).nanoseconds
    override suspend fun sleep(duration: Duration) = delay(duration)
}
