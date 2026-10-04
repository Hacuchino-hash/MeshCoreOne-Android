// PortedFrom: MC1Services/Tests/MC1ServicesTests/Helpers/TestClock.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/Helpers/TestClock.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.testing

import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

interface SuspendingClock {
    val now: Duration
    suspend fun sleepUntil(deadline: Duration)

    suspend fun sleepFor(duration: Duration) = sleepUntil(now + duration)
}

class TestClock : SuspendingClock {
    private class Sleeper(
        val deadline: Duration,
        val continuation: CancellableContinuation<Unit>,
    )

    private val lock = Any()
    private var instant = Duration.ZERO
    private val sleepers = mutableListOf<Sleeper>()

    override val now: Duration
        get() = synchronized(lock) { instant }

    val minimumResolution: Duration
        get() = Duration.ZERO

    val sleeperCount: Int
        get() = synchronized(lock) { sleepers.size }

    override suspend fun sleepUntil(deadline: Duration) {
        require(deadline.isFinite()) { "A test-clock deadline must be finite" }
        currentCoroutineContext().ensureActive()
        suspendCancellableCoroutine { continuation ->
            val sleeper = Sleeper(deadline, continuation)
            val immediate = synchronized(lock) {
                when {
                    !continuation.isActive -> false
                    deadline <= instant -> true
                    else -> {
                        sleepers.add(sleeper)
                        false
                    }
                }
            }
            continuation.invokeOnCancellation {
                synchronized(lock) { sleepers.remove(sleeper) }
            }
            if (immediate) continuation.resume(Unit)
        }
    }

    fun advanceBy(duration: Duration = Duration.ZERO) {
        require(duration.isFinite()) { "A test-clock advance must be finite" }
        val due = synchronized(lock) {
            val advanced = instant + duration
            require(advanced.isFinite()) { "Test-clock time overflow" }
            instant = advanced
            sleepers.filter { it.deadline <= instant }.also { sleepers.removeAll(it.toSet()) }
        }
        // Resume outside the lock: a resumed consumer may immediately sleep again.
        due.forEach { it.continuation.resume(Unit) }
    }
}
