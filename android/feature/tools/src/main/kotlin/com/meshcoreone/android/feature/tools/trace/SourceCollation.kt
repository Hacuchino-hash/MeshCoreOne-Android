// AndroidOnly: WP-314 Foundation localizedStandardCompare/localizedCaseInsensitiveCompare orderings measured by swiftc oracles.
package com.meshcoreone.android.feature.tools.trace

import java.math.BigInteger
import java.text.Collator
import java.util.Locale

/**
 * Foundation string orderings used for tie-breaks and name sorting.
 *
 * - `localizedStandardCompare`: digit runs compare numerically, letters ignore case first
 *   (`"Node 9" < "node 10"`), and only an otherwise equal pair falls back to case, with lowercase
 *   first (`"alpha" < "Alpha"`). Accents are significant (`"e" < "é"`).
 * - `localizedCaseInsensitiveCompare`: collation without case, no numeric runs (`"a10" < "a9"`).
 */
object SourceCollation {
    fun localizedStandard(locale: Locale = Locale.getDefault()): Comparator<String> {
        val secondary = collator(locale, Collator.SECONDARY)
        val tertiary = collator(locale, Collator.TERTIARY)
        return Comparator { left, right ->
            val numeric = numericAware(left, right, secondary)
            if (numeric != 0) numeric else tertiary.compare(left, right)
        }
    }

    fun localizedCaseInsensitive(locale: Locale = Locale.getDefault()): Comparator<String> {
        val secondary = collator(locale, Collator.SECONDARY)
        return Comparator { left, right -> secondary.compare(left, right) }
    }

    private fun collator(locale: Locale, strength: Int): Collator = Collator.getInstance(locale).apply {
        this.strength = strength
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }

    private fun numericAware(left: String, right: String, collator: Collator): Int {
        val leftTokens = tokens(left)
        val rightTokens = tokens(right)
        for (index in 0 until minOf(leftTokens.size, rightTokens.size)) {
            val a = leftTokens[index]
            val b = rightTokens[index]
            val result = if (a.isDigits && b.isDigits) {
                BigInteger(a.text).compareTo(BigInteger(b.text))
            } else {
                collator.compare(a.text, b.text)
            }
            if (result != 0) return result
        }
        return leftTokens.size.compareTo(rightTokens.size)
    }

    private data class Token(val text: String, val isDigits: Boolean)

    /** ASCII digit runs and everything between them. */
    private fun tokens(text: String): List<Token> {
        val result = ArrayList<Token>()
        var start = 0
        while (start < text.length) {
            val digits = text[start] in '0'..'9'
            var end = start
            while (end < text.length && (text[end] in '0'..'9') == digits) end += 1
            result += Token(text.substring(start, end), digits)
            start = end
        }
        return result
    }
}
