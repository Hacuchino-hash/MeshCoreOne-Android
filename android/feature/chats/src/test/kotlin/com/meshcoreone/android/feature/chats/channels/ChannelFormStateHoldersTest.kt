// AndroidOnly: WP-310 Native create/join sheet behavior (auth/slot/boundary/confirm cases of the Swift views).
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.feature.chats.list.support.Fixtures.channel
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ChannelFormStateHoldersTest {
    private val validSecret = "0123456789ABCDEF0123456789ABCDEF"

    // MARK: create private

    @Test
    fun `create generates a secret once and gates the button on a name`() {
        val port = FakeChannelPort()
        val holder = CreatePrivateChannelStateHolder(port, listOf(3u, 4u))
        holder.start()
        holder.start()
        assertEquals(port.secret, holder.state.value.secret)
        assertEquals(3u.toUByte(), holder.state.value.selectedSlot)
        assertFalse(holder.state.value.canCreate)
        holder.setChannelName("Ops")
        assertTrue(holder.state.value.canCreate)
    }

    @Test
    fun `create writes the channel reads it back and switches to share`() = runBlocking {
        val port = FakeChannelPort()
        val holder = CreatePrivateChannelStateHolder(port, listOf(2u))
        holder.start()
        holder.setChannelName("Ops")
        holder.create()
        val state = holder.state.value
        assertTrue(state.isCreated)
        assertEquals("Ops", state.createdChannel?.name)
        assertFalse(state.isCreating)
        assertNull(state.error)
        assertEquals("withSecret:2:Ops:${port.secret.hexString}", port.calls.single())
        assertEquals("meshcore://channel/add?name=Ops&secret=${port.secret.hexString}", holder.shareUri())
    }

    @Test
    fun `create reports a missing device or secret without starting`() = runBlocking {
        val port = FakeChannelPort().apply { connectedRadioId = null }
        val holder = CreatePrivateChannelStateHolder(port, listOf(1u))
        holder.start()
        holder.setChannelName("Ops")
        holder.create()
        assertEquals(ChannelSheetError.NoDeviceConnected, holder.state.value.error)
        assertTrue(port.calls.isEmpty())
        // No secret generated yet (start() not called) is the same guard.
        val noSecret = CreatePrivateChannelStateHolder(FakeChannelPort(), listOf(1u))
        noSecret.create()
        assertEquals(ChannelSheetError.NoDeviceConnected, noSecret.state.value.error)
    }

    @Test
    fun `create surfaces unavailable services and service failures`() = runBlocking {
        val unavailable = FakeChannelPort().apply { servicesAvailable = false }
        val a = CreatePrivateChannelStateHolder(unavailable, listOf(1u)).also { it.start(); it.setChannelName("x"); it.create() }
        assertEquals(ChannelSheetError.ServicesUnavailable, a.state.value.error)
        assertFalse(a.state.value.isCreating)
        val failing = FakeChannelPort().apply { setFailure = IOException("radio busy") }
        val b = CreatePrivateChannelStateHolder(failing, listOf(1u)).also { it.start(); it.setChannelName("x"); it.create() }
        assertEquals(ChannelSheetError.Failure("radio busy"), b.state.value.error)
        assertFalse(b.state.value.isCreated)
    }

    @Test
    fun `create stays on the form when the read back fails`() = runBlocking {
        val port = FakeChannelPort().apply { fetchFailure = IOException("db") }
        val holder = CreatePrivateChannelStateHolder(port, listOf(1u))
        holder.start()
        holder.setChannelName("x")
        holder.create()
        assertFalse(holder.state.value.isCreated)
        assertNull(holder.state.value.error)
    }

    @Test
    fun `create name input is capped`() {
        val holder = CreatePrivateChannelStateHolder(FakeChannelPort(), listOf(1u))
        holder.setChannelName("n".repeat(50))
        assertEquals(31, holder.state.value.channelName.length)
    }

    // MARK: join private

    @Test
    fun `join private validates the secret and shows the footer only after typing`() {
        val holder = JoinPrivateChannelStateHolder(FakeChannelPort(), listOf(1u))
        assertFalse(holder.state.value.showsInvalidSecretFooter)
        holder.setSecretKeyHex("zz12")
        assertEquals("12", holder.state.value.secretKeyHex)
        assertTrue(holder.state.value.showsInvalidSecretFooter)
        holder.setSecretKeyHex(validSecret.lowercase())
        assertTrue(holder.state.value.isValidSecret)
        assertFalse(holder.state.value.canJoin)
        holder.setChannelName("Ops")
        assertTrue(holder.state.value.canJoin)
    }

    @Test
    fun `join private writes the channel and completes with it`() = runBlocking {
        val port = FakeChannelPort()
        val holder = JoinPrivateChannelStateHolder(port, listOf(5u))
        holder.setChannelName("Ops")
        holder.setSecretKeyHex(validSecret)
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals("Ops", result.channel?.name)
        assertEquals("withSecret:5:Ops:${validSecret.lowercase()}", port.calls.single())
        assertFalse(holder.state.value.isJoining)
    }

    @Test
    fun `join private rejects a missing device and a bad secret before any write`() = runBlocking {
        val noDevice = FakeChannelPort().apply { connectedRadioId = null }
        val a = JoinPrivateChannelStateHolder(noDevice, listOf(1u)).also { it.setSecretKeyHex(validSecret) }
        assertEquals(ChannelSheetResult.Stayed, a.join())
        assertEquals(ChannelSheetError.NoDeviceConnected, a.state.value.error)
        val port = FakeChannelPort()
        val b = JoinPrivateChannelStateHolder(port, listOf(1u)).also { it.setSecretKeyHex("ABC") }
        assertEquals(ChannelSheetResult.Stayed, b.join())
        // A short secret is rejected before any radio write.
        assertEquals(ChannelSheetError.InvalidFormat, b.state.value.error)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `join private reports unavailable services and failures`() = runBlocking {
        val unavailable = FakeChannelPort().apply { servicesAvailable = false }
        val a = JoinPrivateChannelStateHolder(unavailable, listOf(1u)).also { it.setSecretKeyHex(validSecret) }
        a.join()
        assertEquals(ChannelSheetError.ServicesUnavailable, a.state.value.error)
        val failing = FakeChannelPort().apply { setFailure = IOException("nope") }
        val b = JoinPrivateChannelStateHolder(failing, listOf(1u)).also { it.setSecretKeyHex(validSecret) }
        assertEquals(ChannelSheetResult.Stayed, b.join())
        assertEquals(ChannelSheetError.Failure("nope"), b.state.value.error)
    }

    // MARK: join public

    @Test
    fun `join public re-adds slot zero`() = runBlocking {
        val port = FakeChannelPort()
        val holder = JoinPublicChannelStateHolder(port)
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals(0u.toUByte(), result.channel?.index)
        assertEquals(listOf("public"), port.calls)
    }

    @Test
    fun `join public guards device and services`() = runBlocking {
        val a = JoinPublicChannelStateHolder(FakeChannelPort().apply { connectedRadioId = null })
        assertEquals(ChannelSheetResult.Stayed, a.join())
        assertEquals(ChannelSheetError.NoDeviceConnected, a.state.value.error)
        val b = JoinPublicChannelStateHolder(FakeChannelPort().apply { servicesAvailable = false })
        b.join()
        assertEquals(ChannelSheetError.ServicesUnavailable, b.state.value.error)
        assertFalse(b.state.value.isJoining)
    }

    // MARK: join hashtag

    @Test
    fun `hashtag name is sanitized and validated`() {
        val holder = JoinHashtagChannelStateHolder(FakeChannelPort(), listOf(1u))
        assertFalse(holder.state.value.isActionEnabled)
        holder.setChannelName("-My Tag!")
        assertEquals("mytag", holder.state.value.channelName)
        assertTrue(holder.state.value.isActionEnabled)
    }

    @Test
    fun `hashtag joins with the full name as the passphrase`() = runBlocking {
        val port = FakeChannelPort()
        val holder = JoinHashtagChannelStateHolder(port, listOf(3u))
        holder.setChannelName("mesh")
        val result = holder.performPrimaryAction()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals("#mesh", result.channel?.name)
        assertEquals("setChannel:3:#mesh:#mesh", port.calls.single())
    }

    @Test
    fun `hashtag goes to an existing channel instead of joining`() = runBlocking {
        val existing = channel(name = "#Mesh", index = 2u)
        val port = FakeChannelPort().apply { channels = mutableListOf(existing) }
        val holder = JoinHashtagChannelStateHolder(port, listOf(3u))
        holder.loadExistingChannels()
        holder.setChannelName("mesh")
        assertEquals(existing, holder.state.value.existingChannel)
        val result = holder.performPrimaryAction()
        assertEquals(ChannelSheetResult.Completed(existing), result)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `hashtag existing-channel lookup fails open`() = runBlocking {
        val port = FakeChannelPort().apply { fetchFailure = IOException("db") }
        val holder = JoinHashtagChannelStateHolder(port, listOf(1u))
        holder.loadExistingChannels()
        holder.setChannelName("mesh")
        assertNull(holder.state.value.existingChannel)
        assertTrue(holder.state.value.isActionEnabled)
    }

    @Test
    fun `hashtag guards device and services`() = runBlocking {
        val a = JoinHashtagChannelStateHolder(FakeChannelPort().apply { connectedRadioId = null }, listOf(1u))
        a.setChannelName("x")
        a.performPrimaryAction()
        assertEquals(ChannelSheetError.NoDeviceConnected, a.state.value.error)
        val b = JoinHashtagChannelStateHolder(FakeChannelPort().apply { servicesAvailable = false }, listOf(1u))
        b.setChannelName("x")
        b.performPrimaryAction()
        assertEquals(ChannelSheetError.ServicesUnavailable, b.state.value.error)
        assertNotNull(b.state.value.error)
        Unit
    }
}
