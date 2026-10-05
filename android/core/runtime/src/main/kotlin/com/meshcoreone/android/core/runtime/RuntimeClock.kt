// AndroidOnly: WP-207 Injectable elapsed-time, wall-time and deadline clocks.
package com.meshcoreone.android.core.runtime

import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay

interface DeadlineClock {
    suspend fun sleep(duration: Duration)
}

interface RuntimeClock : DeadlineClock {
    val elapsed: Duration
    val instant: Instant
}

class SystemRuntimeClock(private val wall: Clock = Clock.systemUTC()) : RuntimeClock {
    private val origin = System.nanoTime()
    override val elapsed: Duration get() = (System.nanoTime() - origin).nanoseconds
    override val instant: Instant get() = wall.instant()
    override suspend fun sleep(duration: Duration) = delay(duration)
}
