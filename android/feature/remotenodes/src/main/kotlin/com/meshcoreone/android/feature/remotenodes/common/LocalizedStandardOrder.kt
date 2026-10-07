// AndroidOnly: WP-313 Foundation localizedStandardCompare (Finder-like numeric, case/diacritic-tiered ordering) for remote-node lists.
package com.meshcoreone.android.feature.remotenodes.common

import java.math.BigInteger
import java.text.Collator
import java.util.Locale

/**
 * Approximates `String.localizedStandardCompare` (verified against Foundation with swiftc, evidence
 * oracle `wp313_compare.swift.txt`): digit runs compare by numeric value ("a2" < "a10"); base letters
 * decide first, then diacritics, then case with lowercase first ("a" < "A" < "á" < "Á"); whitespace is
 * significant and sorts low (" a" < "a"); equal numbers break ties by fewer digits ("x1" < "x01").
 * The tiers are applied across the whole string, as collation does, not token by token.
 */
class LocalizedStandardOrder(locale: Locale) : Comparator<String> {
    private enum class Kind { SPACE, DIGITS, TEXT }
    private class Token(val kind: Kind, val text: String)

    private val collators = listOf(Collator.PRIMARY, Collator.SECONDARY, Collator.TERTIARY).map { strength ->
        Collator.getInstance(locale).apply {
            this.strength = strength
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    override fun compare(left: String, right: String): Int {
        val a = tokens(left)
        val b = tokens(right)
        collators.forEachIndexed { level, collator ->
            val result = compareLevel(a, b, collator, primary = level == 0)
            if (result != 0) return result
        }
        for (index in 0 until minOf(a.size, b.size)) {
            if (a[index].kind == Kind.DIGITS && b[index].kind == Kind.DIGITS) {
                val result = a[index].text.length.compareTo(b[index].text.length)
                if (result != 0) return result
            }
        }
        return 0
    }

    private fun compareLevel(a: List<Token>, b: List<Token>, collator: Collator, primary: Boolean): Int {
        for (index in 0 until minOf(a.size, b.size)) {
            val x = a[index]
            val y = b[index]
            val result = when {
                x.kind != y.kind -> if (primary) x.kind.compareTo(y.kind) else 0
                x.kind == Kind.DIGITS -> if (primary) BigInteger(x.text).compareTo(BigInteger(y.text)) else 0
                x.kind == Kind.SPACE -> if (primary) x.text.length.compareTo(y.text.length) else 0
                else -> collator.compare(x.text, y.text)
            }
            if (result != 0) return result
        }
        return if (primary) a.size.compareTo(b.size) else 0
    }

    private fun tokens(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < text.length) {
            val kind = kindOf(text[index])
            var end = index + 1
            while (end < text.length && kindOf(text[end]) == kind) end++
            tokens += Token(kind, text.substring(index, end))
            index = end
        }
        return tokens
    }

    private fun kindOf(character: Char): Kind = when {
        character in '0'..'9' -> Kind.DIGITS
        character.isWhitespace() -> Kind.SPACE
        else -> Kind.TEXT
    }
}
