// AndroidOnly: WP-217 synchronous boolean preference port standing in for UserDefaults; WP-303 binds core:datastore.
package com.meshcoreone.android.core.services.simulator

/**
 * The two `UserDefaults` calls `DemoModeManager` makes. Both are synchronous, as in Swift: the manager reads
 * its initial values in its constructor and writes through on every assignment. The production binding must
 * therefore serve reads from an already-loaded snapshot and may persist writes asynchronously.
 */
interface DemoModeDefaults {
    /** `UserDefaults.bool(forKey:)`: the stored value, or `false` when the key is absent. */
    fun bool(forKey: String): Boolean

    /** `UserDefaults.set(_:forKey:)` for a `Bool`. */
    fun set(value: Boolean, forKey: String)
}

/**
 * Process-local [DemoModeDefaults], the equivalent of a fresh `UserDefaults(suiteName:)` suite. Used by tests
 * and previews; it does not survive the process.
 */
class InMemoryDemoModeDefaults(initial: Map<String, Boolean> = emptyMap()) : DemoModeDefaults {
    private val lock = Any()
    private var values: Map<String, Boolean> = initial.toMap()

    override fun bool(forKey: String): Boolean = synchronized(lock) { values[forKey] ?: false }

    override fun set(value: Boolean, forKey: String) {
        synchronized(lock) { values = values + (forKey to value) }
    }

    /** Whether [key] has ever been written, distinguishing an explicit `false` from absence. */
    fun contains(key: String): Boolean = synchronized(lock) { key in values }
}
