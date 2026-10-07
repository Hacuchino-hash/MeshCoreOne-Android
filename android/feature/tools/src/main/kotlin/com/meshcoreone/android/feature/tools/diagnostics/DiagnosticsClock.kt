// AndroidOnly: WP-316 Injected wall clock and sleeper so the diagnostics state holders run on a virtual clock in tests.
package com.meshcoreone.android.feature.tools.diagnostics

import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Time source for the CLI countdown, noise-floor polling and output timestamps. The Swift view
 * models call `Date()` and `Task.sleep` directly; injecting both keeps the Kotlin holders testable
 * without real sleeps.
 */
interface DiagnosticsClock {
    fun now(): Instant

    /** Suspends for [duration]; cancellation must propagate as `CancellationException`. */
    suspend fun sleep(duration: Duration)

    companion object {
        val SYSTEM: DiagnosticsClock = object : DiagnosticsClock {
            override fun now(): Instant = Instant.now()
            override suspend fun sleep(duration: Duration) = delay(duration)
        }
    }
}
