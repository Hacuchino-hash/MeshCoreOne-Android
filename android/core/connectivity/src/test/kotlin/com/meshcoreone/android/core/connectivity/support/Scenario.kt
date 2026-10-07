// AndroidOnly: WP-206 Deterministic single-thread scenario runner and virtual clock (no coroutines-test on this locked classpath).
package com.meshcoreone.android.core.connectivity.support

import com.meshcoreone.android.core.connectivity.ConnectivityClock
import com.meshcoreone.android.core.runtime.RuntimeClock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Original-case binding carried in source (same mechanism as core:data): the JUnit XML names the
 * method; this annotation binds it to the frozen Swift case id and its disposition.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
internal annotation class OriginalCase(val value: String, val disposition: String = "source-behavior")

internal val EPOCH: Instant = Instant.ofEpochSecond(1_704_067_200)

/** Virtual monotonic/wall clock: sleepers resume only when the test advances time. */
internal class ManualClock(private val origin: Instant = EPOCH) : RuntimeClock, ConnectivityClock {
    private class Sleeper(val deadline: Long, val continuation: CancellableContinuation<Unit>)
    private val lock = Any()
    private var now = 0L
    private val sleepers = mutableListOf<Sleeper>()
    private val recorded = mutableListOf<Duration>()

    override val elapsed: Duration get() = synchronized(lock) { now }.nanoseconds
    override val instant: Instant get() = origin.plusNanos(synchronized(lock) { now })
    val sleeps: List<Duration> get() = synchronized(lock) { recorded.toList() }
    val pendingSleepers: Int get() = synchronized(lock) { sleepers.size }

    override suspend fun sleep(duration: Duration) {
        synchronized(lock) { recorded += duration }
        if (!duration.isPositive()) { yield(); return }
        suspendCancellableCoroutine { continuation ->
            val sleeper = synchronized(lock) { Sleeper(now + duration.inWholeNanoseconds, continuation).also { sleepers += it } }
            continuation.invokeOnCancellation { synchronized(lock) { sleepers -= sleeper } }
        }
    }

    /** Advances to each due deadline in order, letting woken work run before the next one. */
    suspend fun advanceBy(duration: Duration) {
        val target = synchronized(lock) { now + duration.inWholeNanoseconds }
        while (true) {
            val due = synchronized(lock) {
                sleepers.filter { it.deadline <= target }.minByOrNull { it.deadline }?.also {
                    sleepers -= it
                    now = maxOf(now, it.deadline)
                }
            } ?: break
            due.continuation.resumeWith(Result.success(Unit))
            settle()
        }
        synchronized(lock) { now = maxOf(now, target) }
        settle()
    }

    /** Jumps to the next pending deadline (if any) and runs the woken work. */
    suspend fun advanceToNext(): Boolean {
        val next = synchronized(lock) { sleepers.minOfOrNull { it.deadline } } ?: return false
        advanceBy((next - synchronized(lock) { now }).coerceAtLeast(0).nanoseconds)
        return true
    }
}

/** Lets every coroutine queued on the single-thread event loop run to its next suspension. */
internal suspend fun settle(rounds: Int = 400) = repeat(rounds) { yield() }

internal class Scenario(val scope: CoroutineScope, val clock: ManualClock) {
    /** Settles, advancing virtual time only while [condition] is unmet and timers are pending. */
    suspend fun awaitCondition(maxVirtual: Duration = 300.seconds, condition: () -> Boolean): Boolean {
        val limit = clock.elapsed + maxVirtual
        settle()
        while (!condition()) {
            if (clock.elapsed >= limit || !clock.advanceToNext()) {
                settle()
                return condition()
            }
        }
        return true
    }
}

/**
 * Runs a test body on one thread with a standalone supervisor scope; everything left running is
 * cancelled and drained at the end so no work leaks into the next test.
 */
internal fun scenario(body: suspend Scenario.() -> Unit) = runBlocking {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(kotlinx.coroutines.Job) + job)
    val clock = ManualClock()
    try {
        withTimeout(60.seconds) { Scenario(scope, clock).body() }
    } finally {
        job.cancel()
        settle(800)
    }
}
