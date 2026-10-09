// AndroidOnly: WP-310 Feature-owned seam over the channel/data/notification services (features may not import core:services; the app layer adapts it).
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID

/**
 * Everything the channel create/join/share sheets need from the connected device, its channel service
 * and the data store (the iOS `appState.connectedDevice`, `services.channelService` and `dataStore`).
 */
interface ChannelSetupPort {
    /** Radio of the connected device; null when no device is connected. */
    val connectedRadioId: RadioId?

    /** Channel slot count the connected device supports (iOS `connectedDevice.maxChannels`). */
    val maxChannels: Int

    /** False when radio services are not available (iOS `appState.services == nil`). */
    val servicesAvailable: Boolean

    suspend fun fetchChannels(radioId: RadioId): List<ChannelDTO>
    suspend fun fetchChannel(radioId: RadioId, index: UByte): ChannelDTO?

    /** `ChannelService.setChannelWithSecret`; the secret must be exactly 16 bytes. */
    suspend fun setChannelWithSecret(radioId: RadioId, index: UByte, name: String, secret: Bytes)

    /** `ChannelService.setChannel`: the secret is the SHA-256 prefix of [passphrase]. */
    suspend fun setChannel(radioId: RadioId, index: UByte, name: String, passphrase: String)

    /** `ChannelService.setupPublicChannel`. */
    suspend fun setupPublicChannel(radioId: RadioId)

    /** `dataStore.setChannelFloodScope`: persists the local flood-scope preference. */
    suspend fun setChannelFloodScope(channelId: UUID, scope: ChannelFloodScope)

    /** Cryptographically random 16-byte channel secret. */
    fun generateSecret(): Bytes

    /** `ChannelService.exportChannelURI`; empty when the components cannot be encoded. */
    fun exportChannelUri(name: String, secret: Bytes, floodScope: ChannelFloodScope): String
}

/** Channel-info operations beyond create/join (iOS `ChannelInfoSheet`). */
interface ChannelInfoPort {
    val defaultFloodScopeName: String?
    val knownRegions: List<String>

    suspend fun setNotificationLevel(channel: ChannelDTO, level: NotificationLevel)
    suspend fun setFavorite(channel: ChannelDTO, isFavorite: Boolean)

    /** Clears the channel on the device and removes it locally. */
    suspend fun clearChannel(radioId: RadioId, index: UByte)
    suspend fun clearChannelMessages(radioId: RadioId, index: UByte)
    suspend fun removeDeliveredNotifications(radioId: RadioId, channelIndex: UByte)

    /** Pushes the resolved flood scope for [scope] to the radio (best effort, `ChannelFloodScopeResolver`). */
    suspend fun applyFloodScopeToRadio(scope: ChannelFloodScope)

    fun addKnownRegion(region: String)
    fun removeKnownRegion(region: String)

    suspend fun discoverRegions(radioId: RadioId, knownRegions: List<String>): RegionDiscoveryOutcome
}

/** Result of a nearby-repeater region discovery (iOS `RegionDiscoveryService.Outcome`). */
sealed interface RegionDiscoveryOutcome {
    data object SendFailed : RegionDiscoveryOutcome
    data object NoRepeatersResponded : RegionDiscoveryOutcome
    data object ErrorLoadingRepeaters : RegionDiscoveryOutcome
    data class Completed(val newRegions: List<String>, val allRepeatersTableFull: Boolean) : RegionDiscoveryOutcome
}

/** Why a sheet action failed; the Compose layer maps each to its localized copy. */
sealed interface ChannelSheetError {
    data object NoDeviceConnected : ChannelSheetError
    data object ServicesUnavailable : ChannelSheetError
    data object InvalidFormat : ChannelSheetError
    data object NoSlots : ChannelSheetError
    data object InvalidName : ChannelSheetError
    data object LoadFailed : ChannelSheetError

    /** A service failure; [message] is the error's user-facing text when it has one. */
    data class Failure(val message: String?) : ChannelSheetError
}

/** How a sheet action finished: the sheet completes with a (possibly absent) channel, or stays open. */
sealed interface ChannelSheetResult {
    data class Completed(val channel: ChannelDTO?) : ChannelSheetResult
    data object Stayed : ChannelSheetResult
}
