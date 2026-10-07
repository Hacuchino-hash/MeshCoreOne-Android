// AndroidOnly: WP-206 Injected monotonic clock and diagnostic sink shared by the platform connectivity adapters.
package com.meshcoreone.android.core.connectivity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay

/** Monotonic elapsed time plus a cancellable suspension; tests inject a virtual clock. */
interface ConnectivityClock {
    val elapsed: Duration
    suspend fun sleep(duration: Duration)
}

class SystemConnectivityClock : ConnectivityClock {
    private val origin = System.nanoTime()
    override val elapsed: Duration get() = (System.nanoTime() - origin).nanoseconds
    override suspend fun sleep(duration: Duration) = delay(duration)
}

/**
 * Receives every failure the adapters intentionally contain (best-effort cleanup, callbacks that
 * cannot throw). Nothing is swallowed silently: each contained throwable is reported here.
 */
fun interface ConnectivityDiagnostics {
    fun report(operation: String, failure: Throwable?)

    companion object {
        val NONE = ConnectivityDiagnostics { _, _ -> }
    }
}

/** Typed connectivity failures that do not depend on the runtime module's error hierarchy. */
sealed class ConnectivityError(message: String) : Exception(message) {
    class NotConnected : ConnectivityError("Not connected to device")
    class DeviceNotFound : ConnectivityError("Device not found")
    class ConnectionFailed(val detail: String) : ConnectivityError(detail)
}
