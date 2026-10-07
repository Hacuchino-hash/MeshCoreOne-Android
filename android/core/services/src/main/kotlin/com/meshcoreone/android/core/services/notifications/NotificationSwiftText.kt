// AndroidOnly: WP-215 Swift String/Character semantics the notification text paths depend on.
package com.meshcoreone.android.core.services.notifications

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * - `String.count` and `prefix(_:)` walk `Character`s (extended grapheme clusters). Segmentation uses
 *   `\X`, as the merged protocol and model modules do (ICU-backed on the device, JDK 20+ on the JVM).
 * - `String ==` compares by canonical equivalence; NFC forms are equal exactly when two strings are
 *   canonically equivalent.
 * Expectations were checked with `swiftc` (docs/android/evidence/WP-215/wp215_text_oracle.swift.txt).
 */
internal object NotificationSwiftText {
    private val grapheme: Pattern = Pattern.compile("\\X")

    /** Swift `text.count`. */
    fun characterCount(text: String): Int {
        val matcher = grapheme.matcher(text)
        var count = 0
        while (matcher.find()) count += 1
        return count
    }

    /** Swift `String(text.prefix(limit))`. */
    fun prefix(text: String, limit: Int): String {
        val matcher = grapheme.matcher(text)
        var taken = 0
        var end = 0
        while (taken < limit && matcher.find()) {
            end = matcher.end()
            taken += 1
        }
        return text.substring(0, end)
    }

    /** Swift `String ==` (canonical equivalence). */
    fun equal(a: String, b: String): Boolean =
        a == b || Normalizer.normalize(a, Normalizer.Form.NFC) == Normalizer.normalize(b, Normalizer.Form.NFC)
}
