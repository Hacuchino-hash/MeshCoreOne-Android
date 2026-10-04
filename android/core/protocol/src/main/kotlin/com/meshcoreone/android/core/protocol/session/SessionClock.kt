// AndroidOnly: WP-107 Injectable monotonic deadlines and source wall-clock timestamps without a testing dependency.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.config.MeshCoreException
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

interface SessionClock {
    val now: Duration
    val wallClock: Clock
    suspend fun sleepFor(duration: Duration)
}

class SystemSessionClock(override val wallClock: Clock = Clock.systemUTC()) : SessionClock {
    private val origin = TimeSource.Monotonic.markNow()
    override val now: Duration get() = origin.elapsedNow()
    override suspend fun sleepFor(duration: Duration) = delay(duration)
}

internal fun timeoutDuration(seconds: Double, allowZero: Boolean = false): Duration {
    if (!seconds.isFinite() || seconds < 0 || (!allowZero && seconds == 0.0)) {
        throw MeshCoreException.InvalidInput("Session timeout must be finite and ${if (allowZero) "nonnegative" else "positive"}")
    }
    val duration = seconds.seconds
    if (!duration.isFinite()) throw MeshCoreException.InvalidInput("Session timeout exceeds the monotonic clock range")
    return duration
}

internal suspend fun <T> SessionClock.withDeadline(seconds: Double, operation: suspend () -> T): T = supervisorScope {
    val duration = timeoutDuration(seconds)
    val timer = async { sleepFor(duration) }
    val result = async { operation() }
    try {
        select {
            result.onAwait { it }
            timer.onAwait { throw MeshCoreException.Timeout() }
        }
    } finally {
        result.cancel()
        timer.cancel()
        result.cancelAndJoin()
        timer.cancelAndJoin()
    }
}
