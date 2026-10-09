// AndroidOnly: WP-313 Swift Foundation string/number conversions (Int(_:), Double(_:), CharacterSet trims, Character counts) for the remote-node CLI parsers.
// Feature-local mirror of the WP-210 core:services copy (PR #50 blob aad6807d); features may not depend on core:services. See docs/android/deviations/WP-313.md.
package com.meshcoreone.android.feature.remotenodes.cli

import java.util.regex.Pattern

/**
 * The CLI parsers lean on Swift `String` semantics that differ from Kotlin defaults: `Int(_:)` and
 * `Double(_:)` reject surrounding whitespace, Unicode digits and Java-only suffixes ("1.5d"), and
 * `CharacterSet.whitespaces` excludes newlines. These helpers pin the Swift behavior.
 */
internal object RemoteSwiftText {
    /**
     * Unicode decimal digit (Swift Regex `\d`), spelled as a general category so it means the same on the
     * JVM and on Android's ICU-backed `java.util.regex`, where UNICODE_CHARACTER_CLASS is not portable.
     */
    const val UNICODE_DIGIT: String = "\\p{Nd}"

    /** Unicode White_Space (Swift Regex `\s`), enumerated for the same portability reason. */
    const val UNICODE_WHITESPACE: String =
        "[\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"

    private val INTEGER = Regex("[+-]?[0-9]+")
    private val DECIMAL = Regex("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val HEX_FLOAT = Regex("([+-]?)0[xX]((?:[0-9a-fA-F]+\\.?[0-9a-fA-F]*|\\.[0-9a-fA-F]+))(?:[pP]([+-]?[0-9]+))?")
    private val INFINITY = Regex("([+-]?)(?:inf|infinity)", RegexOption.IGNORE_CASE)
    private val NAN = Regex("[+-]?nan(?:\\([0-9A-Za-z_]*\\))?", RegexOption.IGNORE_CASE)
    private val GRAPHEME: Pattern = Pattern.compile("\\X")

    /** Swift `Int(_:)` (64-bit): optional sign then ASCII digits only; overflow yields null. */
    fun int(text: String): Long? = if (INTEGER.matches(text)) text.toLongOrNull() else null

    /** Swift `Double(_:)`: decimal, hexadecimal-float, inf/infinity and nan spellings, nothing else. */
    fun double(text: String): Double? {
        if (DECIMAL.matches(text)) return text.toDouble()
        HEX_FLOAT.matchEntire(text)?.let { match ->
            val (sign, mantissa, exponent) = match.destructured
            return java.lang.Double.parseDouble("${sign}0x${mantissa}p${exponent.ifEmpty { "0" }}")
        }
        INFINITY.matchEntire(text)?.let { match ->
            return if (match.groupValues[1] == "-") Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        }
        return if (NAN.matches(text)) Double.NaN else null
    }

    /** `trimmingCharacters(in: .whitespacesAndNewlines)`. */
    fun trimWhitespacesAndNewlines(text: String): String = text.trim(::isWhitespaceOrNewline)

    /** `trimmingCharacters(in: .whitespaces)`: tab and space separators, never newlines. */
    fun trimWhitespaces(text: String): String = text.trim(::isWhitespace)

    /** The first [limit] extended grapheme clusters (Swift `Character`s) of [text]. */
    fun leadingCharacters(text: String, limit: Int): List<String> {
        val matcher = GRAPHEME.matcher(text)
        val characters = ArrayList<String>(limit)
        while (characters.size < limit && matcher.find()) characters += matcher.group()
        return characters
    }

    /** Swift `String.count`: the number of extended grapheme clusters in [text]. */
    fun characterCount(text: String): Int {
        val matcher = GRAPHEME.matcher(text)
        var count = 0
        while (matcher.find()) count += 1
        return count
    }

    private fun isWhitespace(character: Char): Boolean =
        character == '\t' || Character.getType(character) == Character.SPACE_SEPARATOR.toInt()

    private fun isWhitespaceOrNewline(character: Char): Boolean = isWhitespace(character) ||
        character in '\n'..'\r' || character == '\u0085' ||
        Character.getType(character) == Character.LINE_SEPARATOR.toInt() ||
        Character.getType(character) == Character.PARAGRAPH_SEPARATOR.toInt()
}
