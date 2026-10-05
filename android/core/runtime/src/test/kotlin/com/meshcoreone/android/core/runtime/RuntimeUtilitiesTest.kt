// AndroidOnly: WP-207 Deterministic deadlines, FIFO cancellation and bounded/terminal multicast assertions.
package com.meshcoreone.android.core.runtime

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeUtilitiesTest {
    @TestFactory
    fun nativeCases() = listOf(
        nativeCase("newest bounded stream retains the latest two events and drains on finish") {
            val broadcaster = EventBroadcaster<Int>(); val stream = broadcaster.subscribe(BufferingPolicy.Newest(2))
            (1..5).forEach(broadcaster::yield); broadcaster.finish()
            assertEquals(listOf(4, 5), stream.events.toList())
        },
        nativeCase("oldest bounded stream retains the first two events") {
            val broadcaster = EventBroadcaster<Int>(); val stream = broadcaster.subscribe(BufferingPolicy.Oldest(2))
            (1..5).forEach(broadcaster::yield); broadcaster.finish()
            assertEquals(listOf(1, 2), stream.events.toList())
        },
        nativeCase("unbounded stream delivers queued terminal data before its original error") {
            val broadcaster = EventBroadcaster<Int>(); val stream = broadcaster.subscribe()
            val error = IllegalStateException("terminal"); val received = mutableListOf<Int>()
            broadcaster.yield(1); broadcaster.yield(2); broadcaster.finish(error)
            val thrown = assertFailsWith<IllegalStateException> { stream.events.toList(received) }
            assertSame(error, thrown); assertEquals(listOf(1, 2), received)
        },
        nativeCase("explicit subscriber close is idempotent and sibling streams remain live") {
            val broadcaster = EventBroadcaster<Int>(); val a = broadcaster.subscribe(); val b = broadcaster.subscribe()
            a.close(); a.close(); broadcaster.yield(8); broadcaster.finish()
            assertEquals(0, broadcaster.subscriberCount); assertEquals(listOf(8), b.events.toList())
        },
        nativeCase("queued semaphore acquisition remains FIFO with a cancelled middle waiter") {
            val semaphore = AsyncSemaphore(0); val received = mutableListOf<Int>()
            val jobs = (1..3).map { number -> backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                semaphore.wait(); received += number
            } }
            assertEquals(3, semaphore.waitingCount); jobs[1].cancelAndJoin()
            semaphore.signal(); runCurrent(); semaphore.signal(); runCurrent()
            assertEquals(listOf(1, 3), received); assertEquals(0, semaphore.waitingCount)
            jobs.forEach { it.join() }
        },
        nativeCase("cancellation after a permit is handed off compensates the next FIFO waiter") {
            val semaphore = AsyncSemaphore(0); var successor = false
            val first = backgroundScope.launch { semaphore.wait() }
            val second = backgroundScope.launch { semaphore.wait(); successor = true }
            runCurrent(); semaphore.signal(); first.cancel(); runCurrent()
            first.join(); second.join(); assertTrue(successor); assertEquals(0L, semaphore.availablePermits)
        },
        nativeCase("withPermit releases on original failure") {
            val semaphore = AsyncSemaphore(1); val original = IllegalStateException("failed")
            assertSame(original, assertFailsWith<IllegalStateException> { semaphore.withPermit { throw original } })
            assertEquals(1L, semaphore.availablePermits)
        },
        nativeCase("deadline returns successful result and joins losing sleeper") {
            val clock = TestClock(testScheduler)
            assertEquals(17, withRuntimeTimeout(3.seconds, "success", clock) { 17 })
            assertEquals(0, coroutineContext[Job]!!.children.count())
        },
        nativeCase("deadline preserves operation error identity") {
            val clock = TestClock(testScheduler); val original = IllegalStateException("original")
            assertSame(original, assertFailsWith<IllegalStateException> {
                withRuntimeTimeout<Int>(3.seconds, "failure", clock) { throw original }
            })
        },
        nativeCase("deadline cancels and joins pending child and preserves operation name and duration") {
            val clock = TestClock(testScheduler); var ended = false
            val thrown = assertFailsWith<TimeoutError> {
                withRuntimeTimeout(3.seconds, "session.start", clock) { try { awaitCancellation() } finally { ended = true } }
            }
            assertEquals("session.start", thrown.operationName); assertEquals(3.seconds, thrown.timeout); assertTrue(ended)
        },
        nativeCase("cooperative timeout is cancellation rather than timeout error") {
            var ended = false
            assertFailsWith<CancellationException> {
                withCooperativeTimeout(2.seconds, TestClock(testScheduler)) { try { awaitCancellation() } finally { ended = true } }
            }
            assertTrue(ended)
        },
        nativeCase("parent cancellation joins both deadline children") {
            var ended = false
            val task = backgroundScope.launch {
                withRuntimeTimeout(9.seconds, "parent", TestClock(testScheduler)) {
                    try { awaitCancellation() } finally { ended = true }
                }
            }
            runCurrent(); task.cancelAndJoin(); assertTrue(ended); assertTrue(task.children.none())
        },
        nativeCase("advisory deadline still awaits cancellation-ignoring child cleanup") {
            val release = CompletableDeferred<Unit>(); var ended = false
            val task = backgroundScope.async {
                runCatching {
                    withRuntimeTimeout(1.seconds, "advisory", TestClock(testScheduler)) {
                        try { awaitCancellation() } finally { withContext(NonCancellable) { release.await(); ended = true } }
                    }
                }
            }
            runCurrent(); advanceTimeBy(1000); runCurrent()
            assertFalse(task.isCompleted); release.complete(Unit); runCurrent()
            assertIs<TimeoutError>(task.await().exceptionOrNull()); assertTrue(ended)
        },
    )
}
