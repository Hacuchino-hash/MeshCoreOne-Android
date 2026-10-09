// PortedFrom: MC1Tests/Views/Chats/MentionDeeplinkSupportTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/Views/HashtagChannelNavigationTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.deeplinks

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InternalDeepLinkSupportTest {
    @Test
    @SourceCases(
        "MentionDeeplinkSupportTests::A plain name round-trips through url and name()",
        "MentionDeeplinkSupportTests::A name containing a slash round-trips without losing the prefix()",
        "MentionDeeplinkSupportTests::A name containing a percent sign round-trips and is not dropped()",
        "MentionDeeplinkSupportTests::A literal percent-escape sequence in the name is not double-decoded()",
        "MentionDeeplinkSupportTests::A unicode name round-trips()",
    )
    fun mentionNamesRoundTripExactlyOnce() {
        listOf("Alice Smith", "Node 1/2 Repeater", "100% Coverage", "a%2Fb", "Café 北京").forEach { name ->
            val uri = MentionDeepLinkSupport.uriForName(name)!!
            assertEquals(MentionDeepLinkSupport.SCHEME, uri.scheme)
            assertEquals(MentionDeepLinkSupport.HOST, uri.host)
            assertEquals(name, MentionDeepLinkSupport.nameFromUri(uri))
        }
    }

    @Test
    @SourceCases("MentionDeeplinkSupportTests::name returns nil for non-mention URLs()")
    fun nonMentionLinksAreRejected() {
        listOf("https://apple.com", "meshcore://map?lat=1&lon=2", "meshcoreone://hashtag/general").forEach {
            assertNull(MentionDeepLinkSupport.nameFromUri(URI(it)))
        }
    }

    @Test
    @SourceCases(
        "HashtagChannelNavigationTests::normalized names produce consistent secrets()",
        "HashtagChannelNavigationTests::URL scheme encodes and decodes channel name correctly()",
    )
    fun hashtagNamesNormalizeAndDecode() {
        listOf("#General", "#GENERAL", "general", "#general").forEach {
            assertEquals("general", HashtagDeepLinkSupport.normalizeName(it))
        }
        val uri = URI("meshcoreone://hashtag/general")
        assertEquals("general", HashtagDeepLinkSupport.channelNameFromUri(uri))
        assertEquals("#general", HashtagDeepLinkSupport.fullChannelName("General"))
    }
}
