// AndroidOnly: WP-310 Native channel-options behavior (loading, slot availability, enablement).
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.feature.chats.list.support.Fixtures.channel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ChannelOptionsStateHolderTest {
    @Test
    fun `loads free slots and detects the public channel`() = runBlocking {
        val port = FakeChannelPort().apply { maxChannels = 4; channels = mutableListOf(channel(index = 0u), channel(index = 2u)) }
        val holder = ChannelOptionsStateHolder(port)
        holder.load()
        val state = holder.state.value
        assertFalse(state.isLoading)
        assertTrue(state.hasPublicChannel)
        assertEquals(listOf<UByte>(1u, 3u), state.availableSlots)
        assertFalse(state.isEnabled(ChannelOption.JOIN_PUBLIC))
        assertTrue(state.isEnabled(ChannelOption.CREATE_PRIVATE))
        assertEquals(ChannelOptionsFooter.HAS_PUBLIC, state.footer)
    }

    @Test
    fun `no free slots disables slot options and shows the no-slots footer`() = runBlocking {
        val port = FakeChannelPort().apply { maxChannels = 2; channels = mutableListOf(channel(index = 1u)) }
        val holder = ChannelOptionsStateHolder(port)
        holder.load()
        val state = holder.state.value
        assertFalse(state.isEnabled(ChannelOption.JOIN_HASHTAG))
        assertFalse(state.isEnabled(ChannelOption.SCAN_QR))
        assertTrue(state.isEnabled(ChannelOption.JOIN_PUBLIC))
        assertEquals(ChannelOptionsFooter.NO_SLOTS, state.footer)
    }

    @Test
    fun `missing device and failed fetch both end loading with the empty state`() = runBlocking {
        val noDevice = FakeChannelPort().apply { connectedRadioId = null }
        ChannelOptionsStateHolder(noDevice).also { it.load() }.state.value.let {
            assertFalse(it.isLoading)
            assertTrue(it.availableSlots.isEmpty())
        }
        val failing = FakeChannelPort().apply { fetchFailure = IllegalStateException("db") }
        ChannelOptionsStateHolder(failing).also { it.load() }.state.value.let {
            assertFalse(it.isLoading)
            assertTrue(it.availableSlots.isEmpty())
        }
    }

    @Test
    fun `selecting an option is tracked and cleared`() {
        val holder = ChannelOptionsStateHolder(FakeChannelPort())
        holder.select(ChannelOption.JOIN_PRIVATE)
        assertEquals(ChannelOption.JOIN_PRIVATE, holder.state.value.selectedOption)
        holder.select(null)
        assertEquals(null, holder.state.value.selectedOption)
    }
}
