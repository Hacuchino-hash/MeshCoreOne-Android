// AndroidOnly: WP-212 Injectable wall clock and sleeper so debug-log flush/prune timing is deterministic in JVM tests.
package com.meshcoreone.android.core.services.diagnostics

import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Time source for the debug-log pipeline.
 *
 * Swift reads `Date()` and `Task.sleep(for:)` directly; Android injects both so the
 * 5 s flush timer and the hourly prune cadence can be driven by a manual clock.
 */
interface DebugLogClock {
    /** Current wall-clock time (Swift `Date()`). */
    fun now(): Instant

    /**
     * Suspends for [duration] (Swift `Task.sleep(for:)`). Must be cancellable and must
     * propagate [kotlinx.coroutines.CancellationException] when the caller is cancelled.
     */
    suspend fun sleep(duration: Duration)
}

/** Production clock: system wall time and coroutine [delay]. */
object SystemDebugLogClock : DebugLogClock {
    override fun now(): Instant = Instant.now()
    override suspend fun sleep(duration: Duration) = delay(duration)
}
