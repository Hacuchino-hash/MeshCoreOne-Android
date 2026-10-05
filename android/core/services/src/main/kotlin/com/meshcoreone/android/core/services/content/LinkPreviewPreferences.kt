// PortedFrom: MC1/Models/LinkPreviewPreferences.swift@db14559b39d32322b06477c6ae676112f583db50
// Pure policy logic only - the real `UserDefaults`-equivalent storage is Android DataStore
// (core:datastore's PreferenceStore), reached through a narrow adapter outside this pure-JVM
// module (see docs/android/deviations/WP-218.md). `LinkPreviewPreferencesSource` is the narrow
// producer-role port this file depends on; it is defined locally (not in core:contracts) because
// WP-218's write_paths do not admit contracts/ edits without a separate amendment.
package com.meshcoreone.android.core.services.content

/**
 * Narrow read/write port for the three booleans [LinkPreviewPreferences] needs. A future
 * native adapter (outside this module) implements this against the real DataStore-backed
 * preference store; this module only depends on the shape of the port, not its implementation.
 */
interface LinkPreviewPreferencesSource {
    var previewsEnabled: Boolean
    var autoResolveDM: Boolean
    var autoResolveChannels: Boolean
}

/**
 * In-memory [LinkPreviewPreferencesSource] with the Swift defaults (previews off, both
 * auto-resolve flags on) - usable directly in tests and as a reference implementation for the
 * eventual DataStore-backed adapter.
 */
class InMemoryLinkPreviewPreferencesSource(
    override var previewsEnabled: Boolean = false,
    override var autoResolveDM: Boolean = true,
    override var autoResolveChannels: Boolean = true,
) : LinkPreviewPreferencesSource

/**
 * User preferences for link preview behavior. Mirrors the Swift `LinkPreviewPreferences`
 * struct's policy exactly; only the storage mechanism differs (DataStore vs. `UserDefaults`,
 * both behind [LinkPreviewPreferencesSource]).
 */
class LinkPreviewPreferences(private val source: LinkPreviewPreferencesSource) {
    var previewsEnabled: Boolean
        get() = source.previewsEnabled
        set(value) { source.previewsEnabled = value }

    var autoResolveDM: Boolean
        get() = source.autoResolveDM
        set(value) { source.autoResolveDM = value }

    var autoResolveChannels: Boolean
        get() = source.autoResolveChannels
        set(value) { source.autoResolveChannels = value }

    /** Whether previews should be shown at all. */
    val shouldShowPreview: Boolean
        get() = previewsEnabled

    /** Whether to auto-resolve based on message type. */
    fun shouldAutoResolve(isChannelMessage: Boolean): Boolean {
        if (!previewsEnabled) return false
        return if (isChannelMessage) autoResolveChannels else autoResolveDM
    }
}
