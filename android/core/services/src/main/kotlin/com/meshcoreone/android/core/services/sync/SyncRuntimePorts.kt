// AndroidOnly: WP-214 Injected clock, sleeper and log sink replacing Swift Date()/ContinuousClock/Task.sleep/PersistentLogger.
package com.meshcoreone.android.core.services.sync

import com.meshcoreone.android.core.model.DebugLogLevel
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/**
 * Time source for sync. [now] stands for Swift `Date()`, [elapsed] for `ContinuousClock.now`, and [sleep]
 * for `Task.sleep`. Every timing, throttle, watchdog, retry and backoff path goes through this interface so
 * tests drive it with virtual time.
 */
interface SyncClock {
    /** Wall-clock time (Swift `Date()`). */
    fun now(): Instant

    /** Monotonic elapsed time for durations and deadlines (Swift `ContinuousClock.now`). */
    fun elapsed(): Duration

    /** Suspends for [duration]; throws `CancellationException` when the caller is cancelled. */
    suspend fun sleep(duration: Duration)

    companion object {
        /** Production clock: system wall time, `System.nanoTime` and coroutine `delay`. */
        val SYSTEM: SyncClock = object : SyncClock {
            private val origin = System.nanoTime()
            override fun now(): Instant = Instant.now()
            override fun elapsed(): Duration = (System.nanoTime() - origin).nanoseconds
            override suspend fun sleep(duration: Duration) = delay(duration)
        }
    }
}

/**
 * Destination for sync log lines (Swift `PersistentLogger(subsystem: "com.mc1", category: ...)`).
 * WP-303 wires WP-212's `diagnostics.PersistentLogger`; a sink must not throw, but any throw is contained.
 */
fun interface SyncLogSink {
    fun log(level: DebugLogLevel, category: String, message: String)

    companion object {
        /** Swift logger category for the coordinator. */
        const val CATEGORY = "SyncCoordinator"

        /** Swift logger category for the connection manager's sync retry extension. */
        const val RETRY_CATEGORY = "ConnectionManager"

        /** Discards every line. */
        val NONE: SyncLogSink = SyncLogSink { _, _, _ -> }
    }
}

/** Writes one log line, containing any throw from the sink (Swift logging cannot throw). */
internal fun SyncLogSink.emit(level: DebugLogLevel, category: String, message: String) {
    try {
        log(level, category, message)
    } catch (_: Exception) {
        // A failing log sink must never change sync behavior.
    }
}

/**
 * Rethrows when the calling coroutine itself is cancelled. A `CancellationException` raised by a
 * collaborator while the caller is still active (an internal `withTimeout`, a foreign job) is an ordinary
 * failure, as Swift only special-cases its own `CancellationError`.
 */
internal suspend fun Throwable.rethrowIfCallerCancelled() {
    if (this is CancellationException) currentCoroutineContext().ensureActive()
}

/** Whether [failure] is a cancellation of the calling coroutine (not a collaborator's own). */
internal suspend fun isCallerCancellation(failure: Throwable): Boolean =
    failure is CancellationException && !currentCoroutineContext().isActive

/** Swift `error.localizedDescription` for a Kotlin throwable. */
internal fun Throwable.syncDescription(): String = message ?: javaClass.simpleName

/**
 * Runs a collaborator call that Swift cannot throw from. A throw is logged and replaced by [fallback]
 * instead of escaping (Swift's call would have completed). `CancellationException` is rethrown when the
 * caller itself is cancelled; one a collaborator raises while the caller is still active (an internal
 * timeout, a foreign job) is an ordinary failure.
 */
internal suspend inline fun <T> containing(
    log: SyncLogSink,
    category: String,
    label: String,
    fallback: T,
    block: () -> T,
): T = try {
    block()
} catch (cancelled: CancellationException) {
    if (!currentCoroutineContext().isActive) throw cancelled
    log.emit(DebugLogLevel.ERROR, category, "$label was cancelled outside the caller: ${cancelled.syncDescription()}")
    fallback
} catch (failure: Exception) {
    log.emit(DebugLogLevel.ERROR, category, "$label threw: ${failure.syncDescription()}")
    fallback
}
