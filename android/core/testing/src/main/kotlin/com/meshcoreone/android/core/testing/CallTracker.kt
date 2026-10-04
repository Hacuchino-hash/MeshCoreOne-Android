// PortedFrom: MeshCore/Tests/MeshCoreTestSupport/CallTracker.swift@db14559b39d32322b06477c6ae676112f583db50
// SPDX-License-Identifier: MIT
package com.meshcoreone.android.core.testing

import java.util.concurrent.atomic.AtomicLong

class CallTracker {
    private val count = AtomicLong()

    val wasCalled: Boolean
        get() = count.get() > 0

    val callCount: Long
        get() = count.get()

    fun markCalled() {
        count.updateAndGet { Math.addExact(it, 1L) }
    }
}
