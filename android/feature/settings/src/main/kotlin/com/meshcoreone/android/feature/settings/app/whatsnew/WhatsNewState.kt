// PortedFrom: MC1/State/WhatsNewState.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: the UserDefaults baseline sits behind WhatsNewBaselineStore (the app binds DataStore/SharedPreferences).
package com.meshcoreone.android.feature.settings.app.whatsnew

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Device-local "last shown What's New version" baseline. */
interface WhatsNewBaselineStore {
    var lastShownVersion: String?
}

/**
 * Owns the baseline and decides once per launch whether to present the sheet. [currentVersion] is the
 * running marketing version (`versionName`).
 */
class WhatsNewState(private val baseline: WhatsNewBaselineStore, private val currentVersion: String) {
    private val pending = MutableStateFlow<WhatsNewRelease?>(null)

    /** The release to present, set by [evaluate] and cleared by [markShown]. */
    val pendingRelease: StateFlow<WhatsNewRelease?> = pending

    /**
     * Runs the resolver once at launch. A show defers the baseline to [markShown]; a suppress finalizes it
     * now so the sheet never re-appears. An unparseable version fails closed (neither shown nor recorded);
     * screenshot mode is skipped.
     */
    fun evaluate(isOnboarded: Boolean, isScreenshotMode: Boolean, catalog: List<WhatsNewRelease> = WhatsNewCatalog.releases) {
        if (isScreenshotMode) return
        val current = WhatsNewVersion.parse(currentVersion) ?: return
        val release = resolve(current, baseline.lastShownVersion, isOnboarded, catalog)
        if (release != null) pending.value = release else baseline.lastShownVersion = currentVersion
    }

    /** Persists the baseline for the running version and clears the pending release (Continue and swipe-dismiss). */
    fun markShown() {
        baseline.lastShownVersion = currentVersion
        pending.value = null
    }

    companion object {
        /** The launch-time show/suppress decision as a pure function. */
        fun resolve(
            current: WhatsNewVersion,
            baselineString: String?,
            isOnboarded: Boolean,
            catalog: List<WhatsNewRelease>,
        ): WhatsNewRelease? {
            val release = catalog.firstOrNull { it.version == current }?.takeIf { it.items.isNotEmpty() } ?: return null
            // No baseline: an upgrader sees the notes, a brand-new install (still mid-onboarding) does not.
            if (baselineString == null) return if (isOnboarded) release else null
            val baseline = WhatsNewVersion.parse(baselineString) ?: return null
            return if (current > baseline) release else null
        }
    }
}
