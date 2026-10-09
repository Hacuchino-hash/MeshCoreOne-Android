// AndroidOnly: WP-308 Swift String/Character semantics the composer needs (type-placement: local mirror of the internal core:services SwiftText, which feature modules may not import).
package com.meshcoreone.android.feature.chats.composer

import java.util.regex.Pattern

/**
 * Swift `String`/`Character` behaviors over JVM UTF-16 strings. Expected values are pinned against
 * the swiftc oracle in docs/android/evidence/WP-308/oracle.
 *
 * - [utf8Length] is `text.utf8.count`. A lone surrogate cannot exist in a Swift `String` (it would be
 *   repaired to U+FFFD, 3 bytes), so it is counted as 3 here rather than as the JDK's `?` (1 byte).
 * - [graphemes] segments like Swift `Character` using the JDK `\X` matcher.
 * - [isWhitespaceCharacter] is `Character.isWhitespace` (first scalar has `White_Space`).
 * - [trimmed] is `trimmingCharacters(in: .whitespacesAndNewlines)`, which also trims U+200B.
 */
internal object ComposerText {
    private val GRAPHEME: Pattern = Pattern.compile("\\X")
    private const val ZERO_WIDTH_SPACE = 0x200B
    private const val REPLACEMENT_CHARACTER_UTF8_BYTES = 3

    fun utf8Length(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char.code < 0x80 -> { bytes += 1; index += 1 }
                char.code < 0x800 -> { bytes += 2; index += 1 }
                Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                    bytes += 4; index += 2
                }
                Character.isSurrogate(char) -> { bytes += REPLACEMENT_CHARACTER_UTF8_BYTES; index += 1 }
                else -> { bytes += 3; index += 1 }
            }
        }
        return bytes
    }

    fun graphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val matcher = GRAPHEME.matcher(text)
        val result = ArrayList<String>()
        while (matcher.find()) result.add(matcher.group())
        return result
    }

    fun isWhiteSpaceScalar(codePoint: Int): Boolean = when (codePoint) {
        in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }

    private fun isTrimmable(codePoint: Int): Boolean = isWhiteSpaceScalar(codePoint) || codePoint == ZERO_WIDTH_SPACE

    fun isWhitespaceCharacter(character: String): Boolean =
        character.isNotEmpty() && isWhiteSpaceScalar(character.codePointAt(0))

    fun trimmed(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && isTrimmable(text.codePointAt(start))) start += Character.charCount(text.codePointAt(start))
        while (end > start && isTrimmable(text.codePointBefore(end))) end -= Character.charCount(text.codePointBefore(end))
        return text.substring(start, end)
    }

    fun isBlank(text: String): Boolean = trimmed(text).isEmpty()
}
