// AndroidOnly: WP-313 Native coverage of the status retry budget and section-request flags (no Swift case exercises them).
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.runSuspend
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class TransientRetryRunnerTest {
    private val clock = VirtualClock()
    private val runner = TransientRetryRunner(clock, FakeFaults)

    private class Flags {
        val loading = mutableListOf<Boolean>()
        val errors = mutableListOf<RemoteNodesText?>()
        val lastError: RemoteNodesText? get() = errors.lastOrNull()
    }

    private suspend fun <T> run(flags: Flags, timeoutMessage: RemoteNodesText? = null, operation: suspend (Duration) -> T): T? {
        var result: T? = null
        if (timeoutMessage == null) {
            runner.runRetryingSectionRequest("test", { flags.loading += it }, { flags.errors += it }, operation = operation) { result = it }
        } else {
            runner.runRetryingSectionRequest("test", { flags.loading += it }, { flags.errors += it }, timeoutMessage, operation) { result = it }
        }
        return result
    }

    @Test
    fun `first attempt receives the whole 45 second budget`() = runSuspend {
        val flags = Flags()
        val timeouts = mutableListOf<Duration>()
        val result = run(flags) { timeouts += it; "ok" }
        assertEquals("ok", result)
        assertEquals(listOf(45.seconds), timeouts)
        assertEquals(listOf(true, false), flags.loading)
        assertEquals(listOf<RemoteNodesText?>(null), flags.errors)
    }

    @Test
    fun `transient replies retry after 500ms 1s and 2s with the remaining budget`() = runSuspend {
        val timeouts = mutableListOf<Duration>()
        var attempts = 0
        val result = runner.performWithTransientRetries("test") {
            timeouts += it
            attempts += 1
            if (attempts < 4) throw NoResponseYetFault()
            "done"
        }
        assertEquals("done", result)
        assertEquals(listOf(500.milliseconds, 1.seconds, 2.seconds), clock.sleeps)
        assertEquals(listOf(45.seconds, 44.5.seconds, 43.5.seconds, 41.5.seconds), timeouts)
    }

    @Test
    fun `a fourth transient reply surfaces as a failure once the delays run out`() = runSuspend {
        val flags = Flags()
        var attempts = 0
        val fault = NoResponseYetFault()
        run(flags) { attempts += 1; throw fault }
        assertEquals(4, attempts)
        assertEquals(RemoteNodesText.Failure(fault), flags.lastError)
        assertEquals(false, flags.loading.last())
    }

    @Test
    fun `non-transient errors are not retried`() = runSuspend {
        val flags = Flags()
        var attempts = 0
        val fault = OtherFault()
        run(flags) { attempts += 1; throw fault }
        assertEquals(1, attempts)
        assertTrue(clock.sleeps.isEmpty())
        assertEquals(RemoteNodesText.Failure(fault), flags.lastError)
    }

    @Test
    fun `retry waits are capped by the remaining budget and exhaustion is a timeout`() = runSuspend {
        val flags = Flags()
        var attempts = 0
        run(flags) {
            attempts += 1
            clock.advance(44.8.seconds)
            throw NoResponseYetFault()
        }
        assertEquals(1, attempts)
        assertEquals(listOf(200.milliseconds), clock.sleeps)
        assertEquals(TransientRetryRunner.REQUEST_TIMED_OUT, flags.lastError)
    }

    @Test
    fun `an exhausted budget before a retry wait throws the timeout`() = runSuspend {
        assertFailsWith<RemoteRequestTimeoutException> {
            runner.performWithTransientRetries("test") {
                clock.advance(46.seconds)
                throw NoResponseYetFault()
            }
        }
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test
    fun `service timeouts use the default or the overridden timeout text`() = runSuspend {
        val defaults = Flags()
        run<Unit>(defaults) { throw TimeoutFault() }
        assertEquals(TransientRetryRunner.REQUEST_TIMED_OUT, defaults.lastError)

        val custom = Flags()
        val message = RemoteNodesText.Verbatim("custom")
        run<Unit>(custom, timeoutMessage = message) { throw TimeoutFault() }
        assertEquals(message, custom.lastError)
    }

    @Test
    fun `cancellation clears loading only and propagates`() = runSuspend {
        val flags = Flags()
        val cancellation = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> { run<Unit>(flags) { throw cancellation } }
        assertSame(cancellation, thrown)
        assertEquals(listOf(true, false), flags.loading)
        assertEquals(listOf<RemoteNodesText?>(null), flags.errors)
    }

    @Test
    fun `transient classification and timeout classification follow the fault classifier`() {
        assertTrue(runner.isTransientError(NoResponseYetFault()))
        assertEquals(false, runner.isTransientError(TimeoutFault()))
        assertTrue(runner.isTimeout(TimeoutFault()))
        assertTrue(runner.isTimeout(RemoteRequestTimeoutException("x")))
        assertEquals(false, runner.isTimeout(OtherFault()))
        assertIs<RemoteNodesText.Resource>(TransientRetryRunner.REQUEST_TIMED_OUT)
        assertNull((TransientRetryRunner.REQUEST_TIMED_OUT as RemoteNodesText.Resource).args.firstOrNull())
    }
}
