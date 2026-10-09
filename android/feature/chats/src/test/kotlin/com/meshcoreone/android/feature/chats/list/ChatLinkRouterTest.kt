// PortedFrom: MC1Tests/Views/Chats/ChatLinkRouterTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.list

import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.FakeLinks
import com.meshcoreone.android.feature.chats.list.support.Fixtures
import com.meshcoreone.android.feature.chats.list.support.OriginalCase
import com.meshcoreone.android.feature.chats.list.support.Scenario
import com.meshcoreone.android.feature.chats.list.support.scenario
import com.meshcoreone.android.feature.chats.list.support.settle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ChatLinkRouterTest {
    private fun Scenario.router(links: FakeLinks = FakeLinks()) = links to ChatLinkRouter(links, scope)

    @Test @OriginalCase("ChatLinkRouterTests::Returns false for plain https URLs (caller falls through to systemAction)()")
    fun `Returns false for plain https URLs`() = scenario {
        assertFalse(router().second.route("https://apple.com"))
    }

    @Test @OriginalCase("ChatLinkRouterTests::Returns false for mailto URLs (caller falls through to systemAction)()")
    fun `Returns false for mailto URLs`() = scenario {
        assertFalse(router().second.route("mailto:test@example.com"))
    }

    @Test @OriginalCase("ChatLinkRouterTests::Returns true for meshcoreone hashtag URLs and stages a pending hashtag()")
    fun `Returns true for meshcoreone hashtag URLs and stages a pending hashtag`() = scenario {
        val (links, router) = router()
        assertTrue(router.route("meshcoreone://hashtag/general"))
        settle()
        assertEquals("#general", links.pendingHashtag)
    }

    @Test @OriginalCase("ChatLinkRouterTests::Returns true for meshcore map URLs and stages map focus immediately()")
    fun `Returns true for meshcore map URLs and stages map focus immediately`() = scenario {
        val (links, router) = router()
        assertTrue(router.route("meshcore://map?lat=37.7749&lon=-122.4194"))
        assertEquals(37.7749 to -122.4194, links.mapFocus)
    }

    @Test @OriginalCase("ChatLinkRouterTests::Returns true for malformed hashtag URLs without crashing()")
    fun `Returns true for malformed hashtag URLs without crashing`() = scenario {
        val (links, router) = router()
        assertTrue(router.route("meshcoreone://hashtag"))
        settle()
        assertNull(links.pendingHashtag)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen stages a pending contact and switches to the Chats tab()")
    fun `routeExternalOpen stages a pending contact and switches to the Chats tab`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        val key = "ab".repeat(32)
        assertTrue(router.routeExternalOpen("meshcore://contact/add?name=NGC-MB&public_key=$key&type=1"))
        assertEquals(AppTab.CHATS, links.selectedTab)
        settle()
        assertNotNull(links.pendingContact)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen stages a pending channel and switches to the Chats tab()")
    fun `routeExternalOpen stages a pending channel and switches to the Chats tab`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        val secret = "ab".repeat(16)
        assertTrue(router.routeExternalOpen("meshcore://channel/add?name=Test&secret=$secret&region_scope=testregion"))
        assertEquals(AppTab.CHATS, links.selectedTab)
        settle()
        assertEquals("Test", links.pendingChannel?.name)
        assertEquals("testregion", links.pendingChannel?.regionScope)
    }

    @Test @OriginalCase("ChatLinkRouterTests::Existing channel match navigates without applying URL region_scope()")
    fun `Existing channel match navigates without applying URL region_scope`() = scenario {
        val secret = Bytes(ByteArray(16) { 0xCD.toByte() })
        val existing = Fixtures.channel("Ops", index = 1u, secret = secret, floodScope = ChannelFloodScope.Region("Germany"))
        val links = FakeLinks().apply { storedChannels = listOf(existing) }
        val router = ChatLinkRouter(links, scope)
        assertTrue(router.route("meshcore://channel/add?name=Ops&secret=${secret.hexString.uppercase()}&region_scope=attacker"))
        settle()
        assertNull(links.pendingChannel)
        assertEquals(existing.id, links.navigatedChannel?.id)
        assertEquals(ChannelFloodScope.Region("Germany"), links.navigatedChannel?.floodScope)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen restores the previous tab for meshcoreone status URLs()")
    fun `routeExternalOpen restores the previous tab for meshcoreone status URLs`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        assertFalse(router.routeExternalOpen("meshcoreone://status"))
        assertEquals(AppTab.NODES, links.selectedTab)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen ends on the map tab for meshcore map URLs()")
    fun `routeExternalOpen ends on the map tab for meshcore map URLs`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        assertTrue(router.routeExternalOpen("meshcore://map?lat=37.7749&lon=-122.4194"))
        assertEquals(AppTab.MAP, links.selectedTab)
        assertNotNull(links.mapFocus)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen restores the previous tab for a malformed meshcore URL()")
    fun `routeExternalOpen restores the previous tab for a malformed meshcore URL`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        assertFalse(router.routeExternalOpen("meshcore://garbage"))
        assertEquals(AppTab.NODES, links.selectedTab)
    }

    @Test @OriginalCase("ChatLinkRouterTests::routeExternalOpen stages a pending hashtag and switches to the Chats tab()")
    fun `routeExternalOpen stages a pending hashtag and switches to the Chats tab`() = scenario {
        val (links, router) = router()
        links.selectedTab = AppTab.NODES
        assertTrue(router.routeExternalOpen("meshcoreone://hashtag/general"))
        assertEquals(AppTab.CHATS, links.selectedTab)
        settle()
        assertEquals("#general", links.pendingHashtag)
    }

    // Android-only: a failed hashtag lookup stages the join request instead of dropping the tap.
    @Test
    fun `hashtag lookup failure stages the join request`() = scenario {
        val links = FakeLinks().apply { failChannelFetch = IllegalStateException("boom") }
        ChatLinkRouter(links, scope).route("meshcoreone://hashtag/general")
        settle()
        assertEquals("#general", links.pendingHashtag)
    }

    // Android-only: an existing hashtag channel is navigated to, case-insensitively, not re-staged.
    @Test
    fun `hashtag tap navigates to the existing channel`() = scenario {
        val existing = Fixtures.channel("#General", index = 4u)
        val links = FakeLinks().apply { storedChannels = listOf(existing) }
        ChatLinkRouter(links, scope).route("meshcoreone://hashtag/general")
        settle()
        assertEquals(existing.id, links.navigatedChannel?.id)
        assertNull(links.pendingHashtag)
    }
}
