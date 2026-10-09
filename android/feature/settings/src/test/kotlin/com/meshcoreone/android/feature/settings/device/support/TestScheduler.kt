// AndroidOnly: WP-317 Deterministic single-thread dispatcher and virtual clock (kotlinx-coroutines-test is not on this classpath).
package com.meshcoreone.android.feature.settings.device.support

import com.meshcoreone.android.feature.settings.device.SettingsClock
import com.meshcoreone.android.feature.settings.device.SettingsEnvironment
import com.meshcoreone.android.core.ui.UiText
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

/**
 * Runs every coroutine of a test on the calling thread: dispatches queue up and only run inside [runCurrent],
 * like the Swift main actor between awaits. Time moves only through [advanceBy].
 */
internal class TestScheduler {
    private val queue = ArrayDeque<Runnable>()
    private val failures = mutableListOf<Throwable>()
    private val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }
    val clock = VirtualClock()
    val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> failures += error })
    var dismissCount = 0

    fun environment(describe: (Throwable) -> UiText = { UiText.Verbatim(it.message ?: it.toString()) }) =
        SettingsEnvironment(scope, clock, describe) { dismissCount++ }

    fun runCurrent() {
        var steps = 0
        while (queue.isNotEmpty()) {
            queue.removeFirst().run()
            check(++steps < 1_000_000) { "Scheduler did not go idle" }
        }
        failures.firstOrNull()?.let { failures.clear(); throw AssertionError("Uncaught coroutine failure", it) }
    }

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
}

internal class VirtualClock : SettingsClock {
    private class Sleeper(val deadline: Long, val continuation: CancellableContinuation<Unit>)

    private var elapsedNanos = 0L
    private val sleepers = mutableListOf<Sleeper>()
    val sleeps = mutableListOf<Duration>()
    val pendingSleepers: Int get() = sleepers.size

    override suspend fun sleep(duration: Duration) {
        sleeps += duration
        if (!duration.isPositive()) { yield(); return }
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
