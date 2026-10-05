// AndroidOnly: WP-205 Monotonic deadlines and pacing; deterministic tests adapt the shared test clock.
package com.meshcoreone.android.core.ble

import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

interface BleClock {
    val now: Duration
    suspend fun sleepUntil(deadline: Duration)
}

class MonotonicBleClock : BleClock {
    private val origin = TimeSource.Monotonic.markNow()
    override val now: Duration get() = origin.elapsedNow()

    override suspend fun sleepUntil(deadline: Duration) {
        currentCoroutineContext().ensureActive()
        val remaining = deadline - now
        if (remaining.isPositive()) delay(remaining)
    }
}
