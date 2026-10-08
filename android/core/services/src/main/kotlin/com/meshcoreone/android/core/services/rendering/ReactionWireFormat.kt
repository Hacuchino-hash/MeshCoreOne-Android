// PortedFrom: MC1Services/Sources/MC1Services/Services/ReactionParser.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/MessageDTO+ReactionVisibility.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

import com.meshcoreone.android.core.contracts.domain.ParsedReaction
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.model.MessageStatus
import java.security.MessageDigest

/**
 * The slice of `ReactionParser` (WP-216) that WP-213 needs, ported locally because core:services cannot
 * see the internal copy in core:data: the reaction message hash used by [MessageLRUCache] keys, and the
 * PocketMesh channel/DM wire parsers behind [HiddenOutgoingReactionPredicate.SourceWireFormat].
 * Byte-for-byte identical to Swift; character tests use Swift `Character` (grapheme) semantics.
 */
internal object ReactionWireFormat {
    private const val CROCKFORD = "0123456789abcdefghjkmnpqrstvwxyz"
    private const val HASH_LENGTH = 8
    private const val HASH_BYTES = 5
    private const val TIMESTAMP_BYTES = 4
    private const val FIVE_BIT_MASK = 0x1FL

    /** `ReactionParser.generateMessageHash`: SHA-256(utf8(text) ‖ LE32(timestamp)), 5 bytes as Crockford Base32. */
    fun generateMessageHash(text: String, timestamp: UInt): String {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val input = textBytes.copyOf(textBytes.size + TIMESTAMP_BYTES)
        val value = timestamp.toLong()
        for (offset in 0 until TIMESTAMP_BYTES) input[textBytes.size + offset] = (value ushr (offset * 8)).toByte()
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        var bits = 0L
        for (index in 0 until HASH_BYTES) bits = (bits shl 8) or (digest[index].toLong() and 0xFF)
        return buildString(HASH_LENGTH) {
            for (shift in 35 downTo 0 step 5) append(CROCKFORD[((bits ushr shift) and FIVE_BIT_MASK).toInt()])
        }
    }

    /** `ReactionParser.parse`: `@[sender]emoji` or `emoji@[sender]`, newline, 8-character Crockford hash. */
    fun parse(text: String): ParsedReaction? {
        val (withoutHash, messageHash) = splitHash(text) ?: return null
        val characters = SwiftText.graphemes(withoutHash)
        val atBracket = indexOfMentionOpen(characters)
        if (atBracket < 0) return null
        val beforeMention = characters.subList(0, atBracket)
        val afterAtBracket = characters.subList(atBracket + 2, characters.size)
        val emoji: String
        val sender: String
        if (beforeMention.isEmpty()) {
            val close = afterAtBracket.indexOfFirst { SwiftText.characterEquals(it, "]") }
            if (close < 0) return null
            sender = afterAtBracket.subList(0, close).joinToString("")
            emoji = afterAtBracket.subList(close + 1, afterAtBracket.size).joinToString("")
        } else {
            emoji = beforeMention.joinToString("")
            if (afterAtBracket.isEmpty() || !SwiftText.characterEquals(afterAtBracket.last(), "]")) return null
            sender = afterAtBracket.subList(0, afterAtBracket.size - 1).joinToString("")
        }
        if (!isReactionEmoji(emoji) || sender.isEmpty()) return null
        return ParsedReaction(emoji = emoji, targetSender = sender, messageHash = messageHash)
    }

    /** `ReactionParser.parseDM`: `{emoji}\n{hash}` with no `@[` anywhere. Returns `(emoji, messageHash)`. */
    fun parseDM(text: String): Pair<String, String>? {
        if (indexOfMentionOpen(SwiftText.graphemes(text)) >= 0) return null
        val (emoji, messageHash) = splitHash(text) ?: return null
        if (!isReactionEmoji(emoji)) return null
        return emoji to messageHash
    }

    /**
     * Splits at the last newline `Character`. A trailing LF that is half of a CRLF grapheme is not a
     * newline `Character` in Swift; any earlier LF would leave the CRLF inside the hash, which can never
     * validate, so both cases are rejected here.
     */
    private fun splitHash(text: String): Pair<String, String>? {
        val newline = text.lastIndexOf('\n')
        if (newline < 0 || (newline > 0 && text[newline - 1] == '\r')) return null
        val rawHash = text.substring(newline + 1)
        if (rawHash.length != HASH_LENGTH || !rawHash.all(::isCrockfordCharacter)) return null
        return text.substring(0, newline) to rawHash.map(::normalizedCrockford).joinToString("")
    }

    /** Grapheme-aware `range(of: "@[")`: an `@` Character immediately followed by a `[` Character. */
    private fun indexOfMentionOpen(characters: List<String>): Int {
        for (index in 0 until characters.size - 1) {
            if (characters[index] == "@" && characters[index + 1] == "[") return index
        }
        return -1
    }

    private fun isReactionEmoji(value: String): Boolean {
        val characters = SwiftText.graphemes(value)
        return characters.size == 1 && CharacterEmojiDetection.isEmoji(characters[0])
    }

    private fun isCrockfordCharacter(character: Char): Boolean =
        character.code < 128 && (character.lowercaseChar() in CROCKFORD || character in "OoIiLl")

    private fun normalizedCrockford(character: Char): Char = when (character) {
        'O', 'o' -> '0'
        'I', 'i', 'L', 'l' -> '1'
        else -> character.lowercaseChar()
    }
}

/**
 * Port for `MessageDTO.isHiddenOutgoingReaction(isDM:)` (WP-216 owns `ReactionParser`). A sent outgoing
 * reaction renders as a badge, so the timeline hides the row; failed reactions stay visible for retry.
 * [SourceWireFormat] is the documented default and reproduces the Swift extension exactly with the
 * locally ported PocketMesh parsers.
 */
fun interface HiddenOutgoingReactionPredicate {
    fun isHiddenOutgoingReaction(message: MessageDTO, isDM: Boolean): Boolean

    object SourceWireFormat : HiddenOutgoingReactionPredicate {
        override fun isHiddenOutgoingReaction(message: MessageDTO, isDM: Boolean): Boolean {
            if (message.direction != MessageDirection.OUTGOING) return false
            val isReaction = if (isDM) ReactionWireFormat.parseDM(message.text) != null else ReactionWireFormat.parse(message.text) != null
            if (!isReaction) return false
            return message.status != MessageStatus.FAILED
        }
    }
}
