// AndroidOnly: WP-213 Swift-runtime oracle vectors for MentionUtilities (the Swift file has no owned test suite).
package com.meshcoreone.android.core.services.rendering

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Expected values printed by the frozen `MentionUtilities.swift` compiled with Swift 6.3.2 (en_US). */
class MentionUtilitiesTest {
    @TestFactory
    fun detectActiveMention(): List<DynamicTest> = listOf(
        "" to null, "@" to "", "hi @" to "", "hi @al" to "al", "email@host" to null, "@al bob" to "al",
        "hi @[Alice]" to null, "hi @[Alice] " to null, "hi @[Alice] @bo" to "bo", "hi @[Ali" to null,
        "@ x" to null, "@@x" to null, "a @bob" to "bob", "a\u0085@bob" to "bob", "a\u001C@bob" to null,
        "́@bob" to null, "a@́bob" to null, "@́bob" to null, "@bo b" to "bo",
        "x @[A] and @[B]" to null, "x @[A] and @[B] tail" to null, "@[́x" to "[́x", "\r\n@bo" to "bo",
        "@bob　x" to "bob",
    ).map { (text, expected) ->
        native("detectActiveMention matches Swift for ${escape(text)}") { assertEquals(expected, MentionUtilities.detectActiveMention(text)) }
    }

    @TestFactory
    fun extractAndAppend(): List<DynamicTest> = listOf(
        native("extractMentions matches Swift") {
            assertEquals(listOf("Alice", "Bob"), MentionUtilities.extractMentions("@[Alice] hi @[Bob]"))
            assertEquals(listOf("x"), MentionUtilities.extractMentions("@[] @[x]"))
            assertEquals(listOf("a"), MentionUtilities.extractMentions("@[a]]"))
            assertEquals(listOf("👍🏽"), MentionUtilities.extractMentions("@[👍🏽]"))
            assertEquals(listOf("a\nb"), MentionUtilities.extractMentions("@[a\nb]"))
            assertEquals(emptyList(), MentionUtilities.extractMentions("no"))
            assertEquals("@[Bob]", MentionUtilities.createMention("Bob"))
        },
        native("appendMention matches Swift") {
            mapOf(
                "" to "@[Bob] ", "hi" to "hi @[Bob] ", "hi " to "hi @[Bob] ", "hi\n" to "hi\n@[Bob] ",
                "hi " to "hi @[Bob] ", "hi\u001C" to "hi\u001C @[Bob] ", "hi\u0085" to "hi\u0085@[Bob] ",
                "é" to "é @[Bob] ",
            ).forEach { (draft, expected) -> assertEquals(expected, MentionUtilities.appendMention("Bob", draft), escape(draft)) }
        },
        native("containsSelfMention uses Foundation case-insensitive comparison") {
            listOf(
                Triple("@[alice] hi", "Alice", true), Triple("@[Straße]", "STRASSE", true),
                Triple("@[Strasse]", "straße", true), Triple("@[José]", "José", true),
                Triple("@[JOSÉ]", "josé", true), Triple("@[Jose]", "José", false),
                Triple("@[İstanbul]", "istanbul", false), Triple("@[ΣΟΣ]", "σος", true),
                Triple("@[ﬁx]", "FIX", true), Triple("@[Bob]", "Bo", false),
            ).forEach { (text, name, expected) ->
                assertEquals(expected, MentionUtilities.containsSelfMention(text, name), "${escape(text)} vs ${escape(name)}")
            }
        },
        native("buildReplyText matches Swift (ICU whitespace, grapheme preview length)") {
            mapOf(
                "hello" to "@[Al]\n>hello\n",
                "0123456789" to "@[Al]\n>0123456789\n",
                "0123456789X" to "@[Al]\n>0123456789..\n",
                "@[Bob] hi there friend" to "@[Al]\n>hi there f..\n",
                "@[Bob]  abc" to "@[Al]\n>abc\n",
                "@[Bob]\u000Babc" to "@[Al]\n>abc\n",
                "@[Bob]\u0085abc" to "@[Al]\n>abc\n",
                "👨‍👩‍👧🇺🇸abcdefghij" to
                    "@[Al]\n>👨‍👩‍👧🇺🇸abcdefgh..\n",
                "x @[Bob] y" to "@[Al]\n>x @[Bob] y\n",
                "@[Bob]" to "@[Al]\n>\n",
                "@[Bob] z" to "@[Al]\n>z\n",
                "@[Bob]　z" to "@[Al]\n>z\n",
            ).forEach { (text, expected) -> assertEquals(expected, MentionUtilities.buildReplyText("Al", text), escape(text)) }
        },
    )

    @TestFactory
    fun filterContacts(): List<DynamicTest> {
        val contacts = listOf(
            contact("zed"), contact("Émile"), contact("emma"), contact("Bob", nickname = "bobby"),
            contact("Rep", type = ContactType.REPEATER), contact("alice"), contact("Alice"), contact("Renée"),
        )
        val all = listOf("alice", "Alice", "bobby", "Émile", "emma", "Renée", "zed")
        val withE = listOf("alice", "Alice", "Émile", "emma", "Renée", "zed")
        return listOf("" to all, "e" to withE, "E" to withE, "é" to withE, "BOB" to listOf("bobby"),
            "rene" to listOf("Renée"), "zz" to emptyList(), "mil" to listOf("Émile"),
        ).map { (query, expected) ->
            native("filterContacts matches Swift for query ${escape(query)}") {
                assertEquals(expected, MentionUtilities.filterContacts(contacts, query, locale = Locale.US).map { it.displayName })
            }
        } + native("filterContacts orders by sender recency, then alphabetically") {
            val order = mapOf("zed" to 5u, "alice" to 9u, "Bob" to 7u)
            assertEquals(
                listOf("alice", "bobby", "zed", "Alice", "Émile", "emma", "Renée"),
                MentionUtilities.filterContacts(contacts, "", order, Locale.US).map { it.displayName },
            )
        }
    }

    private fun contact(name: String, nickname: String? = null, type: ContactType = ContactType.CHAT) = ContactDTO(
        radioId = RadioId(UUID(0, 1)),
        publicKey = Bytes(ByteArray(32) { name.hashCode().toByte() }),
        name = name,
        typeRawValue = type.rawValue,
        lastHeardTimestamp = null,
        nickname = nickname,
    )

    private fun native(name: String, body: () -> Unit) = DynamicTest.dynamicTest("WP-213::$name", body)

    private fun escape(text: String): String = text.codePoints().toArray().joinToString("") { codePoint ->
        if (codePoint in 0x20..0x7E) codePoint.toChar().toString() else "\\u{%X}".format(codePoint)
    }
}
