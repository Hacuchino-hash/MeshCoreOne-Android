// AndroidOnly: WP-214 Deterministic single-thread dispatcher and virtual clock (no kotlinx-coroutines-test in this module).
package com.meshcoreone.android.core.services.sync

import java.time.Instant
import java.util.Collections
import java.util.TreeMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DynamicTest

/**
 * FIFO dispatcher driven by the test thread: nothing runs until the driver pumps it, so every
 * interleaving is reproducible. One thread stands in for Swift actor/MainActor serialization.
 */
internal class ManualDispatcher : CoroutineDispatcher() {
    private val lock = Any()
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(lock) { queue.addLast(block) }
    }

    val pending: Int get() = synchronized(lock) { queue.size }

    fun runOne(): Boolean {
        val task = synchronized(lock) { queue.removeFirstOrNull() } ?: return false
        task.run()
        return true
    }
}

/**
 * Virtual time: [sleep] parks until the driver advances to its deadline. Time only moves when no
 * coroutine is runnable, earliest deadline first, ties in registration order.
 */
internal class VirtualSyncClock(private val origin: Instant = Instant.parse("2026-10-07T12:00:00.250Z")) : SyncClock {
    private val lock = Any()
    private var nowNanos = 0L
    private var sequence = 0L
    private val sleepers = TreeMap<Pair<Long, Long>, CancellableContinuation<Unit>>(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })

    override fun now(): Instant = origin.plusNanos(synchronized(lock) { nowNanos })
    override fun elapsed(): Duration = synchronized(lock) { nowNanos }.nanoseconds

    override suspend fun sleep(duration: Duration) {
        if (duration <= Duration.ZERO) {
            yield()
            return
        }
        suspendCancellableCoroutine { continuation ->
            val key = synchronized(lock) {
                val key = (nowNanos + duration.inWholeNanoseconds) to sequence++
                sleepers[key] = continuation
                key
            }
            continuation.invokeOnCancellation { synchronized(lock) { sleepers.remove(key) } }
        }
    }

    val sleeperCount: Int get() = synchronized(lock) { sleepers.size }

    /** Wakes the earliest sleeper, moving time to its deadline; false when nobody sleeps. */
    fun advanceToNext(): Boolean {
        val entry = synchronized(lock) {
            val first = sleepers.pollFirstEntry() ?: return false
            nowNanos = maxOf(nowNanos, first.key.first)
            first
        }
        entry.value.resume(Unit)
        return true
    }
}

/** What a test body sees: the service scope, the virtual clock, and Swift-style waiting helpers. */
internal class SyncTestScope(
    val scope: CoroutineScope,
    val clock: VirtualSyncClock,
    private val dispatcher: ManualDispatcher,
) {
    /** Runs every currently runnable coroutine before returning (no virtual time passes). */
    suspend fun settle() {
        repeat(10_000) {
            if (dispatcher.pending == 0) return
            yield()
        }
        fail("settle(): work kept rescheduling itself")
    }

    /** Swift `Task.sleep` in a test body, on virtual time. */
    suspend fun sleep(duration: Duration) = clock.sleep(duration)

    /** Swift `try await waitUntil("...") { ... }`: polls on virtual time and fails at [timeout]. */
    suspend fun waitUntil(message: String, timeout: Duration = 10.seconds, predicate: suspend () -> Boolean) {
        val deadline = clock.elapsed() + timeout
        while (!predicate()) {
            if (clock.elapsed() >= deadline) fail("waitUntil timed out: $message")
            clock.sleep(10.milliseconds)
        }
    }

    /** Swift `Task { ... }` in a test body. */
    fun <T> task(block: suspend CoroutineScope.() -> T): Deferred<T> = scope.async(block = block)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    fun coordinator(): SyncCoordinator = SyncCoordinator(scope, clock)
}

/**
 * Runs one case to completion on a fresh dispatcher and clock. Fails on deadlock (nothing runnable and
 * nobody sleeping), on a wall-time bound, and on any uncaught exception in the service scope.
 */
internal fun runSyncTest(wallTimeout: Duration = 60.seconds, body: suspend SyncTestScope.() -> Unit) {
    val dispatcher = ManualDispatcher()
    val clock = VirtualSyncClock()
    val failures = Collections.synchronizedList(ArrayList<Throwable>())
    val supervisor = SupervisorJob()
    val scope = CoroutineScope(dispatcher + supervisor + CoroutineExceptionHandler { _, failure -> failures += failure })
    val harness = SyncTestScope(scope, clock, dispatcher)
    val main = scope.async { harness.body() }
    var outcome: Throwable? = null
    main.invokeOnCompletion { outcome = it }
    val deadline = System.nanoTime() + wallTimeout.inWholeNanoseconds
    while (!main.isCompleted) {
        if (System.nanoTime() > deadline) fail("Wall-time bound exceeded")
        if (dispatcher.runOne()) continue
        if (clock.advanceToNext()) continue
        fail("Deadlock: no runnable coroutine and no virtual sleeper")
    }
    scope.cancel()
    var drained = 0
    while (dispatcher.runOne() && drained++ < 100_000) Unit
    outcome?.let { throw it }
    if (failures.isNotEmpty()) throw AssertionError("Uncaught service-scope failures: $failures", failures.first())
}

/** One original Swift case under its exact id. */
internal fun syncCase(suite: String, name: String, body: suspend SyncTestScope.() -> Unit): DynamicTest =
    DynamicTest.dynamicTest("$suite::$name()") { runSyncTest(body = body) }

/** One original parameterized family, executed once per Swift argument on fresh runtimes. */
internal fun <A> syncParameterizedCase(
    suite: String,
    name: String,
    signature: String,
    arguments: List<A>,
    body: suspend SyncTestScope.(A) -> Unit,
): DynamicTest = DynamicTest.dynamicTest("$suite::$name$signature") {
    arguments.forEach { argument -> runSyncTest { body(argument) } }
}

/** A WP-214 native regression or adaptation case. */
internal fun nativeCase(name: String, body: suspend SyncTestScope.() -> Unit): DynamicTest =
    DynamicTest.dynamicTest("WP-214::$name") { runSyncTest(body = body) }

/** Synchronous Swift case with no coroutine work. */
internal fun pureCase(suite: String, name: String, body: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest("$suite::$name()", body)

/** Thread-safe call counter (Swift `CallTracker`). */
internal class CallTracker {
    @Volatile var callCount = 0
        private set
    val wasCalled: Boolean get() = callCount > 0
    @Synchronized fun markCalled() {
        callCount += 1
    }
}

/** Thread-safe value recorder (Swift `ValueTracker`). */
internal class ValueTracker<T> {
    private val recorded = Collections.synchronizedList(ArrayList<T>())
    val values: List<T> get() = synchronized(recorded) { recorded.toList() }
    fun record(value: T) {
        recorded += value
    }
}
