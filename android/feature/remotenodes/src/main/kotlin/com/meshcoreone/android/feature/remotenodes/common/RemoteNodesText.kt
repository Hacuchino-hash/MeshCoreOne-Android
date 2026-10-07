// AndroidOnly: WP-313 Unresolved user-facing text carried by remote-node state holders until Compose resolves it.
package com.meshcoreone.android.feature.remotenodes.common

/**
 * Swift's view models store resolved `String`s (`L10n...` and `error.userFacingMessage`). Android state
 * holders have no `Resources`, so they carry the text unresolved: a string resource id plus format
 * arguments, verbatim firmware text, or the failure whose `userFacingMessage` (WP-304, core:ui) the
 * screen resolves. Equality is structural so tests can assert the exact Swift branch taken.
 */
sealed interface RemoteNodesText {
    /** A localized string resource ([id] from `AppRemoteNodesStrings`) with printf-style [args]. */
    data class Resource(val id: Int, val args: List<Any> = emptyList()) : RemoteNodesText

    /** Text shown exactly as received (firmware CLI replies, node names). */
    data class Verbatim(val text: String) : RemoteNodesText

    /** A thrown failure; the screen renders it with the shared `userFacingMessage` mapping. */
    data class Failure(val error: Throwable) : RemoteNodesText

    companion object {
        fun resource(id: Int, vararg args: Any): RemoteNodesText = Resource(id, args.toList())
    }
}
