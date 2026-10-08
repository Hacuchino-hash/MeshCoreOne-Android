// AndroidOnly: WP-213 Swift-runtime oracle vectors for the locally ported reaction hash, wire parsers and hidden-reaction predicate.
package com.meshcoreone.android.core.services.rendering

import com.meshcoreone.android.core.contracts.domain.ParsedReaction
import com.meshcoreone.android.core.model.MessageDirection
import com.meshcoreone.android.core.model.MessageStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Expected values were produced by compiling the frozen Swift `ReactionParser.swift` and
 * `Character+EmojiDetection.swift` with Swift 6.3.2 and printing the results (WP-213 evidence run).
 */
class ReactionWireFormatTest {
    @TestFactory
    fun hashVectors(): List<DynamicTest> = listOf(
        Triple("Hello", 1_704_067_200u, "b45pc4ek"),
        Triple("", 0u, "vwzp3604"),
        Triple("Message 0", 0u, "7mt5pe1m"),
        Triple("Message 3", 3u, "frezkk3s"),
        Triple("World", 1_704_067_200u, "vp1vxnf4"),
        Triple("Hello", 1_704_067_201u, "3wh8x8z5"),
        Triple("café", 4_294_967_295u, "xhj588dm"),
        Triple("café", 4_294_967_295u, "vvrpxp56"),
        Triple("👍🏽 emoji", 123_456u, "7zzbzwxw"),
        Triple("Hello world", 1_700_000_000u, "vwpqfpms"),
    ).map { (text, timestamp, expected) ->
        DynamicTest.dynamicTest("WP-213::generateMessageHash matches Swift for ${escape(text)}@$timestamp") {
            assertEquals(expected, ReactionWireFormat.generateMessageHash(text, timestamp))
        }
    }

    @TestFactory
    fun channelParseVectors(): List<DynamicTest> {
        val thumbs = "👍"
        val family = "👨‍👩‍👧"
        val keycap = "1️⃣"
        val cases: List<Pair<String, ParsedReaction?>> = listOf(
            "@[Alice]$thumbs\nabcdefgh" to ParsedReaction(thumbs, "Alice", "abcdefgh"),
            "$thumbs@[Alice]\nabcdefgh" to ParsedReaction(thumbs, "Alice", "abcdefgh"),
            "@[Alice]$thumbs\nABCDEFGH" to ParsedReaction(thumbs, "Alice", "abcdefgh"),
            "@[Alice]$thumbs\nabcdefg" to null,
            "@[Alice]$thumbs\nabcdefgu" to null,
            "@[Alice]$thumbs$thumbs\nabcdefgh" to null,
            "@[]$thumbs\nabcdefgh" to null,
            "@[Alice]x\nabcdefgh" to null,
            "@[Alice]$thumbs\r\nabcdefgh" to null,
            "@[Alice]1\nabcdefgh" to null,
            "@[Alice]$keycap\nabcdefgh" to ParsedReaction(keycap, "Alice", "abcdefgh"),
            "$thumbs@[Alice\nabcdefgh" to null,
            "@[Alice]$family\nOOIILL00" to ParsedReaction(family, "Alice", "00111100"),
            "hello" to null,
            "@[Bob] hi\n12345678" to null,
            "$thumbs@[A]@[B]\nabcdefgh" to ParsedReaction(thumbs, "A]@[B", "abcdefgh"),
            "@[Al]ice]$thumbs\nabcdefgh" to null,
            "@[́A]$thumbs\nabcdefgh" to null,
            "$thumbs@[́A]\nabcdefgh" to null,
            "$thumbs@[A]́\nabcdefgh" to null,
            "@[A]́$thumbs\nabcdefgh" to null,
            "@[Á]$thumbs\nabcdefgh" to ParsedReaction(thumbs, "Á", "abcdefgh"),
        )
        return cases.map { (text, expected) ->
            DynamicTest.dynamicTest("WP-213::ReactionParser.parse matches Swift for ${escape(text)}") {
                assertEquals(expected, ReactionWireFormat.parse(text))
            }
        }
    }

    @TestFactory
    fun directParseVectors(): List<DynamicTest> {
        val thumbs = "👍"
        val cases: List<Pair<String, Pair<String, String>?>> = listOf(
            "$thumbs\nabcdefgh" to (thumbs to "abcdefgh"),
            "$thumbs\nABCDEFGH" to (thumbs to "abcdefgh"),
            "$thumbs🏽\nabcdefgh" to ("$thumbs🏽" to "abcdefgh"),
            "$thumbs$thumbs\nabcdefgh" to null,
            "x\nabcdefgh" to null,
            "$thumbs\r\nabcdefgh" to null,
            "$thumbs\nabcdefg" to null,
            "@[A]$thumbs\nabcdefgh" to null,
            "\nabcdefgh" to null,
            "a\n$thumbs\nabcdefgh" to null,
            "$thumbs\nabcdefgh\n" to null,
            "🇺🇸\nzzzzzzzz" to ("🇺🇸" to "zzzzzzzz"),
            "1\nabcdefgh" to null,
            "©️\nabcdefgh" to ("©️" to "abcdefgh"),
            "$thumbs\nabcdéfgh" to null,
            "$thumbs\nabcdefǵ" to null,
            "@[́$thumbs\nabcdefgh" to null,
        )
        return cases.map { (text, expected) ->
            DynamicTest.dynamicTest("WP-213::ReactionParser.parseDM matches Swift for ${escape(text)}") {
                assertEquals(expected, ReactionWireFormat.parseDM(text))
            }
        }
    }

    @TestFactory
    fun hiddenOutgoingReaction(): List<DynamicTest> {
        val predicate = HiddenOutgoingReactionPredicate.SourceWireFormat
        val dmReaction = RenderingFixtures.testDirectMessage(text = "👍\nabcdefgh", status = MessageStatus.SENT)
        val channelReaction = dmReaction.copy(text = "@[Alice]👍\nabcdefgh")
        return listOf(
            DynamicTest.dynamicTest("WP-213::sent outgoing reactions are hidden in their own conversation kind only") {
                assertTrue(predicate.isHiddenOutgoingReaction(dmReaction, isDM = true))
                assertFalse(predicate.isHiddenOutgoingReaction(dmReaction, isDM = false))
                assertTrue(predicate.isHiddenOutgoingReaction(channelReaction, isDM = false))
                assertFalse(predicate.isHiddenOutgoingReaction(channelReaction, isDM = true))
            },
            DynamicTest.dynamicTest("WP-213::failed and incoming reactions stay visible, plain text is never hidden") {
                assertFalse(predicate.isHiddenOutgoingReaction(dmReaction.copy(status = MessageStatus.FAILED), isDM = true))
                for (status in MessageStatus.entries - MessageStatus.FAILED) {
                    assertTrue(predicate.isHiddenOutgoingReaction(dmReaction.copy(status = status), isDM = true), "$status")
                }
                assertFalse(predicate.isHiddenOutgoingReaction(dmReaction.copy(direction = MessageDirection.INCOMING), isDM = true))
                assertFalse(predicate.isHiddenOutgoingReaction(dmReaction.copy(text = "hello"), isDM = true))
            },
        )
    }

    private fun escape(text: String): String = text.codePoints().toArray().joinToString("") { codePoint ->
        if (codePoint in 0x20..0x7E) codePoint.toChar().toString() else "\\u{%X}".format(codePoint)
    }
}
