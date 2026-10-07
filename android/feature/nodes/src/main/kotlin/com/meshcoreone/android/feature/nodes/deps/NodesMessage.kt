// AndroidOnly: WP-311 Typed user-facing text so state holders stay Context-free; Compose resolves it via core:l10n.
package com.meshcoreone.android.feature.nodes.deps

/**
 * User-facing text produced by the nodes logic. Swift built `String`s from `L10n` at the call site;
 * Android state holders keep the resource reference and arguments so resolution happens in the UI
 * with the current configuration.
 */
sealed interface NodesMessage {
    /** Already user-facing text (for example `Error.userFacingMessage` from WP-304). */
    data class Text(val value: String) : NodesMessage

    /** A `core:l10n` string resource with printf arguments (nested messages resolve first). */
    data class Resource(val id: Int, val args: List<Any> = emptyList()) : NodesMessage

    /** Parts joined by [separator] (`[String].joined(separator:)`). */
    data class Joined(val parts: List<NodesMessage>, val separator: String) : NodesMessage

    companion object {
        fun res(id: Int, vararg args: Any): NodesMessage = Resource(id, args.toList())
    }
}
