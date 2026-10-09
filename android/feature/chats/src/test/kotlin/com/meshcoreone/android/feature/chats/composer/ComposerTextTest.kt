// AndroidOnly: WP-308 Swift String semantics; expected values are the swiftc oracle output in docs/android/evidence/WP-308/oracle.
package com.meshcoreone.android.feature.chats.composer

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ComposerTextTest {
    @Test
    fun `utf8 length and grapheme count match the Swift oracle`() {
        // (text, utf8.count, count) from wp308_oracle.swift
        val cases = listOf(
            Triple("", 0, 0), Triple("a", 1, 1), Triple("é", 2, 1), Triple("é", 3, 1), Triple("日本語", 9, 3),
            Triple("👍🏽", 8, 1), Triple("👨‍👩‍👧‍👦", 25, 1), Triple("🇺🇸🇯🇵", 16, 2), Triple("1️⃣", 7, 1),
            Triple("​", 3, 1), Triple("한", 3, 1), Triple("a‍b", 5, 2), Triple("�", 3, 1), Triple("\r\n", 2, 1),
        )
        for ((text, bytes, count) in cases) {
            assertEquals(bytes, ComposerText.utf8Length(text), "bytes of $text")
            assertEquals(count, ComposerText.graphemes(text).size, "graphemes of $text")
        }
    }

    @Test
    fun `a lone surrogate counts as the three byte replacement character`() {
        assertEquals(3, ComposerText.utf8Length("\uD83D"))
        assertEquals(3, ComposerText.utf8Length("\uDC4D"))
        assertEquals(4, ComposerText.utf8Length("👍"))
        assertEquals("a".toByteArray().size + 3, ComposerText.utf8Length("a\uD83D"))
    }

    @Test
    fun `trimming matches whitespacesAndNewlines including zero width space but not BOM`() {
        assertEquals("hi", ComposerText.trimmed("  hi  "))
        assertEquals("hi", ComposerText.trimmed("​hi​"))
        assertEquals("hi", ComposerText.trimmed(" hi "))
        assertEquals("hi", ComposerText.trimmed(" hi "))
        assertEquals("hi", ComposerText.trimmed("\u0085hi　"))
        assertEquals("﻿hi", ComposerText.trimmed("﻿hi"))
        assertEquals("á", ComposerText.trimmed("á "))
        assertTrue(ComposerText.isBlank("​"))
        assertTrue(ComposerText.isBlank(" \n\t "))
        assertFalse(ComposerText.isBlank("﻿"))
    }

    @Test
    fun `last character whitespace follows Character isWhitespace`() {
        fun endsWhitespace(text: String) = ComposerText.graphemes(text).last().let(ComposerText::isWhitespaceCharacter)
        assertFalse(endsWhitespace("hello"))
        assertTrue(endsWhitespace("hello "))
        assertTrue(endsWhitespace("hello "))
        assertFalse(endsWhitespace("hello​"))
        assertFalse(endsWhitespace("helló"))
        assertTrue(endsWhitespace("hello ́"))
        assertTrue(endsWhitespace("x\n"))
    }
}
