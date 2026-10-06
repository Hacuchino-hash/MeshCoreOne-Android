// AndroidOnly: WP-213 Swift-runtime oracle vectors for Character.isEmoji, grapheme counts and whitespace sets.
package com.meshcoreone.android.core.services.rendering

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Expected values printed by the Swift 6.3.2 runtime from the frozen `Character+EmojiDetection.swift`. */
class SwiftCharacterSemanticsTest {
    @TestFactory
    fun isEmojiVectors(): List<DynamicTest> = listOf(
        "1" to false, "#" to false, "*" to false,
        "1️⃣" to true, "#️⃣" to true, // keycaps: digit/# carry Emoji=Yes and count > 1
        "©" to false, "©️" to true, "™" to false,
        "⌚" to false, "⎌" to false, "⏩" to true, // the 0x238C threshold
        "👍" to true, "👍🏽" to true, "🏽" to true, // skin tones
        "🇺🇸" to true, "🇺" to true, // flag and lone regional indicator
        "👨‍👩‍👧" to true, // ZWJ family
        "❤" to true, "❤️" to true, "a" to false, "é" to false, "é" to false,
        "⭐" to true, "〰" to true, "🫩" to true, "😀" to true, " " to false, "☺" to true,
        "" to false,
    ).map { (character, expected) ->
        DynamicTest.dynamicTest("WP-213::Character.isEmoji matches Swift for ${escape(character)}") {
            assertEquals(expected, CharacterEmojiDetection.isEmoji(character))
        }
    }

    @TestFactory
    fun propertyTables(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("WP-213::embedded Emoji table is a superset of the JDK set, adding only newer Unicode emoji") {
            val newerThanJdk21 = setOf(
                0x1F6D8, 0x1FA89, 0x1FA8A, 0x1FA8E, 0x1FA8F, 0x1FABE, 0x1FAC6, 0x1FAC8, 0x1FACD, 0x1FADC, 0x1FADF, 0x1FAE9, 0x1FAEA, 0x1FAEF,
            )
            var tableCount = 0
            for (codePoint in 0..Character.MAX_CODE_POINT) {
                val table = CharacterEmojiDetection.hasEmojiProperty(codePoint)
                if (table) tableCount++
                if (Character.isEmoji(codePoint)) assertTrue(table, "JDK emoji U+%X missing from table".format(codePoint))
                if (table && !Character.isEmoji(codePoint)) assertTrue(codePoint in newerThanJdk21, "U+%X".format(codePoint))
            }
            assertEquals(1438, tableCount)
        },
        DynamicTest.dynamicTest("WP-213::White_Space and Foundation whitespacesAndNewlines sets match the Swift runtime") {
            val white = ranges("9-D 20 85 A0 1680 2000-200A 2028-2029 202F 205F 3000")
            val trim = ranges("9-D 20 85 A0 1680 2000-200B 2028-2029 202F 205F 3000")
            for (codePoint in 0..Character.MAX_CODE_POINT) {
                assertEquals(codePoint in white, SwiftText.isWhiteSpaceScalar(codePoint), "U+%X".format(codePoint))
                assertEquals(codePoint in trim, SwiftText.isTrimmableScalar(codePoint), "U+%X".format(codePoint))
            }
        },
    )

    @TestFactory
    fun graphemeAndTextVectors(): List<DynamicTest> = listOf(
        native("grapheme counts match Swift String.count") {
            mapOf(
                "\r\n" to 1, "a\r\nb" to 3, "🇺🇸🇬🇧" to 2,
                "👨‍👩‍👧x" to 2, "é̂" to 1, "각" to 1,
                "1️⃣" to 1, "👍🏽" to 1, "" to 0,
            ).forEach { (text, expected) -> assertEquals(expected, SwiftText.graphemeCount(text), escape(text)) }
        },
        native("documented deviation: JDK 21 lacks Unicode 15.1 GB9c, so a Devanagari conjunct is 2 Characters (Swift: 1)") {
            assertEquals(2, SwiftText.graphemeCount("क्ष"))
        },
        native("trimming and blank tests follow Foundation whitespacesAndNewlines") {
            mapOf(
                " " to true, "\t" to true, "\u000B" to true, "\u0085" to true, " " to true, " " to true,
                " " to true, "　" to true, "\u001C" to false, "\u001F" to false, "​" to true, "﻿" to false,
                "᠎" to false, "\r\n" to true,
            ).forEach { (text, expected) -> assertEquals(expected, SwiftText.isBlankAfterTrimming(text), escape(text)) }
            assertEquals("a\u001C", SwiftText.trimmingWhitespacesAndNewlines("​ a\u001C\u0085"))
        },
        native("Character.isWhitespace is White_Space of the first scalar") {
            mapOf(
                " " to true, "\u0085" to true, " " to true, "\u001C" to false, "​" to false, "᠎" to false,
                "﻿" to false, "\r\n" to true, "　" to true, " " to true, " " to true,
            ).forEach { (character, expected) -> assertEquals(expected, SwiftText.isWhitespaceCharacter(character), escape(character)) }
        },
        native("String.lowercased is context-free full lowercase mapping") {
            assertEquals("σοσ", SwiftText.lowercased("ΣΟΣ")) // no final sigma
            assertEquals("i̇", SwiftText.lowercased("İ"))
            assertEquals("ß", SwiftText.lowercased("ẞ"))
            assertEquals("abcé", SwiftText.lowercased("ABCÉ"))
            assertEquals("ǆ", SwiftText.lowercased("ǅ"))
        },
        native("canonical lookup and Character equality use canonical equivalence") {
            assertEquals(1, SwiftText.canonicalLookup(mapOf("josé" to 1), "josé"))
            assertTrue(SwiftText.characterEquals("é", "é"))
        },
    )

    private fun ranges(spec: String): Set<Int> = spec.split(" ").flatMap { part ->
        val bounds = part.split("-").map { it.toInt(16) }
        (bounds.first()..bounds.last()).toList()
    }.toSet()

    private fun native(name: String, body: () -> Unit) = DynamicTest.dynamicTest("WP-213::$name", body)

    private fun escape(text: String): String = text.codePoints().toArray().joinToString("") { codePoint ->
        if (codePoint in 0x20..0x7E) codePoint.toChar().toString() else "\\u{%X}".format(codePoint)
    }
}
