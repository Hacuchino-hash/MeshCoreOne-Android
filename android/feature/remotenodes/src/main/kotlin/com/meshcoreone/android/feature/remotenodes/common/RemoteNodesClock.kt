// AndroidOnly: WP-313 Injected wall/monotonic clock so remote-node state holders run on virtual time in JVM tests.
package com.meshcoreone.android.feature.remotenodes.common

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay

/**
 * Swift reads `Date.now`, `ContinuousClock.now` and `Task.sleep` directly. State holders take this
 * clock instead: [now] is the wall clock (snapshot windows, clock drift, history ranges), [elapsed]
 * is monotonic (retry deadlines) and [sleep] suspends cancellably.
 */
interface RemoteNodesClock {
    val now: Instant
    val elapsed: Duration
    suspend fun sleep(duration: Duration)
}

/** Production clock: system wall time, `System.nanoTime` and coroutine `delay`. */
object SystemRemoteNodesClock : RemoteNodesClock {
    override val now: Instant get() = Instant.now()
    override val elapsed: Duration get() = System.nanoTime().nanoseconds
    override suspend fun sleep(duration: Duration) = delay(duration)
}
