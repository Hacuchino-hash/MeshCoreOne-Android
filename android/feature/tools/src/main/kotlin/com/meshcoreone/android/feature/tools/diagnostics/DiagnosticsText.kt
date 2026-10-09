// AndroidOnly: WP-316 Resource-id string port so the non-UI diagnostics logic resolves core:l10n strings without a Context.
package com.meshcoreone.android.feature.tools.diagnostics

import android.content.res.Resources
import java.util.Locale

/**
 * Localized text for the diagnostics state holders. Callers pass the generated core:l10n ids
 * (`AppToolsStrings`, `AppRemoteNodesStrings`, `AppContactsStrings`), so the logic layer keeps the
 * Swift `L10n.*` keys while staying free of Android `Context`.
 */
interface DiagnosticsText {
    val locale: Locale

    /** An unformatted string (`formatted="false"` resources are never passed through a formatter). */
    fun string(id: Int): String

    /** A printf-style resource formatted with [locale], matching core:l10n `L10nFormatting.string`. */
    fun format(id: Int, vararg args: Any): String

    /** Foundation `ListFormatter.localizedString(byJoining:)` equivalent for [locale]. */
    fun joinList(items: List<String>): String
}

/** Production text backed by Android resources; the UI layer constructs it from its `Context`. */
class ResourcesDiagnosticsText(private val resources: Resources) : DiagnosticsText {
    override val locale: Locale get() = resources.configuration.locales[0]

    override fun string(id: Int): String = resources.getString(id)

    override fun format(id: Int, vararg args: Any): String =
        String.format(locale, resources.getString(id), *args)

    override fun joinList(items: List<String>): String =
        android.icu.text.ListFormatter.getInstance(locale).format(items)
}
