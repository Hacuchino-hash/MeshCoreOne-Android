// PortedFrom: MC1/Views/Chats/CreatePrivateChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinPrivateChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinPublicChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinHashtagChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.ChatTextMatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Fetches the channel now occupying [index]; a failed fetch yields null (the iOS `try?`). */
internal suspend fun ChannelSetupPort.fetchChannelAt(radioId: RadioId, index: UByte): ChannelDTO? =
    try {
        fetchChannels(radioId).firstOrNull { it.index == index }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

internal fun Exception.asSheetError(): ChannelSheetError = ChannelSheetError.Failure(message)

// MARK: - Create private channel

data class CreatePrivateChannelState(
    val channelName: String = "",
    val selectedSlot: UByte = 1u,
    val secret: Bytes? = null,
    val isCreating: Boolean = false,
    val createdChannel: ChannelDTO? = null,
    val error: ChannelSheetError? = null,
) {
    /** The sheet switches to the share content once the created channel was read back. */
    val isCreated: Boolean get() = createdChannel != null
    val canCreate: Boolean get() = channelName.isNotEmpty() && !isCreating && secret != null
}

/** Create-private-channel form: auto-generated secret, then share QR/secret content. */
class CreatePrivateChannelStateHolder(
    private val port: ChannelSetupPort,
    availableSlots: List<UByte>,
) {
    private val mutable = MutableStateFlow(CreatePrivateChannelState(selectedSlot = ChannelInput.defaultSlot(availableSlots)))
    val state: StateFlow<CreatePrivateChannelState> = mutable.asStateFlow()

    /** Generates the secret the first time the sheet appears. */
    fun start() = mutable.update { if (it.secret == null) it.copy(secret = port.generateSecret()) else it }

    fun setChannelName(value: String) = mutable.update { it.copy(channelName = ChannelInput.truncatedName(value)) }

    /** The QR payload the share content renders. */
    fun shareUri(): String? {
        val current = mutable.value
        val secret = current.secret ?: return null
        return port.exportChannelUri(current.channelName, secret, ChannelFloodScope.Inherit)
    }

    suspend fun create() {
        val radioId = port.connectedRadioId
        val secret = mutable.value.secret
        if (radioId == null || secret == null) {
            mutable.update { it.copy(error = ChannelSheetError.NoDeviceConnected) }
            return
        }
        mutable.update { it.copy(isCreating = true, error = null) }
        try {
            if (!port.servicesAvailable) {
                mutable.update { it.copy(error = ChannelSheetError.ServicesUnavailable) }
                return
            }
            val current = mutable.value
            port.setChannelWithSecret(radioId, current.selectedSlot, current.channelName, secret)
            val created = port.fetchChannelAt(radioId, current.selectedSlot)
            mutable.update { it.copy(createdChannel = created) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            mutable.update { it.copy(error = failure.asSheetError()) }
        } finally {
            mutable.update { it.copy(isCreating = false) }
        }
    }
}

// MARK: - Join private channel

data class JoinPrivateChannelState(
    val channelName: String = "",
    val secretKeyHex: String = "",
    val selectedSlot: UByte = 1u,
    val isJoining: Boolean = false,
    val error: ChannelSheetError? = null,
) {
    val isValidSecret: Boolean get() = ChannelInput.isValidSecretHex(secretKeyHex)

    /** Shows the invalid-secret footer only after the user typed something. */
    val showsInvalidSecretFooter: Boolean get() = secretKeyHex.isNotEmpty() && !isValidSecret
    val canJoin: Boolean get() = channelName.isNotEmpty() && isValidSecret && !isJoining
}

/** Join-private-channel form: name plus 32-hex-digit secret. */
class JoinPrivateChannelStateHolder(
    private val port: ChannelSetupPort,
    availableSlots: List<UByte>,
) {
    private val mutable = MutableStateFlow(JoinPrivateChannelState(selectedSlot = ChannelInput.defaultSlot(availableSlots)))
    val state: StateFlow<JoinPrivateChannelState> = mutable.asStateFlow()

    fun setChannelName(value: String) = mutable.update { it.copy(channelName = ChannelInput.truncatedName(value)) }
    fun setSecretKeyHex(value: String) = mutable.update { it.copy(secretKeyHex = ChannelInput.sanitizedSecretHex(value)) }

    suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId
        if (radioId == null) return fail(ChannelSheetError.NoDeviceConnected)
        // Stricter than iOS (which lets the service reject a wrong-size secret): never start a doomed radio write.
        val secret = ChannelInput.secretFromHex(mutable.value.secretKeyHex)
            ?.takeIf { it.size == ProtocolLimits.CHANNEL_SECRET_SIZE } ?: return fail(ChannelSheetError.InvalidFormat)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            if (!port.servicesAvailable) return fail(ChannelSheetError.ServicesUnavailable)
            val current = mutable.value
            port.setChannelWithSecret(radioId, current.selectedSlot, current.channelName, secret)
            return ChannelSheetResult.Completed(port.fetchChannelAt(radioId, current.selectedSlot))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return fail(failure.asSheetError())
        } finally {
            mutable.update { it.copy(isJoining = false) }
        }
    }

    private fun fail(error: ChannelSheetError): ChannelSheetResult {
        mutable.update { it.copy(error = error) }
        return ChannelSheetResult.Stayed
    }
}

// MARK: - Join public channel

data class JoinPublicChannelState(val isJoining: Boolean = false, val error: ChannelSheetError? = null)

/** Re-adds the public channel on slot 0. */
class JoinPublicChannelStateHolder(private val port: ChannelSetupPort) {
    private val mutable = MutableStateFlow(JoinPublicChannelState())
    val state: StateFlow<JoinPublicChannelState> = mutable.asStateFlow()

    suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId
        if (radioId == null) return fail(ChannelSheetError.NoDeviceConnected)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            if (!port.servicesAvailable) return fail(ChannelSheetError.ServicesUnavailable)
            port.setupPublicChannel(radioId)
            return ChannelSheetResult.Completed(port.fetchChannelAt(radioId, 0u))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return fail(failure.asSheetError())
        } finally {
            mutable.update { it.copy(isJoining = false) }
        }
    }

    private fun fail(error: ChannelSheetError): ChannelSheetResult {
        mutable.update { it.copy(error = error) }
        return ChannelSheetResult.Stayed
    }
}

// MARK: - Join hashtag channel

data class JoinHashtagChannelState(
    val channelName: String = "",
    val selectedSlot: UByte = 1u,
    val isJoining: Boolean = false,
    val error: ChannelSheetError? = null,
    val existingChannels: List<ChannelDTO> = emptyList(),
) {
    val isValidName: Boolean get() = ChannelInput.isValidHashtagName(channelName)

    /** The already-joined channel this name resolves to (case-insensitive on the full `#name`). */
    val existingChannel: ChannelDTO?
        get() = if (channelName.isEmpty()) null
        else existingChannels.firstOrNull { ChatTextMatching.caseInsensitiveEquals(it.name, "#$channelName") }

    /** The button is disabled for an invalid name, or while a join of a new channel is running. */
    val isActionEnabled: Boolean get() = isValidName && !(isJoining && existingChannel == null)
}

/** Joins a public hashtag channel (`#name`, secret derived from the name). */
class JoinHashtagChannelStateHolder(
    private val port: ChannelSetupPort,
    availableSlots: List<UByte>,
) {
    private val mutable = MutableStateFlow(JoinHashtagChannelState(selectedSlot = ChannelInput.defaultSlot(availableSlots)))
    val state: StateFlow<JoinHashtagChannelState> = mutable.asStateFlow()

    fun setChannelName(value: String) = mutable.update { it.copy(channelName = ChannelInput.sanitizedHashtagName(value)) }

    /** Fails open: a failed fetch only means an existing channel is not detected. */
    suspend fun loadExistingChannels() {
        val radioId = port.connectedRadioId ?: return
        if (!port.servicesAvailable) return
        try {
            val channels = port.fetchChannels(radioId)
            mutable.update { it.copy(existingChannels = channels) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    /** Goes to the existing channel when there is one, otherwise joins. */
    suspend fun performPrimaryAction(): ChannelSheetResult {
        if (mutable.value.isJoining) return ChannelSheetResult.Stayed
        mutable.value.existingChannel?.let { return ChannelSheetResult.Completed(it) }
        return join()
    }

    private suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId
        if (radioId == null) return fail(ChannelSheetError.NoDeviceConnected)
        if (!port.servicesAvailable) return fail(ChannelSheetError.ServicesUnavailable)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            val current = mutable.value
            // The passphrase is the full name including "#": sha256("#name")[0:16].
            port.setChannel(radioId, current.selectedSlot, "#${current.channelName}", "#${current.channelName}")
            return ChannelSheetResult.Completed(port.fetchChannelAt(radioId, current.selectedSlot))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return fail(failure.asSheetError())
        } finally {
            mutable.update { it.copy(isJoining = false) }
        }
    }

    private fun fail(error: ChannelSheetError): ChannelSheetResult {
        mutable.update { it.copy(error = error) }
        return ChannelSheetResult.Stayed
    }
}
