// PortedFrom: MC1/Settings.bundle/Acknowledgements.plist@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: LICENSE@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MeshCore/LICENSE@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: Apple-only entries (SwiftDocCPlugin, SymbolKit, MessagingUI) are not listed; license text comes from LicenseTextSource.
package com.meshcoreone.android.feature.settings.app.about

enum class LicenseKind(val spdx: String) {
    GPL_3("GPL-3.0"), MIT("MIT"), APACHE_2("Apache-2.0"), BSD_2("BSD-2-Clause"), CC0("CC0-1.0"),
}

data class LicenseEntry(val id: String, val name: String, val kind: LicenseKind)

/** Supplies the full license text for an entry id (the app reads bundled assets); `null` when it is unavailable. */
fun interface LicenseTextSource {
    suspend fun text(id: String): String?
}

object LicenseCatalog {
    /** The app's own GPLv3 license and the MIT-licensed MeshCore library come first, then bundled third-party data/assets. */
    val entries: List<LicenseEntry> = listOf(
        LicenseEntry("meshcore-one", "MeshCore One", LicenseKind.GPL_3),
        LicenseEntry("meshcore", "MeshCore", LicenseKind.MIT),
        LicenseEntry("emojibase", "Emojibase", LicenseKind.APACHE_2),
        LicenseEntry("urlhaus-filter", "urlhaus-filter", LicenseKind.CC0),
        LicenseEntry("catppuccin", "Catppuccin", LicenseKind.MIT),
        LicenseEntry("nord", "Nord", LicenseKind.MIT),
        LicenseEntry("solarized", "Solarized", LicenseKind.MIT),
        LicenseEntry("maplibre", "MapLibre", LicenseKind.BSD_2),
    )

    /** Settings.bundle packages with no Android counterpart; recorded so the omission is deliberate. */
    val appleOnlyOmitted: List<String> = listOf("SwiftDocCPlugin", "SymbolKit", "MessagingUI")

    fun entry(id: String): LicenseEntry? = entries.firstOrNull { it.id == id }
}

/** Resolved text for the detail pane; [Missing] is shown honestly instead of fabricating license text. */
sealed interface LicenseText {
    data class Loaded(val entry: LicenseEntry, val text: String) : LicenseText
    data class Missing(val entry: LicenseEntry) : LicenseText
}

suspend fun LicenseTextSource.resolve(entry: LicenseEntry): LicenseText {
    val text = try { text(entry.id) } catch (failure: java.io.IOException) { null }
    return if (text.isNullOrBlank()) LicenseText.Missing(entry) else LicenseText.Loaded(entry, text)
}
