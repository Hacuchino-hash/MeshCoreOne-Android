// PortedFrom: MC1/Utilities/DemoModeManager.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.simulator

import java.util.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable demo-mode flags, replacing the Swift `@Observable` properties. */
data class DemoModeState(val isUnlocked: Boolean, val isEnabled: Boolean)

/**
 * Demo-mode unlock/enable flags persisted in [defaults] under the `AppStorageKey` raw values
 * `isDemoModeUnlocked` and `isDemoModeEnabled`.
 *
 * Swift observes the properties via `@Observable` on the main actor. Here the flags are lock-confined, every
 * assignment persists the value (like Swift's `didSet`, including when it is unchanged) and then publishes it
 * on [state]. The defaults port is synchronous, so nothing suspends while the lock is held.
 */
class DemoModeManager(private val defaults: DemoModeDefaults) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(
        DemoModeState(
            isUnlocked = defaults.bool(IS_DEMO_MODE_UNLOCKED_KEY),
            isEnabled = defaults.bool(IS_DEMO_MODE_ENABLED_KEY),
        ),
    )

    /** Current flags, for observers (the Swift `@Observable` surface). */
    val state: StateFlow<DemoModeState> = mutableState.asStateFlow()

    var isUnlocked: Boolean
        get() = mutableState.value.isUnlocked
        set(value) = synchronized(lock) {
            defaults.set(value, IS_DEMO_MODE_UNLOCKED_KEY)
            mutableState.value = mutableState.value.copy(isUnlocked = value)
        }

    var isEnabled: Boolean
        get() = mutableState.value.isEnabled
        set(value) = synchronized(lock) {
            defaults.set(value, IS_DEMO_MODE_ENABLED_KEY)
            mutableState.value = mutableState.value.copy(isEnabled = value)
        }

    /**
     * Unlocks and enables demo mode. Both values are written through before a single state publish, so observers
     * never see the unlocked-but-disabled midpoint (SwiftUI coalesces Swift's two assignments the same way).
     */
    fun unlock() {
        logger.info("Demo mode unlocked and enabled")
        synchronized(lock) {
            defaults.set(true, IS_DEMO_MODE_UNLOCKED_KEY)
            defaults.set(true, IS_DEMO_MODE_ENABLED_KEY)
            mutableState.value = DemoModeState(isUnlocked = true, isEnabled = true)
        }
    }

    companion object {
        /** `AppStorageKey.isDemoModeUnlocked.rawValue`. */
        const val IS_DEMO_MODE_UNLOCKED_KEY = "isDemoModeUnlocked"

        /** `AppStorageKey.isDemoModeEnabled.rawValue`. */
        const val IS_DEMO_MODE_ENABLED_KEY = "isDemoModeEnabled"

        private val logger: Logger = Logger.getLogger("com.mc1.DemoMode")
        private val sharedLock = Any()
        private var standardDefaults: DemoModeDefaults? = null
        private var sharedInstance: DemoModeManager? = null

        /**
         * Binds the process-wide store behind [shared] (Swift's `UserDefaults.standard`). Must run before the
         * first [shared] access; installing the same instance again is a no-op, a different one is rejected.
         */
        fun installStandardDefaults(defaults: DemoModeDefaults) {
            synchronized(sharedLock) {
                val current = standardDefaults
                check(current == null || current === defaults) { "Demo-mode standard defaults are already installed" }
                standardDefaults = defaults
            }
        }

        /** The process-wide manager. Fails fast if [installStandardDefaults] has not run yet. */
        val shared: DemoModeManager
            get() = synchronized(sharedLock) {
                sharedInstance ?: run {
                    val defaults = checkNotNull(standardDefaults) {
                        "DemoModeManager.shared accessed before installStandardDefaults"
                    }
                    DemoModeManager(defaults).also { sharedInstance = it }
                }
            }
    }
}
