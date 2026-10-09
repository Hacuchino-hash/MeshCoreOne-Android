// PortedFrom: MC1/Models/Conversation+Filtering.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/SenderContactMatcher.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.list

import java.text.Normalizer
import java.util.Locale

/**
 * Locale-independent stand-ins for Foundation's `localizedStandardContains` and
 * `localizedCaseInsensitiveCompare == .orderedSame`. Both were checked against a swiftc oracle
 * (docs/android/evidence/WP-306/oracle); see docs/android/deviations/WP-306.md for the residual gaps.
 */
object ChatTextMatching {
    private val combiningMarks = Regex("\\p{Mn}+")

    private fun caseFold(text: String): String = text.uppercase(Locale.ROOT).lowercase(Locale.ROOT)

    /** Case-insensitive, canonical-equivalence and width-insensitive equality, diacritic-sensitive. */
    fun caseInsensitiveEquals(lhs: String, rhs: String): Boolean =
        caseFold(Normalizer.normalize(lhs, Normalizer.Form.NFKC)) ==
            caseFold(Normalizer.normalize(rhs, Normalizer.Form.NFKC))

    private fun searchFold(text: String): String =
        caseFold(combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), ""))

    /** Case- and diacritic-insensitive substring match; an empty needle never matches. */
    fun standardContains(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        return searchFold(haystack).contains(searchFold(needle))
    }
}
