// PortedFrom: MC1/Tips/DeviceMenuTip.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Tips/LiveActivityTip.swift@db14559b39d32322b06477c6ae676112f583db50
// AndroidOnly: WP-304 Real atomic process-owned DataStore claims; no notification permission or Live Update eligibility claim.
package com.meshcoreone.android.core.ui

import com.meshcoreone.android.core.datastore.PreferenceKey
import com.meshcoreone.android.core.datastore.PreferenceStore
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings

enum class SharedTip(
    val sourceId: String,
    val eventId: String,
    val titleResource: Int,
    val messageResource: Int,
) {
    DEVICE_MENU("DeviceMenuTip", "hasCompletedOnboarding",
        AppChatsStrings.chatsTipDeviceMenuTitle, AppChatsStrings.chatsTipDeviceMenuMessage),
    LIVE_STATUS("LiveActivityTip", "radioConnected",
        AppSettingsStrings.liveActivityTipTitle, AppSettingsStrings.liveActivityTipMessage);

    internal val eventKey get() = PreferenceKey.BooleanKey("tips.event.$eventId", false)
    internal val displayedKey get() = PreferenceKey.BooleanKey("tips.$sourceId.displayed", false)
}

data class TipHostState(
    val deviceMenuVisible: Boolean,
    val connectedDevicePresent: Boolean,
    val liveStatusPresented: Boolean,
    val overlayQuiescent: Boolean = true,
    val taskInProgress: Boolean = false,
) {
    fun eligible(tip: SharedTip): Boolean = overlayQuiescent && !taskInProgress && when (tip) {
        SharedTip.DEVICE_MENU -> deviceMenuVisible && connectedDevicePresent
        SharedTip.LIVE_STATUS -> liveStatusPresented
    }
}

class ShowOnceTips(private val preferences: PreferenceStore) {
    suspend fun donateCompletedOnboarding() = donate(SharedTip.DEVICE_MENU)
    suspend fun donateRadioConnected() = donate(SharedTip.LIVE_STATUS)

    private suspend fun donate(tip: SharedTip) {
        preferences.update { this[tip.eventKey] = true }
    }

    suspend fun claim(tip: SharedTip, currentHost: () -> TipHostState): Boolean {
        var claimed = false
        preferences.update {
            val stored = snapshot
            if (currentHost().eligible(tip) && stored[tip.eventKey] && !stored[tip.displayedKey]) {
                this[tip.displayedKey] = true
                claimed = true
            }
        }
        return claimed
    }

    suspend fun hasDisplayed(tip: SharedTip): Boolean = preferences.get(tip.displayedKey)
}
