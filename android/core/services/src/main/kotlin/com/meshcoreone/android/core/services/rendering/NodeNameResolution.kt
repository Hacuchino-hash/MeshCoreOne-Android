// PortedFrom: MC1Services/Sources/MC1Services/Models/Rendering/NodeNameResolution.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

/** Whether a name resolution is an exact public-key match, a proximity fallback, or unresolved. */
enum class NodeNameMatchKind { EXACT, FALLBACK, UNRESOLVED }

/** Resolved sender display name paired with the confidence level of the match. */
data class NodeNameResolution(
    val displayName: String,
    val matchKind: NodeNameMatchKind,
    /** Nickname for a channel sender matched by name only (identity unverified); nil otherwise. */
    val unverifiedNickname: String? = null,
) {
    val isFallback: Boolean get() = matchKind == NodeNameMatchKind.FALLBACK
}
