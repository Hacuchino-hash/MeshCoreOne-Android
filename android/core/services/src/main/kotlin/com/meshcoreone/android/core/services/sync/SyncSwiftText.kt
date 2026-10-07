// AndroidOnly: WP-214 Swift String/Character/CharacterSet semantics the sync text paths depend on.
package com.meshcoreone.android.core.services.sync

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Swift text semantics used by sync:
 * - `String ==` and `Set<String>` membership compare by canonical equivalence; NFC forms are equal exactly
 *   when two strings are canonically equivalent.
 * - `split(separator: ":")` walks `Character`s (extended grapheme clusters); a `:` that a combining mark
 *   attaches to is not a separator. Segmentation uses `\X`, as the merged protocol module does.
 * - `CharacterSet.whitespaces` is Foundation's set, enumerated from Foundation with `swiftc` (it includes
 *   U+200B but not U+180E, unlike Unicode `Zs`).
 */
internal object SyncSwiftText {
    private val grapheme: Pattern = Pattern.compile("\\X")

    /** Foundation `CharacterSet.whitespaces`, exactly. */
    private val foundationWhitespaces: Set<Int> = buildSet {
        add(0x09); add(0x20); add(0xA0); add(0x1680)
        for (code in 0x2000..0x200B) add(code)
        add(0x202F); add(0x205F); add(0x3000)
    }

    /** Canonical (NFC) form used as the key for Swift `String` equality. */
    fun canonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    /** Swift `String ==` (canonical equivalence). */
    fun equal(a: String?, b: String?): Boolean =
        if (a == null || b == null) a == b else a == b || canonical(a) == canonical(b)

    /** Extended grapheme clusters (Swift `Character`s) of [text]. */
    fun characters(text: String): List<String> {
        val matcher = grapheme.matcher(text)
        val result = ArrayList<String>()
        while (matcher.find()) result += matcher.group()
        return result
    }

    /**
     * Swift `split(separator:maxSplits:omittingEmptySubsequences: true)` over `Character`s. Only appended
     * (non-empty) pieces count toward [maxSplits], matching the standard library implementation.
     */
    fun split(text: String, separator: String, maxSplits: Int): List<String> {
        val characters = characters(text)
        val result = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < characters.size) {
            val character = characters[index]
            index += 1
            if (character != separator) {
                current.append(character)
                continue
            }
            if (current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
                if (result.size == maxSplits) break
            }
        }
        while (index < characters.size) current.append(characters[index++])
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    /** Swift `trimmingCharacters(in: .whitespaces)`, which trims Unicode scalars at both ends. */
    fun trimmingWhitespaces(text: String): String {
        var start = 0
        var end = text.length
        while (start < end) {
            val code = text.codePointAt(start)
            if (code !in foundationWhitespaces) break
            start += Character.charCount(code)
        }
        while (end > start) {
            val code = text.codePointBefore(end)
            if (code !in foundationWhitespaces) break
            end -= Character.charCount(code)
        }
        return text.substring(start, end)
    }
}
