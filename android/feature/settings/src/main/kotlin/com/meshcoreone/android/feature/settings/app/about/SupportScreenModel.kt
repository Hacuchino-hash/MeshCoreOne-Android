// PortedFrom: MC1/Views/Support/SupportDevelopmentView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Support/Sections/ThemesPurchaseSection.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: no StoreKit, purchase, tip, restore, refund or pending-approval behavior exists on Android.
package com.meshcoreone.android.feature.settings.app.about

import com.meshcoreone.android.core.designsystem.Theme
import com.meshcoreone.android.core.designsystem.ThemeRegistry

/** Content of the IAP-free support screen. Every registry theme is unlocked, so the themes section is the "all unlocked" card. */
data class SupportScreenModel(
    val themes: List<Theme>,
    val allThemesUnlocked: Boolean,
    val links: List<AboutRow.ExternalLink>,
) {
    companion object {
        /** [available] is what the user can apply; the card shows when it covers the whole registry. */
        fun create(
            available: List<Theme> = ThemeRegistry.allThemes,
            registry: List<Theme> = ThemeRegistry.allThemes,
        ) = SupportScreenModel(
            themes = available,
            allThemesUnlocked = available.map { it.id }.toSet().containsAll(registry.map { it.id }),
            links = AboutLinks.supportLinks,
        )
    }
}
