// AndroidOnly: WP-306 Foundation-comparison parity checks; expected values come from the swiftc oracle in docs/android/evidence/WP-306/oracle.
package com.meshcoreone.android.feature.chats.list

import kotlin.test.assertEquals
import org.junit.Test

class ChatTextMatchingTest {
    @Test
    fun `localizedCaseInsensitiveCompare oracle`() {
        val cases = listOf(
            Triple("alice", "ALICE", true), Triple("Ä", "ä", true), Triple("é", "e", false),
            Triple("é", "é", true), Triple("ß", "SS", true), Triple("ß", "ss", true),
            Triple("İ", "i", false), Triple("ａ", "a", true), Triple("Alice ", "Alice", false),
            Triple("ǅ", "ǆ", true), Triple("σ", "ς", true), Triple("Σ", "ς", true),
        )
        for ((lhs, rhs, expected) in cases) assertEquals(expected, ChatTextMatching.caseInsensitiveEquals(lhs, rhs), "$lhs vs $rhs")
    }

    @Test
    fun `localizedStandardContains oracle`() {
        val cases = listOf(
            Triple("Café", "cafe", true), Triple("cafe", "Café", true), Triple("Straße", "strasse", true),
            Triple("strasse", "Straße", true), Triple("ａlice", "alice", false), Triple("Alice", "ａ", false),
            Triple("İstanbul", "istanbul", true), Triple("Ångström", "angstrom", true), Triple("Alice", "ALI", true),
            Triple("a b", "a  b", false), Triple("alice", "", false),
        )
        for ((haystack, needle, expected) in cases) assertEquals(expected, ChatTextMatching.standardContains(haystack, needle), "$haystack contains $needle")
    }
}
