// AndroidOnly: WP-213 Swift Character/String semantics (graphemes, White_Space, canonical equality) the rendering ports rely on.
package com.meshcoreone.android.core.services.rendering

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * Swift `Character` / `String` behaviors re-expressed over JVM UTF-16 strings.
 *
 * - A Swift `Character` is an extended grapheme cluster; [graphemes] segments with the JDK `\X`
 *   matcher (JDK 21 implements the Unicode 15.0 rules; see the WP-213 deviation notes for the
 *   Unicode 15.1 Indic-conjunct rule GB9c that Swift applies and JDK 21 does not).
 * - Swift compares `Character`/`String` values by canonical equivalence; [canonical] maps to NFC so
 *   two canonically equivalent spellings compare equal.
 * - `Character.isWhitespace` is the Unicode `White_Space` property of the first scalar
 *   ([isWhitespaceCharacter]); Foundation `CharacterSet.whitespacesAndNewlines` additionally contains
 *   U+200B ([isTrimmableScalar]). Both sets were enumerated from the Swift 6.3 runtime.
 */
internal object SwiftText {
    private val GRAPHEME: Pattern = Pattern.compile("\\X")
    private const val ZERO_WIDTH_SPACE = 0x200B
    private const val CAPITAL_I_WITH_DOT = 0x130
    private const val DOTTED_LOWER_I = "i̇"

    fun graphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val matcher = GRAPHEME.matcher(text)
        val result = ArrayList<String>()
        while (matcher.find()) result.add(matcher.group())
        return result
    }

    fun graphemeCount(text: String): Int = graphemes(text).size

    /** Unicode `White_Space` (stable property; identical in Unicode 15–17). */
    fun isWhiteSpaceScalar(codePoint: Int): Boolean = when (codePoint) {
        in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }

    /** Foundation `CharacterSet.whitespacesAndNewlines` membership for one scalar. */
    fun isTrimmableScalar(codePoint: Int): Boolean = isWhiteSpaceScalar(codePoint) || codePoint == ZERO_WIDTH_SPACE

    /** Swift `Character.isWhitespace`: the first scalar has the `White_Space` property. */
    fun isWhitespaceCharacter(character: String): Boolean =
        character.isNotEmpty() && isWhiteSpaceScalar(character.codePointAt(0))

    /** `text.trimmingCharacters(in: .whitespacesAndNewlines)`. */
    fun trimmingWhitespacesAndNewlines(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && isTrimmableScalar(text.codePointAt(start))) start += Character.charCount(text.codePointAt(start))
        while (end > start && isTrimmableScalar(text.codePointBefore(end))) end -= Character.charCount(text.codePointBefore(end))
        return text.substring(start, end)
    }

    /** `text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty`. */
    fun isBlankAfterTrimming(text: String): Boolean = trimmingWhitespacesAndNewlines(text).isEmpty()

    /** Canonical-equivalence key (NFC) for Swift `String`/`Character` equality and hashing. */
    fun canonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    fun characterEquals(character: String, other: String): Boolean = canonical(character) == canonical(other)

    /**
     * Swift `String.lowercased()`: each scalar's full, context-free lowercase mapping. Unlike
     * `String.lowercase(Locale.ROOT)` it never applies the Greek final-sigma context rule.
     */
    fun lowercased(text: String): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (codePoint == CAPITAL_I_WITH_DOT) builder.append(DOTTED_LOWER_I) else builder.appendCodePoint(Character.toLowerCase(codePoint))
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }

    /** Foundation `caseInsensitiveCompare(_:) == .orderedSame`: full case folding over canonical forms. */
    fun caseInsensitiveEquals(first: String, second: String): Boolean = caseFold(first) == caseFold(second)

    private fun caseFold(text: String): String =
        canonical(canonical(text).uppercase(Locale.ROOT).lowercase(Locale.ROOT))

    /** Swift `Dictionary<String, V>` lookup, which hashes and compares keys by canonical equivalence. */
    fun <V> canonicalLookup(table: Map<String, V>, key: String): V? {
        table[key]?.let { return it }
        val canonicalKey = canonical(key)
        return table.entries.firstOrNull { canonical(it.key) == canonicalKey }?.value
    }
}
