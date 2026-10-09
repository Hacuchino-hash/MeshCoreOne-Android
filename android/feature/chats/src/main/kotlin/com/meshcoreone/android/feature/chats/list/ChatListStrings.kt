// AndroidOnly: WP-306 Feature-owned text seam so ported display rules stay JVM-testable without Android resources.
package com.meshcoreone.android.feature.chats.list

/**
 * Localized strings the pure list/header logic needs. The Compose layer supplies a resource-backed
 * implementation; JVM tests supply deterministic ones.
 */
interface ChatListStrings {
    fun channelDefaultName(index: Int): String
    fun floodRouting(): String
    fun directHops(hops: Long): String
    fun headerRegion(region: String): String
    fun scopedDefault(region: String): String
}
