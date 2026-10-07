// AndroidOnly: WP-216 checks the Kotlin parsers, hashes and WP-208 stand-ins against Swift-runtime oracle vectors.
package com.meshcoreone.android.core.services.reactions

import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.ReactionDTO
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Every vector family is one test that reports all mismatching inputs at once. */
class ReactionsSwiftOracleTest {
    private val vectors = ReactionsSwiftOracleVectors

    @TestFactory
    fun reactionParserVectors(): List<DynamicTest> = listOf(
        family("ReactionParser.parse matches Swift (grapheme delimiters, emoji rule, Crockford hash)", vectors.channelParse) {
            ReactionParser.parse(it)
        },
        family("ReactionParser.parseDM matches Swift", vectors.directParse) { ReactionParser.parseDM(it) },
        family(
            "ReactionParser.isReactionText matches Swift for DM and channel",
            vectors.isReactionText.map { (text, dm, channel) -> text to (dm to channel) },
        ) { ReactionParser.isReactionText(it, isDM = true) to ReactionParser.isReactionText(it, isDM = false) },
        family(
            "ReactionParser.generateMessageHash matches Swift (UTF-8 bytes, LE32 stamp, NFC/NFD distinct)",
            vectors.messageHashes.map { (text, stamp, hash) -> (text to stamp) to hash },
        ) { (text, stamp) -> ReactionParser.generateMessageHash(text, stamp) },
        DynamicTest.dynamicTest("WP-216::ReactionParser.buildDMReactionText matches Swift") {
            assertEquals(vectors.DM_BUILD_HELLO_WORLD, ReactionParser.buildDMReactionText("👍", "Hello world", 1_704_067_200u))
        },
        DynamicTest.dynamicTest("WP-216::ReactionParser.buildSummary keeps Swift's stable order for equal counts") {
            val tuples = listOf(
                listOf(ReactionCount("a", 1), ReactionCount("b", 2), ReactionCount("c", 1), ReactionCount("d", 2)),
                listOf(ReactionCount("👍", 3), ReactionCount("❤\uFE0F", 2), ReactionCount("😂", 1)),
                emptyList(),
            )
            assertEquals(vectors.buildSummaryTuples, tuples.map(ReactionParser::buildSummary))
        },
        DynamicTest.dynamicTest("WP-216::ReactionParser.buildSummary groups canonically equivalent emoji and breaks ties by earliest") {
            val base = Instant.ofEpochSecond(1_700_000_000)
            val radioId = RadioId(UUID.randomUUID())
            val messageID = UUID.randomUUID()
            fun dto(emoji: String, offset: Long) = ReactionDTO(
                messageID = messageID, emoji = emoji, senderName = "n$offset", messageHash = "abcdefgh",
                rawText = "", receivedAt = base.plusSeconds(offset), radioId = radioId,
            )
            val reactions = listOf(dto("b", 5), dto("a", 9), dto("a", 1), dto("c", 2), dto("#\u0301", 3), dto("#\u0341", 4), dto("c", 0))
            assertEquals(vectors.BUILD_SUMMARY_DTOS, ReactionParser.buildSummary(reactions))
        },
        family(
            "ReactionParser.parseSummary matches Swift (grapheme split, empty pieces dropped, Swift Int parsing)",
            vectors.parseSummary.map { (summary, pairs) -> summary to pairs.map { (emoji, count) -> ReactionCount(emoji, count) } },
        ) { ReactionParser.parseSummary(it) },
    )

    @TestFactory
    fun meshCoreOpenVectors(): List<DynamicTest> = listOf(
        family("MeshCoreOpenReactionParser.parse matches Swift (fullwidth hex, grapheme positions)", vectors.meshCoreOpenV3) {
            MeshCoreOpenReactionParser.parse(it)
        },
        family("MeshCoreOpenReactionParser.parseV1 matches Swift (signs, empty parts, ranges)", vectors.meshCoreOpenV1) {
            MeshCoreOpenReactionParser.parseV1(it)
        },
        family("MeshCoreOpenReactionParser.dartStringHash matches Swift", vectors.dartHashes) { MeshCoreOpenReactionParser.dartStringHash(it) },
        family("MeshCoreOpenReactionParser.computeReactionHash matches Swift", vectors.computeReactionHashes) { (stamp, sender, text) ->
            MeshCoreOpenReactionParser.computeReactionHash(stamp, sender, text)
        },
        DynamicTest.dynamicTest("WP-216::MeshCoreOpenReactionParser.emojiTable matches the Swift table entry for entry") {
            assertEquals(vectors.emojiTable, MeshCoreOpenReactionParser.emojiTable)
        },
    )

    @TestFactory
    fun wp208StandInVectors(): List<DynamicTest> = listOf(
        family("ChannelMessageFormat.parse matches Swift (first colon Character, Foundation whitespaces incl. U+200B)", vectors.channelMessageFormat) {
            ChannelMessageFormat.parse(it)
        },
        DynamicTest.dynamicTest("WP-216::DeduplicationKey.contentBased matches Swift") {
            val contact = UUID.fromString("0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9")
            val actual = listOf(
                DeduplicationKey.contentBased(null, 3u, "Alice", 42u, "one"),
                DeduplicationKey.contentBased(null, 0u, null, 0u, ""),
                DeduplicationKey.contentBased(contact, null, "x", 4_294_967_295u, "café"),
                DeduplicationKey.contentBased(null, null, null, 7u, "hi"),
                DeduplicationKey.contentBased(null, 255u, "e\u0301", 1u, "👍"),
            )
            assertEquals(vectors.deduplicationKeys, actual)
        },
    )

    @TestFactory
    fun oracleCoverage(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("WP-216::oracle vector families are non-trivial") {
            assertTrue(vectors.channelParse.size >= 80 && vectors.channelParse.count { it.second != null } >= 30)
            assertTrue(vectors.meshCoreOpenV1.count { it.second != null } >= 15)
            assertTrue(vectors.channelMessageFormat.size >= 30)
        },
    )

    private fun <I, O> family(name: String, cases: List<Pair<I, O>>, actual: (I) -> O): DynamicTest =
        DynamicTest.dynamicTest("WP-216::$name") {
            val mismatches = cases.mapNotNull { (input, expected) ->
                val value = actual(input)
                if (value == expected) null else "${escape(input)}: expected $expected, got $value"
            }
            if (mismatches.isNotEmpty()) fail("${mismatches.size}/${cases.size} mismatches:\n" + mismatches.joinToString("\n"))
        }

    private fun escape(value: Any?): String = value.toString().map { c ->
        if (c.code in 0x20..0x7E) c.toString() else "\\u%04X".format(c.code)
    }.joinToString("")
}
