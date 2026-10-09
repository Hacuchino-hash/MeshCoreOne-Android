// PortedFrom: MC1/Views/Chats/Sheets/ChannelOptionsSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The destinations the options sheet offers (iOS `ChannelOption`). */
enum class ChannelOption { CREATE_PRIVATE, JOIN_PRIVATE, JOIN_PUBLIC, JOIN_HASHTAG, SCAN_QR }

data class ChannelOptionsState(
    val availableSlots: List<UByte> = emptyList(),
    val hasPublicChannel: Boolean = false,
    val isLoading: Boolean = true,
    val selectedOption: ChannelOption? = null,
) {
    val hasSlots: Boolean get() = availableSlots.isNotEmpty()

    /** Private/hashtag/QR entries need a free slot; the public entry needs slot 0 to be empty. */
    fun isEnabled(option: ChannelOption): Boolean = when (option) {
        ChannelOption.JOIN_PUBLIC -> !hasPublicChannel
        else -> hasSlots
    }

    val footer: ChannelOptionsFooter
        get() = when {
            availableSlots.isEmpty() -> ChannelOptionsFooter.NO_SLOTS
            hasPublicChannel -> ChannelOptionsFooter.HAS_PUBLIC
            else -> ChannelOptionsFooter.NONE
        }
}

enum class ChannelOptionsFooter { NONE, NO_SLOTS, HAS_PUBLIC }

/** Loads which slots are free and whether the public channel exists. */
class ChannelOptionsStateHolder(private val port: ChannelSetupPort) {
    private val mutable = MutableStateFlow(ChannelOptionsState())
    val state: StateFlow<ChannelOptionsState> = mutable.asStateFlow()

    fun select(option: ChannelOption?) = mutable.update { it.copy(selectedOption = option) }

    suspend fun load() {
        val radioId = port.connectedRadioId
        if (radioId == null) {
            mutable.update { it.copy(isLoading = false) }
            return
        }
        try {
            val used = port.fetchChannels(radioId).map { it.index }.toSet()
            mutable.update {
                it.copy(
                    hasPublicChannel = 0.toUByte() in used,
                    availableSlots = ChannelInput.availableSlots(port.maxChannels, used),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The iOS sheet shows the empty state when the load fails.
        }
        mutable.update { it.copy(isLoading = false) }
    }
}
