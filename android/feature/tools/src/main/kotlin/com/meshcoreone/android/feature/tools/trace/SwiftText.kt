// AndroidOnly: WP-314 Foundation String semantics (Character splitting, whitespace trimming, hex digits) measured by swiftc oracles.
package com.meshcoreone.android.feature.tools.trace

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * The Swift/Foundation text rules the trace code relies on. Each rule was measured with a
 * `swiftc` oracle (see `docs/android/evidence/WP-314/`); none relies on `\d`, `\s` or
 * `UNICODE_CHARACTER_CLASS`.
 */
internal object SwiftText {
    private val graphemePattern: Pattern = Pattern.compile("\\X")

    /** `CharacterSet.whitespaces`: general category Zs plus U+0009 and U+200B (enumerated by oracle). */
    private val whitespaceCodePoints: Set<Int> = buildSet {
        add(0x0009); add(0x0020); add(0x00A0); add(0x1680)
        for (codePoint in 0x2000..0x200B) add(codePoint)
        add(0x202F); add(0x205F); add(0x3000)
    }

    fun isWhitespace(codePoint: Int): Boolean = codePoint in whitespaceCodePoints

    /** `trimmingCharacters(in: .whitespaces)`. Newlines and U+FEFF are not trimmed. */
    fun trimWhitespaces(text: String): String {
        var start = 0
        var end = text.length
        while (start < end) {
            val codePoint = text.codePointAt(start)
            if (!isWhitespace(codePoint)) break
            start += Character.charCount(codePoint)
        }
        while (end > start) {
            val codePoint = text.codePointBefore(end)
            if (!isWhitespace(codePoint)) break
            end -= Character.charCount(codePoint)
        }
        return text.substring(start, end)
    }

    /** Extended grapheme clusters: Swift `Character`s. */
    fun characters(text: String): List<String> {
        val matcher = graphemePattern.matcher(text)
        val result = ArrayList<String>()
        while (matcher.find()) result += matcher.group()
        return result
    }

    /**
     * `split(separator: ",")` with empty pieces omitted. A comma followed by a combining mark is a
     * different Character, so it does not split.
     */
    fun splitOnComma(text: String): List<String> {
        val pieces = ArrayList<String>()
        val current = StringBuilder()
        for (character in characters(text)) {
            if (character == ",") {
                if (current.isNotEmpty()) pieces += current.toString()
                current.setLength(0)
            } else {
                current.append(character)
            }
        }
        if (current.isNotEmpty()) pieces += current.toString()
        return pieces
    }

    /** `Character.isHexDigit`: ASCII hex digits and their fullwidth forms, as single scalars only. */
    fun isHexDigit(character: String): Boolean {
        if (character.isEmpty() || Character.charCount(character.codePointAt(0)) != character.length) return false
        val codePoint = character.codePointAt(0)
        return asciiHexValue(codePoint) != null || codePoint in 0xFF10..0xFF19 ||
            codePoint in 0xFF21..0xFF26 || codePoint in 0xFF41..0xFF46
    }

    /** ASCII hex value; `UInt8(_, radix: 16)` rejects the fullwidth forms. */
    fun asciiHexValue(codePoint: Int): Int? = when (codePoint) {
        in '0'.code..'9'.code -> codePoint - '0'.code
        in 'a'.code..'f'.code -> codePoint - 'a'.code + 10
        in 'A'.code..'F'.code -> codePoint - 'A'.code + 10
        else -> null
    }

    /** `String.uppercased()`: full, locale-independent case mapping (`ß` becomes `SS`). */
    fun uppercased(text: String): String = text.uppercase(Locale.ROOT)

    /** Swift `String ==` is canonical equivalence; NFC gives the same identity for set membership. */
    fun canonicalKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
}
