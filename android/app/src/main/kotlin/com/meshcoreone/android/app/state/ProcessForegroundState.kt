// PortedFrom: MC1/Services/AppStateProviderImpl.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.state

import com.meshcoreone.android.core.contracts.domain.AppStateProvider
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether the app is in the foreground (Swift `applicationState != .background`). Android reports it as "at least
 * one activity is started": the application forwards `onActivityStarted`/`onActivityStopped` here, so the answer
 * is a lock-free count and is safe to read from any thread. Configuration changes stop and restart the same
 * activity, so the count dips to zero only when the whole task is truly backgrounded.
 */
class ProcessForegroundState : AppStateProvider {
    private val startedActivities = AtomicInteger(0)

    val isForeground: Boolean get() = startedActivities.get() > 0

    override suspend fun isInForeground(): Boolean = isForeground

    /** Returns true when this start moved the process from background to foreground. */
    fun activityStarted(): Boolean = startedActivities.incrementAndGet() == 1

    /** Returns true when this stop moved the process from foreground to background. */
    fun activityStopped(): Boolean {
        while (true) {
            val current = startedActivities.get()
            if (current == 0) return false
            if (startedActivities.compareAndSet(current, current - 1)) return current == 1
        }
    }
}
