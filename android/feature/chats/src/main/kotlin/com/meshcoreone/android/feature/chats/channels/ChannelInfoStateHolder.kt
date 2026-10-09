// PortedFrom: MC1/Views/Chats/Sheets/ChannelInfoSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.NotificationLevel
import com.meshcoreone.android.core.model.RadioId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ChannelTypeLabel { PUBLIC, HASHTAG, PRIVATE }

/** Why a region discovery run produced no new regions (copy resolved in the UI). */
enum class RegionDiscoveryMessage { NO_NEW_REGIONS, NO_REPEATERS_RESPONDED, ERROR_LOADING_REPEATERS, RADIO_CONTACTS_FULL }

data class ChannelInfoState(
    val notificationLevel: NotificationLevel,
    val isFavorite: Boolean,
    val selectedFloodScope: ChannelFloodScope,
    val isDeleting: Boolean = false,
    val isClearingMessages: Boolean = false,
    val error: ChannelSheetError? = null,
    val isDiscoveringRegions: Boolean = false,
    val discoveryMessage: RegionDiscoveryMessage? = null,
    val discoveredNewRegions: List<String> = emptyList(),
) {
    val isActionInProgress: Boolean get() = isDeleting || isClearingMessages
}

/** Result of a destructive action: the sheet dismisses and the caller reacts, or stays open on failure. */
enum class ChannelInfoOutcome { DELETED, MESSAGES_CLEARED, FAILED }

/** Share/QR/secret, region scope, notification and delete behavior of the channel info sheet. */
class ChannelInfoStateHolder(
    private val channel: ChannelDTO,
    private val setup: ChannelSetupPort,
    private val info: ChannelInfoPort,
    private val onDiagnostic: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(
        ChannelInfoState(channel.notificationLevel, channel.isFavorite, channel.floodScope),
    )
    val state: StateFlow<ChannelInfoState> = mutable.asStateFlow()

    val typeLabel: ChannelTypeLabel
        get() = when {
            channel.isPublicChannel -> ChannelTypeLabel.PUBLIC
            channel.name.startsWith("#") -> ChannelTypeLabel.HASHTAG
            else -> ChannelTypeLabel.PRIVATE
        }

    /** QR and secret-key sections are only for private channels that carry a secret. */
    val showsShareSections: Boolean get() = channel.hasSecret && !channel.isPublicChannel
    val knownRegions: List<String> get() = info.knownRegions
    val deviceDefaultFloodScopeName: String? get() = info.defaultFloodScopeName

    /** The QR payload follows the currently selected flood scope. */
    fun qrUri(): String =
        setup.exportChannelUri(channel.name, channel.secret, mutable.value.selectedFloodScope)

    suspend fun setNotificationLevel(level: NotificationLevel) {
        mutable.update { it.copy(notificationLevel = level) }
        info.setNotificationLevel(channel, level)
    }

    suspend fun setFavorite(isFavorite: Boolean) {
        mutable.update { it.copy(isFavorite = isFavorite) }
        info.setFavorite(channel, isFavorite)
    }

    /** Persists the preference first (reverting on failure), then pushes it to the radio best-effort. */
    suspend fun selectFloodScope(scope: ChannelFloodScope) {
        val previous = mutable.value.selectedFloodScope
        mutable.update { it.copy(selectedFloodScope = scope) }
        try {
            setup.setChannelFloodScope(channel.id, scope)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onDiagnostic("Failed to save flood scope", failure)
            mutable.update { it.copy(selectedFloodScope = previous) }
            return
        }
        try {
            info.applyFloodScopeToRadio(scope)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // `try?`: the radio push is best effort.
        }
    }

    suspend fun deleteChannel(): ChannelInfoOutcome =
        runDestructive(deleting = true) { radioId ->
            info.clearChannel(radioId, channel.index)
            info.removeDeliveredNotifications(radioId, channel.index)
            ChannelInfoOutcome.DELETED
        }

    suspend fun clearMessages(): ChannelInfoOutcome =
        runDestructive(deleting = false) { radioId ->
            info.clearChannelMessages(radioId, channel.index)
            info.removeDeliveredNotifications(radioId, channel.index)
            ChannelInfoOutcome.MESSAGES_CLEARED
        }

    private suspend fun runDestructive(
        deleting: Boolean,
        action: suspend (RadioId) -> ChannelInfoOutcome,
    ): ChannelInfoOutcome {
        val radioId = setup.connectedRadioId
        if (radioId == null) return failed(ChannelSheetError.NoDeviceConnected)
        if (!setup.servicesAvailable) return failed(ChannelSheetError.ServicesUnavailable)
        mutable.update {
            if (deleting) it.copy(isDeleting = true, error = null) else it.copy(isClearingMessages = true, error = null)
        }
        return try {
            action(radioId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            mutable.update {
                it.copy(error = failure.asSheetError(), isDeleting = false, isClearingMessages = false)
            }
            ChannelInfoOutcome.FAILED
        }
    }

    private fun failed(error: ChannelSheetError): ChannelInfoOutcome {
        mutable.update { it.copy(error = error) }
        return ChannelInfoOutcome.FAILED
    }

    /** Discovers nearby repeaters' regions; the caller shows the results screen when [state] has new regions. */
    suspend fun discoverRegions() {
        mutable.update { it.copy(isDiscoveringRegions = true, discoveryMessage = null, discoveredNewRegions = emptyList()) }
        try {
            val outcome = info.discoverRegions(channel.radioId, info.knownRegions)
            val newRegions = (outcome as? RegionDiscoveryOutcome.Completed)?.newRegions.orEmpty()
            val message = when {
                newRegions.isNotEmpty() -> null
                outcome == RegionDiscoveryOutcome.NoRepeatersResponded -> RegionDiscoveryMessage.NO_REPEATERS_RESPONDED
                outcome == RegionDiscoveryOutcome.ErrorLoadingRepeaters -> RegionDiscoveryMessage.ERROR_LOADING_REPEATERS
                outcome is RegionDiscoveryOutcome.Completed && outcome.allRepeatersTableFull ->
                    RegionDiscoveryMessage.RADIO_CONTACTS_FULL
                else -> RegionDiscoveryMessage.NO_NEW_REGIONS
            }
            mutable.update { it.copy(discoveredNewRegions = newRegions, discoveryMessage = message) }
        } finally {
            mutable.update { it.copy(isDiscoveringRegions = false) }
        }
    }

    fun addDiscoveredRegions(regions: Collection<String>) = regions.forEach(info::addKnownRegion)
    fun addKnownRegion(region: String) = info.addKnownRegion(region)
    fun removeKnownRegion(region: String) = info.removeKnownRegion(region)
}
