// AndroidOnly: WP-310 Native channel info/share/delete/region behavior.
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.Fixtures.channel
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ChannelInfoStateHolderTest {
    private val secret = Bytes(ByteArray(16) { 1 })

    private fun holder(
        port: FakeChannelPort = FakeChannelPort(),
        channel: com.meshcoreone.android.core.model.ChannelDTO = channel("Ops", index = 2u, secret = secret),
    ) = ChannelInfoStateHolder(channel, port, port)

    @Test
    fun `type label and share sections follow the channel kind`() {
        assertEquals(ChannelTypeLabel.PUBLIC, holder(channel = channel("Public", index = 0u, secret = secret)).typeLabel)
        assertEquals(ChannelTypeLabel.HASHTAG, holder(channel = channel("#mesh", secret = secret)).typeLabel)
        assertEquals(ChannelTypeLabel.PRIVATE, holder().typeLabel)
        assertTrue(holder().showsShareSections)
        assertFalse(holder(channel = channel("Public", index = 0u, secret = secret)).showsShareSections)
        assertFalse(holder(channel = channel("Zero", index = 3u)).showsShareSections)
    }

    @Test
    fun `qr uri follows the selected scope`() = runBlocking {
        val h = holder()
        assertEquals("meshcore://channel/add?name=Ops&secret=${secret.hexString}", h.qrUri())
        h.selectFloodScope(ChannelFloodScope.Region("eu"))
        assertTrue(h.qrUri().endsWith("&region_scope=eu"))
    }

    @Test
    fun `selecting a scope persists then pushes to the radio`() = runBlocking {
        val port = FakeChannelPort()
        val h = holder(port)
        h.selectFloodScope(ChannelFloodScope.AllRegions)
        assertEquals(ChannelFloodScope.AllRegions, h.state.value.selectedFloodScope)
        assertEquals(1, port.persistedScopes.size)
        assertEquals(listOf<ChannelFloodScope>(ChannelFloodScope.AllRegions), port.radioScopes)
    }

    @Test
    fun `a failed scope save reverts and skips the radio push`() = runBlocking {
        val port = FakeChannelPort().apply { floodScopeFailure = IOException("store") }
        val h = holder(port)
        h.selectFloodScope(ChannelFloodScope.Region("eu"))
        assertEquals(ChannelFloodScope.Inherit, h.state.value.selectedFloodScope)
        assertTrue(port.radioScopes.isEmpty())
    }

    @Test
    fun `quick actions update state and persist`() = runBlocking {
        val port = FakeChannelPort()
        val h = holder(port)
        h.setNotificationLevel(NotificationLevel.MUTED)
        h.setFavorite(true)
        assertEquals(NotificationLevel.MUTED, h.state.value.notificationLevel)
        assertTrue(h.state.value.isFavorite)
        assertEquals(listOf(NotificationLevel.MUTED), port.notificationLevels)
        assertTrue("favorite:true" in port.calls)
    }

    @Test
    fun `delete clears the channel and its notifications`() = runBlocking {
        val port = FakeChannelPort()
        val h = holder(port)
        assertEquals(ChannelInfoOutcome.DELETED, h.deleteChannel())
        assertEquals(listOf("clearChannel:2", "removeNotifications:2"), port.calls)
    }

    @Test
    fun `clear messages keeps the channel`() = runBlocking {
        val port = FakeChannelPort()
        assertEquals(ChannelInfoOutcome.MESSAGES_CLEARED, holder(port).clearMessages())
        assertEquals(listOf("clearMessages:2", "removeNotifications:2"), port.calls)
    }

    @Test
    fun `destructive actions guard device and services and surface failures`() = runBlocking {
        val noDevice = holder(FakeChannelPort().apply { connectedRadioId = null })
        assertEquals(ChannelInfoOutcome.FAILED, noDevice.deleteChannel())
        assertEquals(ChannelSheetError.NoDeviceConnected, noDevice.state.value.error)
        val noServices = holder(FakeChannelPort().apply { servicesAvailable = false })
        assertEquals(ChannelInfoOutcome.FAILED, noServices.clearMessages())
        assertEquals(ChannelSheetError.ServicesUnavailable, noServices.state.value.error)
        val failing = holder(FakeChannelPort().apply { clearFailure = IOException("busy") })
        assertEquals(ChannelInfoOutcome.FAILED, failing.deleteChannel())
        assertEquals(ChannelSheetError.Failure("busy"), failing.state.value.error)
        assertFalse(failing.state.value.isDeleting)
    }

    @Test
    fun `discovery maps each outcome to the right message or results`() = runBlocking {
        fun run(outcome: RegionDiscoveryOutcome): ChannelInfoStateHolder {
            val port = FakeChannelPort().apply { discovery = outcome }
            return holder(port).also { runBlocking { it.discoverRegions() } }
        }
        assertEquals(RegionDiscoveryMessage.NO_NEW_REGIONS, run(RegionDiscoveryOutcome.SendFailed).state.value.discoveryMessage)
        assertEquals(RegionDiscoveryMessage.NO_REPEATERS_RESPONDED, run(RegionDiscoveryOutcome.NoRepeatersResponded).state.value.discoveryMessage)
        assertEquals(RegionDiscoveryMessage.ERROR_LOADING_REPEATERS, run(RegionDiscoveryOutcome.ErrorLoadingRepeaters).state.value.discoveryMessage)
        assertEquals(RegionDiscoveryMessage.RADIO_CONTACTS_FULL, run(RegionDiscoveryOutcome.Completed(emptyList(), true)).state.value.discoveryMessage)
        assertEquals(RegionDiscoveryMessage.NO_NEW_REGIONS, run(RegionDiscoveryOutcome.Completed(emptyList(), false)).state.value.discoveryMessage)
        val found = run(RegionDiscoveryOutcome.Completed(listOf("a", "b"), true)).state.value
        assertNull(found.discoveryMessage)
        assertEquals(listOf("a", "b"), found.discoveredNewRegions)
        assertFalse(found.isDiscoveringRegions)
    }

    @Test
    fun `discovered regions are added to the known set`() {
        val port = FakeChannelPort()
        holder(port).addDiscoveredRegions(listOf("a", "b"))
        assertEquals(listOf("a", "b"), port.addedRegions)
    }
}
