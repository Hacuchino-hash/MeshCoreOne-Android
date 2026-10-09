// PortedFrom: MC1/Views/Chats/Sheets/JoinPrivateChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/ChannelOptionsSheet.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Utilities/HashtagUtilities.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.HashtagLinks
import java.util.Locale

/** Pure input rules shared by the channel create/join forms. */
object ChannelInput {
    /** Caps [value] to [ProtocolLimits.MAX_USABLE_NAME_BYTES] UTF-8 bytes on a character boundary (`utf8Prefix`). */
    fun truncatedName(value: String): String {
        if (value.toByteArray(Charsets.UTF_8).size <= ProtocolLimits.MAX_USABLE_NAME_BYTES) return value
        val capped = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > ProtocolLimits.MAX_USABLE_NAME_BYTES) break
            capped.appendCodePoint(codePoint)
            bytes += size
            index += Character.charCount(codePoint)
        }
        return capped.toString()
    }

    private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** Uppercases and drops every non-hex character as the user types the secret key. */
    fun sanitizedSecretHex(value: String): String = value.uppercase(Locale.ROOT).filter(::isHexDigit)

    /** True for exactly [ProtocolLimits.CHANNEL_SECRET_SIZE] bytes of hex (spaces tolerated). */
    fun isValidSecretHex(value: String): Boolean {
        val cleaned = value.replace(" ", "").uppercase(Locale.ROOT)
        return cleaned.length == ProtocolLimits.CHANNEL_SECRET_SIZE * 2 && cleaned.all(::isHexDigit)
    }

    fun secretFromHex(value: String): Bytes? = Bytes.parseHex(value)

    /** Lowercases and keeps `[a-z0-9-]`, then drops leading hyphens (`sanitizeHashtagNameInput`). */
    fun sanitizedHashtagName(input: String): String =
        input.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.trimStart('-')

    fun isValidHashtagName(name: String): Boolean = HashtagLinks.isValidHashtagName(name)

    /** Slots 1 until [maxChannels] that no existing channel uses; slot 0 is reserved for the public channel. */
    fun availableSlots(maxChannels: Int, usedSlots: Set<UByte>): List<UByte> =
        if (maxChannels > 1) (1 until maxChannels).map { it.toUByte() }.filter { it !in usedSlots } else emptyList()

    /** First free slot the forms preselect (`availableSlots.first ?? 1`). */
    fun defaultSlot(availableSlots: List<UByte>): UByte = availableSlots.firstOrNull() ?: 1u
}
