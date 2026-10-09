// AndroidOnly: WP-316 Deterministic single-thread dispatcher and virtual clock (kotlinx-coroutines-test is not on this classpath).
package com.meshcoreone.android.feature.tools.diagnostics.support

import com.meshcoreone.android.feature.tools.diagnostics.DiagnosticsClock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield

internal val EPOCH: Instant = Instant.parse("2024-01-01T00:00:00Z")

/**
 * Runs every coroutine of a test on the calling thread: dispatches queue up and only run inside
 * [runCurrent], like the Swift main actor between awaits. Time moves only through [advanceBy].
 */
internal class TestScheduler(origin: Instant = EPOCH) {
    private val queue = ArrayDeque<Runnable>()
    private val failures = mutableListOf<Throwable>()
    private val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }
    val clock = VirtualClock(origin)
    val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> failures += error })

    /** Runs queued work until idle; rethrows any uncaught coroutine failure. */
    fun runCurrent() {
        var steps = 0
        while (queue.isNotEmpty()) {
            queue.removeFirst().run()
            check(++steps < MAX_STEPS) { "Scheduler did not go idle" }
        }
        failures.firstOrNull()?.let { failures.clear(); throw AssertionError("Uncaught coroutine failure", it) }
    }

    /** Advances virtual time, waking each due sleeper in deadline order and running its work. */
    fun advanceBy(duration: Duration) {
        runCurrent()
        clock.advanceBy(duration) { runCurrent() }
        runCurrent()
    }

    /** Runs a suspending call to completion on the scheduler and returns its result. */
    fun <T> run(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        scope.launch { outcome = runCatching { block() } }
        runCurrent()
        return checkNotNull(outcome) { "Suspending call did not complete" }.getOrThrow()
    }

    private companion object {
        const val MAX_STEPS = 1_000_000
    }
}

/** Virtual wall clock; [sleep] parks until [advanceBy] passes its deadline. */
internal class VirtualClock(private val origin: Instant) : DiagnosticsClock {
    private class Sleeper(val deadline: Long, val continuation: CancellableContinuation<Unit>)

    private var elapsedNanos = 0L
    private val sleepers = mutableListOf<Sleeper>()
    val sleeps = mutableListOf<Duration>()
    val pendingSleepers: Int get() = sleepers.size

    override fun now(): Instant = origin.plusNanos(elapsedNanos)

    override suspend fun sleep(duration: Duration) {
        sleeps += duration
        if (!duration.isPositive()) {
            yield()
            return
        }
        suspendCancellableCoroutine { continuation ->
            val sleeper = Sleeper(elapsedNanos + duration.inWholeNanoseconds, continuation)
            sleepers += sleeper
            continuation.invokeOnCancellation { sleepers -= sleeper }
        }
    }

    fun advanceBy(duration: Duration, drain: () -> Unit) {
        val target = elapsedNanos + duration.inWholeNanoseconds
        while (true) {
            val due = sleepers.filter { it.deadline <= target }.minByOrNull { it.deadline } ?: break
            sleepers -= due
            elapsedNanos = maxOf(elapsedNanos, due.deadline)
            due.continuation.resumeWith(Result.success(Unit))
            drain()
        }
        elapsedNanos = target
    }
}
