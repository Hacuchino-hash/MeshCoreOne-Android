// PortedFrom: MC1Tests/AppState/NavigationCoordinatorTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/AppState/NavigationStateTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/SidebarNavigationLayoutTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.navigation.cases

import com.meshcoreone.android.app.navigation.*
import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

// Native adaptation: Same assertion body is compiled for instrumented and declared local native runners.
abstract class OriginalNavigationCases {
    protected val radio = RadioId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    protected val messageId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    protected fun contact(name: String = "TestContact") = ContactDTO(
        id = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)),
        radioId = radio, publicKey = Bytes(ByteArray(32) { 0xAA.toByte() }),
        name = name, lastHeardTimestamp = null,
    )
    protected fun channel(index: UByte = 0u) = ChannelDTO(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        radioId = radio, index = index, name = "TestChannel",
    )
    protected fun room() = RemoteNodeSessionDTO(
        id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
        radioId = radio, publicKey = Bytes(ByteArray(32) { 0xBB.toByte() }),
        name = "TestRoom", role = RemoteNodeRole.ROOM_SERVER,
    )

    protected abstract fun lookup(
        contact: ContactDTO? = contact(), channel: ChannelDTO? = channel(), room: RemoteNodeSessionDTO? = room(),
    ): NavigationLookup

    private fun assertChat(n: NavigationCoordinator, c: ContactDTO) {
        assertEquals(c, n.state.value.pendingChatContact)
        assertEquals(ChatSelection.Direct(c), n.state.value.chatsSelectedRoute)
        assertEquals(AppTab.CHATS, n.state.value.selectedTab)
    }

    @Test fun `DM notification tap navigates to chat with contact`() = runBlocking {
        val c = contact(); val n = NavigationCoordinator()
        NotificationFixture(this, radio, n, lookup()).use {
            requireNotNull(it.service.onNotificationTapped)(EntityKey(radio, c.id))
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertChat(n, c)
    }

    @Test fun `New contact notification with manualAddContacts navigates to discovery`() = runBlocking {
        val n = NavigationCoordinator()
        val device = DeviceDTO(radioId = radio, publicKey = contact().publicKey, nodeName = "Fixture", manualAddContacts = true)
        NotificationFixture(this, radio, n, lookup(), { device }).use {
            requireNotNull(it.service.onNewContactNotificationTapped)(EntityKey(radio, contact().id))
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertTrue(n.state.value.pendingDiscoveryNavigation)
        assertEquals(AppTab.NODES, n.state.value.selectedTab)
    }

    @Test fun `New contact notification without manualAddContacts navigates to contact detail`() = runBlocking {
        val n = NavigationCoordinator(); val c = contact()
        NotificationFixture(this, radio, n, lookup()).use {
            requireNotNull(it.service.onNewContactNotificationTapped)(EntityKey(radio, c.id))
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertEquals(c, n.state.value.pendingContactDetail)
        assertEquals(AppTab.NODES, n.state.value.selectedTab)
    }

    @Test fun `Channel notification tap navigates to channel`() = runBlocking {
        val n = NavigationCoordinator(); val c = channel(3u)
        NotificationFixture(this, radio, n, lookup(channel = c)).use {
            requireNotNull(it.service.onChannelNotificationTapped)(radio, c.index)
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertEquals(c, n.state.value.pendingChannel)
        assertEquals(ChatSelection.Channel(c), n.state.value.chatsSelectedRoute)
        assertEquals(AppTab.CHATS, n.state.value.selectedTab)
    }

    @Test fun `Reaction notification on DM navigates to chat with scrollToMessageID`() = runBlocking {
        val n = NavigationCoordinator(); val c = contact()
        NotificationFixture(this, radio, n, lookup()).use {
            requireNotNull(it.service.onReactionNotificationTapped)(EntityKey(radio, c.id), null, null, messageId)
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertChat(n, c); assertEquals(messageId, n.state.value.pendingScrollToMessageID)
    }

    @Test fun `Reaction notification on channel navigates to channel with scrollToMessageID`() = runBlocking {
        val n = NavigationCoordinator(); val c = channel()
        NotificationFixture(this, radio, n, lookup()).use {
            requireNotNull(it.service.onReactionNotificationTapped)(null, c.index, radio, messageId)
            assertEquals(listOf(NavigationOutcome.Navigated), it.outcomes)
        }
        assertEquals(c, n.state.value.pendingChannel)
        assertEquals(ChatSelection.Channel(c), n.state.value.chatsSelectedRoute)
        assertEquals(messageId, n.state.value.pendingScrollToMessageID)
    }

    @Test fun `navigateToMap sets pendingMapFocus and selects the map tab`() {
        val n = NavigationCoordinator(); n.navigateToMap(37.3349, -122.00902)
        assertEquals(MapFocusRequest(37.3349, -122.00902), n.state.value.pendingMapFocus)
        assertEquals(37.3349, requireNotNull(n.state.value.pendingMapFocus).coordinate.latitude, 0.0)
        assertEquals(AppTab.MAP, n.state.value.selectedTab)
    }

    @Test fun `chatConsumerForwardsMapCoordinateToNavigationSink`() {
        val n = NavigationCoordinator()
        val consumer = MapNavigationConsumer()
        consumer.onNavigateToMap = { n.navigateToMap(it.latitude, it.longitude) }
        assertEquals(MapNavigationOutcome.FORWARDED, consumer.navigateToMap(MapFocusRequest(51.5074, -0.1278)))
        assertEquals(MapFocusRequest(51.5074, -0.1278), n.state.value.pendingMapFocus)
        assertEquals(AppTab.MAP, n.state.value.selectedTab)
    }

    @Test fun `clearPendingMapFocus resets the pending focus`() {
        val n = NavigationCoordinator(); n.navigateToMap(1.0, 2.0); n.clearPendingMapFocus()
        assertNull(n.state.value.pendingMapFocus)
    }

    @Test fun `navigateToSetting sets selectedSetting and selects the settings tab`() {
        val n = NavigationCoordinator(); n.navigateToSetting(SettingsDetail.SUPPORT)
        assertEquals(SettingsDetail.SUPPORT, n.state.value.selectedSetting)
        assertEquals(AppTab.SETTINGS, n.state.value.selectedTab)
    }

    @Test fun `pendingContactLink starts nil and clears via helper`() {
        val n = NavigationCoordinator(); assertNull(n.state.value.pendingContactLink)
        n.stageContactLink(ContactLinkRequest("Alice", Bytes(ByteArray(32) { 0xAB.toByte() }), ContactType.CHAT))
        assertNotNull(n.state.value.pendingContactLink); n.clearPendingContactLink(); assertNull(n.state.value.pendingContactLink)
    }

    @Test fun `pendingChannelLink starts nil and clears via helper`() {
        val n = NavigationCoordinator(); assertNull(n.state.value.pendingChannelLink)
        n.stageChannelLink(ChannelLinkRequest("general", Bytes(ByteArray(16) { 0xCC.toByte() })))
        assertNotNull(n.state.value.pendingChannelLink); n.clearPendingChannelLink(); assertNull(n.state.value.pendingChannelLink)
    }

    @Test fun `pendingHashtag starts nil and clears via helper`() {
        val n = NavigationCoordinator(); assertNull(n.state.value.pendingHashtag)
        n.stageHashtag(HashtagJoinRequest("#general")); assertNotNull(n.state.value.pendingHashtag)
        n.clearPendingHashtag(); assertNull(n.state.value.pendingHashtag)
    }

    @Test fun `clearPendingLinks clears the hoisted Nodes selected contact`() {
        val n = NavigationCoordinator(); n.navigateToContactDetail(contact()); assertNotNull(n.state.value.selectedContact)
        n.clearPendingLinks(); assertNull(n.state.value.selectedContact)
    }

    @Test fun `clearPendingLinks clears the hoisted Nodes discovery flag`() {
        val n = NavigationCoordinator(); n.navigateToDiscovery(); n.clearPendingLinks()
        assertFalse(n.state.value.nodesShowingDiscovery)
    }

    @Test fun `clearPendingLinks clears every staged per-radio field at once`() {
        val n = NavigationCoordinator()
        n.stageContactLink(ContactLinkRequest("Alice", contact().publicKey, ContactType.CHAT))
        n.stageChannelLink(ChannelLinkRequest("general", channel().secret))
        n.stageHashtag(HashtagJoinRequest("#general"))
        n.navigateToContactDetail(contact()); n.navigateToDiscovery(); n.navigateToChat(contact())
        n.navigateToTool(ToolSelection.CLI); n.clearPendingLinks()
        val s = n.state.value
        assertNull(s.pendingContactLink); assertNull(s.pendingChannelLink); assertNull(s.pendingHashtag)
        assertNull(s.selectedContact); assertFalse(s.nodesShowingDiscovery); assertNull(s.chatsSelectedRoute)
        assertNull(s.selectedTool)
    }

    @Test fun `clearPerRadioSelection clears the hoisted Chats route`() {
        val n = NavigationCoordinator(); n.navigateToChat(contact()); n.clearPerRadioSelection()
        assertNull(n.state.value.chatsSelectedRoute)
    }

    @Test fun `clearPerRadioSelection clears a radio-requiring tool`() {
        val n = NavigationCoordinator(); n.navigateToTool(ToolSelection.CLI)
        assertTrue(requireNotNull(n.state.value.selectedTool).requiresRadio)
        n.clearPerRadioSelection(); assertNull(n.state.value.selectedTool)
    }

    @Test fun `clearPerRadioSelection preserves the offline Line of Sight tool`() {
        val n = NavigationCoordinator(); n.navigateToTool(ToolSelection.LINE_OF_SIGHT)
        assertFalse(requireNotNull(n.state.value.selectedTool).requiresRadio)
        n.clearPerRadioSelection(); assertEquals(ToolSelection.LINE_OF_SIGHT, n.state.value.selectedTool)
    }

    @Test fun `clearPerRadioSelection clears a per-device settings page`() {
        val n = NavigationCoordinator(); n.navigateToSetting(SettingsDetail.RADIO)
        assertTrue(requireNotNull(n.state.value.selectedSetting).requiresDevice)
        n.clearPerRadioSelection(); assertNull(n.state.value.selectedSetting)
    }

    @Test fun `clearPerRadioSelection preserves a device-independent settings page`() {
        val n = NavigationCoordinator(); n.navigateToSetting(SettingsDetail.APPEARANCE)
        assertFalse(requireNotNull(n.state.value.selectedSetting).requiresDevice)
        n.clearPerRadioSelection(); assertEquals(SettingsDetail.APPEARANCE, n.state.value.selectedSetting)
    }

    @Test fun `language settings page is device-independent`() {
        val n = NavigationCoordinator(); n.navigateToSetting(SettingsDetail.LANGUAGE)
        assertFalse(requireNotNull(n.state.value.selectedSetting).requiresDevice)
        n.clearPerDeviceSelection(); assertEquals(SettingsDetail.LANGUAGE, n.state.value.selectedSetting)
    }

    @Test fun `Default navigation state is tab 0 with no pending navigation`() {
        val s = NavigationCoordinator().state.value
        assertEquals(0, s.selectedTab.sourceIndex)
        assertNull(s.pendingChatContact); assertNull(s.pendingChannel); assertNull(s.pendingRoomSession)
        assertNull(s.pendingRoomAuthentication); assertFalse(s.pendingDiscoveryNavigation)
        assertNull(s.pendingContactDetail); assertNull(s.pendingScrollToMessageID); assertNull(s.chatsSelectedRoute)
        assertTrue(s.tabBarVisible)
    }

    @Test fun `navigateToChat sets contact, route, and tab`() {
        val n = NavigationCoordinator(); val c = contact(); n.navigateToChat(c)
        assertChat(n, c); assertFalse(n.state.value.tabBarVisible); assertNull(n.state.value.pendingScrollToMessageID)
    }

    @Test fun `navigateToChat with scrollToMessageID sets message ID`() {
        val n = NavigationCoordinator(); val c = contact(); n.navigateToChat(c, messageId)
        assertChat(n, c); assertEquals(messageId, n.state.value.pendingScrollToMessageID)
    }

    @Test fun `navigateToChat switches to Chats tab from another tab`() {
        val n = NavigationCoordinator(); n.selectTab(AppTab.TOOLS); val c = contact(); n.navigateToChat(c)
        assertChat(n, c)
    }

    @Test fun `navigateToRoom sets session, route, and tab`() {
        val n = NavigationCoordinator(); val r = room(); n.navigateToRoom(r)
        assertEquals(r, n.state.value.pendingRoomSession); assertEquals(ChatSelection.Room(r), n.state.value.chatsSelectedRoute)
        assertEquals(AppTab.CHATS, n.state.value.selectedTab); assertFalse(n.state.value.tabBarVisible)
    }

    @Test fun `navigateToChannel sets channel, route, and tab`() {
        val n = NavigationCoordinator(); val c = channel(); n.navigateToChannel(c)
        assertEquals(c, n.state.value.pendingChannel); assertEquals(ChatSelection.Channel(c), n.state.value.chatsSelectedRoute)
        assertEquals(AppTab.CHATS, n.state.value.selectedTab); assertFalse(n.state.value.tabBarVisible)
        assertNull(n.state.value.pendingScrollToMessageID)
    }

    @Test fun `navigateToChannel with scrollToMessageID sets message ID`() {
        val n = NavigationCoordinator(); val c = channel(); n.navigateToChannel(c, messageId)
        assertEquals(c, n.state.value.pendingChannel); assertEquals(messageId, n.state.value.pendingScrollToMessageID)
    }

    @Test fun `navigateToDiscovery sets pending flag and contacts tab`() {
        val n = NavigationCoordinator(); n.navigateToDiscovery()
        assertTrue(n.state.value.pendingDiscoveryNavigation); assertEquals(AppTab.NODES, n.state.value.selectedTab)
    }

    @Test fun `navigateToDiscovery does not hide tab bar`() {
        val n = NavigationCoordinator(); n.navigateToDiscovery(); assertTrue(n.state.value.tabBarVisible)
    }

    @Test fun `navigateToContacts switches to contacts tab`() {
        val n = NavigationCoordinator(); n.selectTab(AppTab.TOOLS); n.navigateToContacts()
        assertEquals(AppTab.NODES, n.state.value.selectedTab)
    }

    @Test fun `navigateToContactDetail sets contact and contacts tab`() {
        val n = NavigationCoordinator(); val c = contact(); n.navigateToContactDetail(c)
        assertEquals(c, n.state.value.pendingContactDetail); assertEquals(AppTab.NODES, n.state.value.selectedTab)
    }

    @Test fun `clearPendingNavigation clears chat contact`() {
        val n = NavigationCoordinator(); n.navigateToChat(contact()); n.clearPendingNavigation()
        assertNull(n.state.value.pendingChatContact)
    }

    @Test fun `clearPendingRoomNavigation clears room session`() {
        val n = NavigationCoordinator(); n.navigateToRoom(room()); n.clearPendingRoomNavigation()
        assertNull(n.state.value.pendingRoomSession)
    }

    @Test fun `clearPendingRoomAuthentication clears room auth session`() {
        val n = NavigationCoordinator(NavigationState(pendingRoomAuthentication = room()))
        n.clearPendingRoomAuthentication(); assertNull(n.state.value.pendingRoomAuthentication)
    }

    @Test fun `clearPendingChannelNavigation clears channel`() {
        val n = NavigationCoordinator(); n.navigateToChannel(channel()); n.clearPendingChannelNavigation()
        assertNull(n.state.value.pendingChannel)
    }

    @Test fun `clearPendingDiscoveryNavigation clears discovery flag`() {
        val n = NavigationCoordinator(); n.navigateToDiscovery(); n.clearPendingDiscoveryNavigation()
        assertFalse(n.state.value.pendingDiscoveryNavigation)
    }

    @Test fun `clearPendingScrollToMessage clears message ID`() {
        val n = NavigationCoordinator(); n.navigateToChat(contact(), messageId); n.clearPendingScrollToMessage()
        assertNull(n.state.value.pendingScrollToMessageID)
    }

    @Test fun `clearPendingContactDetailNavigation clears contact detail`() {
        val n = NavigationCoordinator(); n.navigateToContactDetail(contact()); n.clearPendingContactDetailNavigation()
        assertNull(n.state.value.pendingContactDetail)
    }

    @Test fun `navigateToChat from contacts tab hides tab bar and switches tab`() {
        val n = NavigationCoordinator(); n.selectTab(AppTab.NODES); val c = contact(); n.navigateToChat(c)
        assertChat(n, c); assertFalse(n.state.value.tabBarVisible)
    }

    @Test fun `Multiple navigation calls overwrite pending state`() {
        val n = NavigationCoordinator(); n.navigateToChat(contact("First")); val c = contact("Second"); n.navigateToChat(c)
        assertEquals(c, n.state.value.pendingChatContact); assertEquals(ChatSelection.Direct(c), n.state.value.chatsSelectedRoute)
    }

    @Test fun `Device menu tip donation is pending by default when false`() {
        assertFalse(NavigationCoordinator().state.value.pendingDeviceMenuTipDonation)
    }

    @Test fun `Section sidebar is narrow enough that 11-inch portrait tiles all three columns`() {
        assertTrue(NavigationLayout.TILE_MIN_WIDTH_DP <= 834)
        assertTrue(NavigationLayout.tilesListDetail(834f))
    }

    @Test fun `iPad mini portrait intentionally collapses rather than tiling`() {
        assertTrue(NavigationLayout.TILE_MIN_WIDTH_DP > 744)
        assertFalse(NavigationLayout.tilesListDetail(744f))
    }

    @Test fun `Sidebar width is in icon-only range, not a full sidebar`() {
        assertTrue(NavigationLayout.RAIL_WIDTH_DP <= 115)
    }

    @Test fun `A sidebar-collapsing tool collapses the sidebar even when the container is wide`() {
        assertFalse(NavigationLayout.showsRail(834f, ToolSelection.LINE_OF_SIGHT))
        assertFalse(NavigationLayout.showsRail(834f, ToolSelection.TRACE_PATH))
    }

    @Test fun `A wide container tiles the sidebar when no sidebar-collapsing tool is open`() {
        assertTrue(NavigationLayout.showsRail(834f, null))
        assertTrue(NavigationLayout.tilesListDetail(834f))
    }

    @Test fun `A narrow container collapses to the section's hidden shape`() {
        assertFalse(NavigationLayout.tilesListDetail(744f))
        assertTrue(NavigationLayout.usesRail(744f))
        assertFalse(NavigationLayout.usesRail(599f))
        assertTrue(NavigationLayout.usesRail(600f))
    }

    @Test fun `lineOfSightAndTracePathCollapseSidebarOtherToolsKeepIt`() {
        assertTrue(ToolSelection.LINE_OF_SIGHT.prefersCollapsedSidebar)
        assertTrue(ToolSelection.TRACE_PATH.prefersCollapsedSidebar)
        ToolSelection.entries.filter { it != ToolSelection.LINE_OF_SIGHT && it != ToolSelection.TRACE_PATH }.forEach {
            assertFalse(it.prefersCollapsedSidebar)
            assertTrue(NavigationLayout.showsRail(834f, it))
        }
    }
}
