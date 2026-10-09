// AndroidOnly: WP-310 JVM fakes for the channel sheet seams.
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.support.Fixtures
import java.util.UUID

internal class FakeChannelPort : ChannelSetupPort, ChannelInfoPort {
    override var connectedRadioId: RadioId? = Fixtures.radio
    override var maxChannels: Int = 8
    override var servicesAvailable: Boolean = true
    var channels: MutableList<ChannelDTO> = mutableListOf()
    var fetchFailure: Exception? = null
    var setFailure: Exception? = null
    var floodScopeFailure: Exception? = null
    val calls = mutableListOf<String>()
    val persistedScopes = mutableListOf<Pair<UUID, ChannelFloodScope>>()
    val secret = Bytes(ByteArray(16) { (it + 1).toByte() })

    override suspend fun fetchChannels(radioId: RadioId): List<ChannelDTO> {
        fetchFailure?.let { throw it }
        return channels.toList()
    }

    override suspend fun fetchChannel(radioId: RadioId, index: UByte): ChannelDTO? = fetchChannels(radioId).firstOrNull { it.index == index }

    private fun write(index: UByte, name: String, secret: Bytes) {
        setFailure?.let { throw it }
        channels.removeAll { it.index == index }
        channels += ChannelDTO(radioId = Fixtures.radio, index = index, name = name, secret = secret)
    }

    override suspend fun setChannelWithSecret(radioId: RadioId, index: UByte, name: String, secret: Bytes) {
        calls += "withSecret:$index:$name:${secret.hexString}"
        write(index, name, secret)
    }

    override suspend fun setChannel(radioId: RadioId, index: UByte, name: String, passphrase: String) {
        calls += "setChannel:$index:$name:$passphrase"
        write(index, name, ChannelLinkPresentation.hashSecret(passphrase))
    }

    override suspend fun setupPublicChannel(radioId: RadioId) {
        calls += "public"
        write(0u, "Public", ChannelLinkPresentation.hashSecret("public"))
    }

    override suspend fun setChannelFloodScope(channelId: UUID, scope: ChannelFloodScope) {
        floodScopeFailure?.let { throw it }
        persistedScopes += channelId to scope
    }

    override fun generateSecret(): Bytes = secret

    override fun exportChannelUri(name: String, secret: Bytes, floodScope: ChannelFloodScope): String =
        "meshcore://channel/add?name=$name&secret=${secret.hexString}" +
            ((floodScope as? ChannelFloodScope.Region)?.let { "&region_scope=${it.name}" } ?: "")

    // ChannelInfoPort
    override var defaultFloodScopeName: String? = null
    override var knownRegions: List<String> = emptyList()
    val notificationLevels = mutableListOf<NotificationLevel>()
    var clearFailure: Exception? = null
    var discovery: RegionDiscoveryOutcome = RegionDiscoveryOutcome.SendFailed
    val radioScopes = mutableListOf<ChannelFloodScope>()
    val addedRegions = mutableListOf<String>()

    override suspend fun setNotificationLevel(channel: ChannelDTO, level: NotificationLevel) { notificationLevels += level }
    override suspend fun setFavorite(channel: ChannelDTO, isFavorite: Boolean) { calls += "favorite:$isFavorite" }
    override suspend fun clearChannel(radioId: RadioId, index: UByte) {
        clearFailure?.let { throw it }
        calls += "clearChannel:$index"
    }
    override suspend fun clearChannelMessages(radioId: RadioId, index: UByte) {
        clearFailure?.let { throw it }
        calls += "clearMessages:$index"
    }
    override suspend fun removeDeliveredNotifications(radioId: RadioId, channelIndex: UByte) { calls += "removeNotifications:$channelIndex" }
    override suspend fun applyFloodScopeToRadio(scope: ChannelFloodScope) { radioScopes += scope }
    override fun addKnownRegion(region: String) { addedRegions += region }
    override fun removeKnownRegion(region: String) { calls += "removeRegion:$region" }
    override suspend fun discoverRegions(radioId: RadioId, knownRegions: List<String>) = discovery
}
