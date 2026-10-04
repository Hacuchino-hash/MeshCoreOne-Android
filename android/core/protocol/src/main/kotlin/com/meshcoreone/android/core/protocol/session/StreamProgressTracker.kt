// PortedFrom: MeshCore/Sources/MeshCore/Session/StreamProgressTracker.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import kotlin.time.Duration

internal class StreamProgressTracker(private val clock: SessionClock) {
    data class Snapshot(val generation: Long, val elapsed: Duration)
    private val lock = Any()
    private val startedAt = clock.now
    private var generation = 0L

    fun markProgress() = synchronized(lock) { generation += 1 }
    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(generation, clock.now - startedAt) }
}
