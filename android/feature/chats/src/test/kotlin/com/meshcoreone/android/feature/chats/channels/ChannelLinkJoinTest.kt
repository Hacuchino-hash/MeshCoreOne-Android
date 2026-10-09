// AndroidOnly: WP-310 Native link/QR join, hashtag-from-message and flood-scope-after-join behavior.
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.ChannelLinkResult
import com.meshcoreone.android.feature.chats.list.MeshCoreLinkParser
import com.meshcoreone.android.feature.chats.list.support.Fixtures.channel
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ChannelLinkJoinTest {
    private val secret = Bytes(ByteArray(16) { 0xBB.toByte() })
    private fun link(region: String? = null) = ChannelLinkResult("EmergencyOps", secret, region)

    // MARK: flood scope applier

    @Test
    fun `preferred scope is a normalized region or nothing`() {
        assertEquals(ChannelFloodScope.Region("europe"), ChannelJoinFloodScopeApplier.preferredFloodScope("  europe "))
        assertNull(ChannelJoinFloodScopeApplier.preferredFloodScope("   "))
        assertNull(ChannelJoinFloodScopeApplier.preferredFloodScope(null))
        assertEquals(31, (ChannelJoinFloodScopeApplier.preferredFloodScope("r".repeat(40)) as ChannelFloodScope.Region).name.length)
    }

    @Test
    fun `applier persists the scope and returns the updated channel`() = runBlocking {
        val original = channel("Ops")
        val written = mutableListOf<ChannelFloodScope>()
        val result = ChannelJoinFloodScopeApplier.applyIfNeeded(original, "eu", { _, scope -> written += scope })
        assertEquals(ChannelFloodScope.Region("eu"), result.floodScope)
        assertEquals(listOf<ChannelFloodScope>(ChannelFloodScope.Region("eu")), written)
    }

    @Test
    fun `applier skips empty scope and survives store failure`() = runBlocking {
        val original = channel("Ops")
        var writes = 0
        assertSame(original, ChannelJoinFloodScopeApplier.applyIfNeeded(original, null, { _, _ -> writes++ }))
        assertEquals(0, writes)
        var reported: Exception? = null
        val failed = ChannelJoinFloodScopeApplier.applyIfNeeded(original, "eu", { _, _ -> throw IOException("store") }) { reported = it }
        assertSame(original, failed)
        assertEquals("store", reported?.message)
    }

    // MARK: secret presentation

    @Test
    fun `hash secret matches the hashtag name hash and zero for empty`() {
        val parsed = MeshCoreLinkParser.parseChannelURL("meshcore://channel/add?name=%23Mesh")!!
        assertEquals("#mesh", parsed.name)
        assertEquals(parsed.secret.hexString, ChannelLinkPresentation.hashSecret("#mesh").hexString)
        assertEquals("00".repeat(16), ChannelLinkPresentation.hashSecret("").hexString)
    }

    @Test
    fun `hashtag secret mismatch only for hashtag names with a different secret`() {
        val derived = ChannelLinkPresentation.hashSecret("#mesh")
        assertFalse(ChannelLinkPresentation.hasHashtagSecretMismatch("#Mesh", derived))
        assertTrue(ChannelLinkPresentation.hasHashtagSecretMismatch("#mesh", secret))
        assertFalse(ChannelLinkPresentation.hasHashtagSecretMismatch("Private", secret))
        assertFalse(ChannelLinkPresentation.hasHashtagSecretMismatch("#-bad", secret))
    }

    @Test
    fun `secret is truncated to first and last eight digits`() {
        assertEquals("BBBBBBBB...BBBBBBBB", ChannelLinkPresentation.truncatedSecret(secret))
        assertEquals("AABB", ChannelLinkPresentation.truncatedSecret(Bytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))))
    }

    // MARK: confirmation sheet

    @Test
    fun `confirmation reports each content state`() = runBlocking {
        val loading = ChannelJoinConfirmationStateHolder(FakeChannelPort(), link())
        assertEquals(ChannelJoinContent.LOADING, loading.state.value.content)
        loading.loadAvailableSlots()
        assertEquals(ChannelJoinContent.CONFIRM, loading.state.value.content)
        val noDevice = ChannelJoinConfirmationStateHolder(FakeChannelPort().apply { connectedRadioId = null }, link())
        noDevice.loadAvailableSlots()
        assertEquals(ChannelJoinContent.MISSING_DEVICE, noDevice.state.value.content)
        val noSlots = ChannelJoinConfirmationStateHolder(FakeChannelPort().apply { maxChannels = 1 }, link())
        noSlots.loadAvailableSlots()
        assertEquals(ChannelJoinContent.NO_SLOTS, noSlots.state.value.content)
    }

    @Test
    fun `confirmation joins on the first free slot and applies the region scope`() = runBlocking {
        val port = FakeChannelPort().apply { channels = mutableListOf(channel(index = 1u)) }
        val holder = ChannelJoinConfirmationStateHolder(port, link("eu"))
        holder.loadAvailableSlots()
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals(2u.toUByte(), result.channel?.index)
        assertEquals(ChannelFloodScope.Region("eu"), result.channel?.floodScope)
        assertEquals(1, port.persistedScopes.size)
        assertTrue(holder.state.value.joinSucceeded)
    }

    @Test
    fun `confirmation still completes when persisting the scope fails`() = runBlocking {
        val port = FakeChannelPort().apply { floodScopeFailure = IOException("store") }
        val diagnostics = mutableListOf<String>()
        val holder = ChannelJoinConfirmationStateHolder(port, link("eu")) { message, _ -> diagnostics += message }
        holder.loadAvailableSlots()
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals(ChannelFloodScope.Inherit, result.channel?.floodScope)
        assertEquals(1, diagnostics.size)
    }

    @Test
    fun `confirmation join guards`() = runBlocking {
        val noDevice = ChannelJoinConfirmationStateHolder(FakeChannelPort().apply { connectedRadioId = null }, link())
        assertEquals(ChannelSheetResult.Stayed, noDevice.join())
        assertNull(noDevice.state.value.error)
        val noServices = ChannelJoinConfirmationStateHolder(FakeChannelPort().apply { servicesAvailable = false }, link())
        noServices.join()
        assertEquals(ChannelSheetError.NoDeviceConnected, noServices.state.value.error)
        val noSlots = ChannelJoinConfirmationStateHolder(FakeChannelPort().apply { maxChannels = 1 }, link())
        noSlots.loadAvailableSlots()
        noSlots.join()
        assertEquals(ChannelSheetError.NoSlots, noSlots.state.value.error)
        val failing = FakeChannelPort().apply { setFailure = IOException("busy") }
        val a = ChannelJoinConfirmationStateHolder(failing, link()).also { it.loadAvailableSlots() }
        a.join()
        assertEquals(ChannelSheetError.Failure("busy"), a.state.value.error)
        assertFalse(a.state.value.isJoining)
    }

    @Test
    fun `confirmation reports a failed read back without marking success`() = runBlocking {
        val port = FakeChannelPort()
        // The channel write succeeds but the read back finds nothing.
        val racing = object : ChannelSetupPort by port {
            override suspend fun fetchChannel(radioId: com.meshcoreone.android.core.model.RadioId, index: UByte) = null
        }
        val racingHolder = ChannelJoinConfirmationStateHolder(racing, link())
        racingHolder.loadAvailableSlots()
        assertEquals(ChannelSheetResult.Stayed, racingHolder.join())
        assertEquals(ChannelSheetError.LoadFailed, racingHolder.state.value.error)
        assertFalse(racingHolder.state.value.joinSucceeded)
    }

    // MARK: hashtag from message

    @Test
    fun `hashtag from message normalizes the name and joins with it as the passphrase`() = runBlocking {
        val port = FakeChannelPort()
        val holder = JoinHashtagFromMessageStateHolder(port, "#Mesh")
        assertEquals("mesh", holder.normalizedName)
        assertEquals("#mesh", holder.fullChannelName)
        holder.loadAvailableSlots()
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals("setChannel:1:#mesh:#mesh", port.calls.single())
    }

    @Test
    fun `hashtag from message validates before writing`() = runBlocking {
        val port = FakeChannelPort()
        val invalid = JoinHashtagFromMessageStateHolder(port, "#bad name")
        invalid.loadAvailableSlots()
        invalid.join()
        assertEquals(ChannelSheetError.InvalidName, invalid.state.value.error)
        val noSlots = JoinHashtagFromMessageStateHolder(FakeChannelPort().apply { maxChannels = 1 }, "mesh")
        noSlots.loadAvailableSlots()
        noSlots.join()
        assertEquals(ChannelSheetError.NoSlots, noSlots.state.value.error)
        val noDevice = JoinHashtagFromMessageStateHolder(FakeChannelPort().apply { connectedRadioId = null }, "mesh")
        noDevice.join()
        assertEquals(ChannelSheetError.NoDeviceConnected, noDevice.state.value.error)
        val noServices = JoinHashtagFromMessageStateHolder(FakeChannelPort().apply { servicesAvailable = false }, "mesh")
        noServices.join()
        assertEquals(ChannelSheetError.ServicesUnavailable, noServices.state.value.error)
        assertTrue(port.calls.isEmpty())
    }

    // MARK: scan QR

    @Test
    fun `scan accepts a channel url and rejects other payloads`() {
        val holder = ScanChannelQrStateHolder(FakeChannelPort(), listOf(1u))
        holder.onScanResult("https://example.com")
        assertEquals(ScanChannelQrError.InvalidFormat, holder.state.value.error)
        assertNull(holder.state.value.scannedChannel)
        holder.onScanResult("meshcore://channel/add?name=Ops&secret=${"AB".repeat(16)}&region_scope=eu")
        assertEquals("Ops", holder.state.value.scannedChannel?.name)
        holder.scanAgain()
        assertNull(holder.state.value.scannedChannel)
        assertNull(holder.state.value.error)
    }

    @Test
    fun `scan join writes the scanned channel and applies the region scope`() = runBlocking {
        val port = FakeChannelPort()
        val holder = ScanChannelQrStateHolder(port, listOf(4u))
        holder.onScanResult("meshcore://channel/add?name=Ops&secret=${"AB".repeat(16)}&region_scope=eu")
        val result = holder.join()
        assertIs<ChannelSheetResult.Completed>(result)
        assertEquals(ChannelFloodScope.Region("eu"), result.channel?.floodScope)
        assertEquals("withSecret:4:Ops:${"ab".repeat(16)}", port.calls.single())
    }

    @Test
    fun `scan join guards missing scan and device and surfaces failures`() = runBlocking {
        val none = ScanChannelQrStateHolder(FakeChannelPort(), listOf(1u))
        assertEquals(ChannelSheetResult.Stayed, none.join())
        assertEquals(ScanChannelQrError.Join(ChannelSheetError.NoDeviceConnected), none.state.value.error)
        val unavailable = ScanChannelQrStateHolder(FakeChannelPort().apply { servicesAvailable = false }, listOf(1u))
        unavailable.onScanResult("meshcore://channel/add?name=Ops&secret=${"AB".repeat(16)}")
        unavailable.join()
        assertEquals(ScanChannelQrError.Join(ChannelSheetError.ServicesUnavailable), unavailable.state.value.error)
        val failing = ScanChannelQrStateHolder(FakeChannelPort().apply { setFailure = IOException("x") }, listOf(1u))
        failing.onScanResult("meshcore://channel/add?name=Ops&secret=${"AB".repeat(16)}")
        failing.join()
        assertEquals(ScanChannelQrError.Join(ChannelSheetError.Failure("x")), failing.state.value.error)
    }

    @Test
    fun `scan completes with no channel when the read back fails`() = runBlocking {
        val port = FakeChannelPort().apply { fetchFailure = IOException("db") }
        val holder = ScanChannelQrStateHolder(port, listOf(1u))
        holder.onScanResult("meshcore://channel/add?name=Ops&secret=${"AB".repeat(16)}")
        assertEquals(ChannelSheetResult.Completed(null), holder.join())
    }

    @Test
    fun `camera permission denial is tracked`() {
        val holder = ScanChannelQrStateHolder(FakeChannelPort(), listOf(1u))
        holder.onCameraPermissionDenied()
        assertTrue(holder.state.value.cameraPermissionDenied)
    }
}
