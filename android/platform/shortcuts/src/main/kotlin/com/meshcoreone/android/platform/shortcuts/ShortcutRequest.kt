// AndroidOnly: WP-404 Strict, framework-free parsing of shortcut and share intents into typed requests.
package com.meshcoreone.android.platform.shortcuts

/** The only requests an external or launcher intent can express; none carries authorization. */
sealed interface ShortcutRequest {
    /** [targetId] and [message] are both optional: missing parts are collected in the foreground. */
    data class Send(val targetId: String?, val message: String?) : ShortcutRequest
    data object Status : ShortcutRequest
    data class Advert(val reach: AdvertReach) : ShortcutRequest
}

/** Platform-free view of an incoming intent so the parser is unit-testable on the JVM. */
data class RawShortcutIntent(
    val action: String?,
    val mimeType: String?,
    val text: CharSequence?,
    val targetId: String?,
    val reach: String?,
    val shortcutId: String?,
)

object ShortcutContract {
    const val ACTION_SEND_TARGET = "com.meshcoreone.android.platform.shortcuts.action.SEND_TARGET"
    const val ACTION_STATUS = "com.meshcoreone.android.platform.shortcuts.action.STATUS"
    const val ACTION_ADVERT = "com.meshcoreone.android.platform.shortcuts.action.ADVERT"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val EXTRA_TARGET_ID = "com.meshcoreone.android.platform.shortcuts.extra.TARGET_ID"
    const val EXTRA_REACH = "com.meshcoreone.android.platform.shortcuts.extra.REACH"
    const val SHARE_CATEGORY = "com.meshcoreone.android.platform.shortcuts.category.SEND_TARGET"
    const val SEND_SHORTCUT_PREFIX = "send:"
    const val ID_STATUS = "status"
    const val ID_SEND = "send"
    const val ID_ADVERT_ZERO_HOP = "advert.zerohop"
    const val ID_ADVERT_FLOOD = "advert.flood"
    const val MAX_SHARED_TEXT_CHARS = 4096
    const val MAX_ID_CHARS = 256
}

object ShortcutRequestParser {
    /** Returns null for anything unknown, malformed, oversized or blank so it is dropped, never guessed. */
    fun parse(raw: RawShortcutIntent): ShortcutRequest? = when (raw.action) {
        ShortcutContract.ACTION_STATUS -> ShortcutRequest.Status
        ShortcutContract.ACTION_ADVERT -> AdvertReach.fromRawValue(raw.reach)?.let { ShortcutRequest.Advert(it) }
        ShortcutContract.ACTION_SEND_TARGET -> boundedId(raw.targetId)?.let { ShortcutRequest.Send(it, null) }
        ShortcutContract.ACTION_SEND -> parseShare(raw)
        else -> null
    }

    private fun parseShare(raw: RawShortcutIntent): ShortcutRequest? {
        if (raw.mimeType?.lowercase() != "text/plain") return null
        val text = raw.text?.toString() ?: return null
        if (text.isBlank() || text.length > ShortcutContract.MAX_SHARED_TEXT_CHARS) return null
        val shortcut = raw.shortcutId
        val target = when {
            shortcut == null -> null
            shortcut.startsWith(ShortcutContract.SEND_SHORTCUT_PREFIX) ->
                boundedId(shortcut.removePrefix(ShortcutContract.SEND_SHORTCUT_PREFIX)) ?: return null
            else -> return null
        }
        return ShortcutRequest.Send(target, text)
    }

    private fun boundedId(id: String?): String? =
        id?.takeIf { it.isNotEmpty() && it.length <= ShortcutContract.MAX_ID_CHARS && TargetIdentity.parse(it) != null }
}
