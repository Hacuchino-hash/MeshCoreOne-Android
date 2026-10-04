// AndroidOnly: WP-004 Assertions for primitive scripts and test-owned assembly.
package com.meshcoreone.android.core.testing

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptedOperationTest {
    @Test
    fun recordedSnapshotsDoNotChangeWhenLaterCallsArrive() {
        val calls = RecordedCalls<Int>()
        calls.record(1)
        val before = calls.values
        calls.record(2)
        assertEquals(listOf(1), before)
        assertEquals(listOf(1, 2), calls.values)
    }

    @Test
    fun scriptedOperationsPreserveOrderingAndSurfaceFailures() = runTest {
        val operation = ScriptedOperation<Int, String>()
        operation.enqueue { "first:$it" }
        operation.enqueue { throw IllegalStateException("scripted error") }
        assertEquals("first:4", operation(4))
        assertEquals("scripted error", assertFailsWith<IllegalStateException> { operation(5) }.message)
        assertEquals(listOf(4, 5), operation.calls.values)
        operation.assertConsumed()
        assertEquals(3, assertFailsWith<UnscriptedCallException> { operation(6) }.invocation)
    }

    @Test
    fun missingConsumerIsNotSuccessShapedFakeCoverage() {
        val operation = ScriptedOperation<Unit, Unit>()
        operation.enqueue {}
        assertEquals(1, assertFailsWith<UnconsumedScriptException> { operation.assertConsumed() }.remaining)
    }

    @Test
    fun scriptedSuspensionRetainsCancellationAndNoFabricatedResult() = runTest {
        val clock = TestClock()
        val operation = ScriptedOperation<Unit, String>()
        operation.enqueue { clock.sleepFor(10.milliseconds); "must not complete" }
        val task = async { operation(Unit) }
        runCurrent()
        assertEquals(1, clock.sleeperCount)
        task.cancel()
        assertFailsWith<CancellationException> { task.await() }
        assertEquals(0, clock.sleeperCount)
        assertEquals(listOf(Unit), operation.calls.values)
        operation.assertConsumed()
    }

    @Test
    fun testContainerUsesTheCallingTestSchedulerAndOwnedBackgroundScope() = runTest {
        val container = TestFixtureContainer(this)
        assertTrue(container.scheduler === testScheduler)
        assertTrue(container.scope === backgroundScope)
        val task = container.testScope.async(container.dispatcher) {
            container.clock.sleepFor(20.milliseconds)
            "done"
        }
        assertEquals("done", task.await())
        assertEquals(20L, testScheduler.currentTime)
    }
}
