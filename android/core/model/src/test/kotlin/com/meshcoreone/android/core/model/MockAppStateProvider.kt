// PortedFrom: MC1Services/Tests/MC1ServicesTests/Mocks/MockAppStateProvider.swift@db14559b39d32322b06477c6ae676112f583db50
// Test-only suspension gate is cancellation-aware; never a production foreground provider.
package com.meshcoreone.android.core.model

import com.meshcoreone.android.core.contracts.domain.AppStateProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MockAppStateProvider(private var foreground: Boolean = true) : AppStateProvider {
    private val mutex = Mutex()
    private var gate: CompletableDeferred<Unit>? = null
    var isWaitingOnForegroundCheck: Boolean = false
        private set

    override suspend fun isInForeground(): Boolean {
        val wait = mutex.withLock { gate.also { isWaitingOnForegroundCheck = it != null } }
        try {
            wait?.await()
            return mutex.withLock { foreground }
        } finally {
            mutex.withLock { isWaitingOnForegroundCheck = false }
        }
    }
    suspend fun setIsInForeground(value: Boolean) = mutex.withLock { foreground = value }
    suspend fun hangForegroundChecks() = mutex.withLock {
        check(gate == null) { "Foreground gate already armed" }
        gate = CompletableDeferred()
    }
    suspend fun releaseForegroundCheck() = mutex.withLock {
        gate?.complete(Unit)
        gate = null
    }
}
