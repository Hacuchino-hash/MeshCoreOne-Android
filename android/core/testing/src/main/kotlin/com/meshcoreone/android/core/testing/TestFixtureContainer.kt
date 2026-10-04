// AndroidOnly: WP-004 Primitive scripted operations and test-owned coroutine assembly, not an AppContainer.
package com.meshcoreone.android.core.testing

import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

class RecordedCalls<T> {
    private val lock = Any()
    private val calls = mutableListOf<T>()

    val values: List<T>
        get() = synchronized(lock) { Collections.unmodifiableList(calls.toList()) }

    fun record(value: T) {
        synchronized(lock) { calls.add(value) }
    }
}

class UnscriptedCallException(val invocation: Int) :
    AssertionError("No scripted result for invocation $invocation")

class UnconsumedScriptException(val remaining: Int) :
    AssertionError("$remaining scripted results were not consumed")

class ScriptedOperation<A, R> {
    private val lock = Any()
    private val steps = ArrayDeque<suspend (A) -> R>()
    val calls = RecordedCalls<A>()

    fun enqueue(step: suspend (A) -> R) {
        synchronized(lock) { steps.addLast(step) }
    }

    suspend operator fun invoke(argument: A): R {
        currentCoroutineContext().ensureActive()
        val step = synchronized(lock) {
            calls.record(argument)
            steps.removeFirstOrNull() ?: throw UnscriptedCallException(calls.values.size)
        }
        return step(argument)
    }

    fun assertConsumed() {
        synchronized(lock) {
            if (steps.isNotEmpty()) throw UnconsumedScriptException(steps.size)
        }
    }
}

class TestFixtureContainer(val testScope: TestScope) {
    val scheduler = testScope.testScheduler
    val dispatcher = StandardTestDispatcher(scheduler)
    val clock: SuspendingClock = SchedulerClock(scheduler)
    val scope: CoroutineScope
        get() = testScope.backgroundScope

    fun <A, R> scriptedOperation(): ScriptedOperation<A, R> = ScriptedOperation()
}
