// PortedFrom: MC1/Utilities/ChannelJoinFloodScopeApplier.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinChannelConfirmationSheet.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinHashtagFromMessageView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/ScanChannelQRView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.ProtocolLimits
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.uppercaseHexString
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.chats.list.ChannelLinkResult
import com.meshcoreone.android.feature.chats.list.MeshCoreLinkParser
import com.meshcoreone.android.feature.chats.list.MeshCoreLinkParsing
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Best-effort local flood-scope preference after a channel join from a URL/QR code. */
object ChannelJoinFloodScopeApplier {
    /** `.Region(name)` for a non-empty normalized scope; otherwise null (no write). */
    fun preferredFloodScope(regionScope: String?): ChannelFloodScope? =
        MeshCoreLinkParser.normalizedRegionScope(regionScope)?.let(ChannelFloodScope::Region)

    /** On a store failure the original channel is returned so the radio join still completes. */
    suspend fun applyIfNeeded(
        channel: ChannelDTO,
        regionScope: String?,
        setFloodScope: suspend (UUID, ChannelFloodScope) -> Unit,
        onFailure: (Exception) -> Unit = {},
    ): ChannelDTO {
        val preferred = preferredFloodScope(regionScope) ?: return channel
        return try {
            setFloodScope(channel.id, preferred)
            channel.withFloodScope(preferred)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onFailure(failure)
            channel
        }
    }
}

/** Secret derivation and display rules of a channel link/QR result. */
object ChannelLinkPresentation {
    /** `sha256(passphrase)[0:16]`; all zeros for an empty passphrase (`ChannelService.hashSecret`). */
    fun hashSecret(passphrase: String): Bytes {
        if (passphrase.isEmpty()) return Bytes(ByteArray(ProtocolLimits.CHANNEL_SECRET_SIZE))
        val digest = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))
        return Bytes(digest.copyOf(ProtocolLimits.CHANNEL_SECRET_SIZE))
    }

    /** True when [name] is a valid hashtag shape and [secret] is not the public name hash. */
    fun hasHashtagSecretMismatch(name: String, secret: Bytes): Boolean {
        if (!name.startsWith("#")) return false
        val body = name.drop(1)
        if (!ChannelInput.isValidHashtagName(body)) return false
        val expected = hashSecret("#" + body.lowercase(Locale.ROOT))
        return !secret.toByteArray().contentEquals(expected.toByteArray())
    }

    /** First and last 8 hex digits for a 16+ digit secret (`truncatedSecret`). */
    fun truncatedSecret(secret: Bytes): String {
        val hex = secret.uppercaseHexString()
        return if (hex.length >= 16) hex.take(8) + "..." + hex.takeLast(8) else hex
    }
}

data class ChannelJoinConfirmationState(
    val isLoading: Boolean = true,
    val availableSlots: List<UByte> = emptyList(),
    val isMissingDevice: Boolean = false,
    val isJoining: Boolean = false,
    val error: ChannelSheetError? = null,
    val joinSucceeded: Boolean = false,
)

/** What the confirmation sheet shows once loading is done. */
enum class ChannelJoinContent { LOADING, MISSING_DEVICE, NO_SLOTS, CONFIRM }

val ChannelJoinConfirmationState.content: ChannelJoinContent
    get() = when {
        isLoading -> ChannelJoinContent.LOADING
        isMissingDevice -> ChannelJoinContent.MISSING_DEVICE
        availableSlots.isEmpty() -> ChannelJoinContent.NO_SLOTS
        else -> ChannelJoinContent.CONFIRM
    }

private suspend fun ChannelSetupPort.loadFreeSlots(): Pair<RadioId?, List<UByte>> {
    val radioId = connectedRadioId ?: return null to emptyList()
    return try {
        radioId to ChannelInput.availableSlots(maxChannels, fetchChannels(radioId).map { it.index }.toSet())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        radioId to emptyList()
    }
}

/** Confirmation sheet for a `meshcore://channel/add` link tapped in a message. */
class ChannelJoinConfirmationStateHolder(
    private val port: ChannelSetupPort,
    private val link: ChannelLinkResult,
    private val onDiagnostic: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(ChannelJoinConfirmationState())
    val state: StateFlow<ChannelJoinConfirmationState> = mutable.asStateFlow()

    val hasHashtagSecretMismatch: Boolean get() = ChannelLinkPresentation.hasHashtagSecretMismatch(link.name, link.secret)
    val truncatedSecret: String get() = ChannelLinkPresentation.truncatedSecret(link.secret)

    suspend fun loadAvailableSlots() {
        val (radioId, slots) = port.loadFreeSlots()
        mutable.update { it.copy(isLoading = false, isMissingDevice = radioId == null, availableSlots = slots) }
    }

    suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId ?: return ChannelSheetResult.Stayed
        if (!port.servicesAvailable) return fail(ChannelSheetError.NoDeviceConnected)
        val slot = mutable.value.availableSlots.firstOrNull() ?: return fail(ChannelSheetError.NoSlots)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            port.setChannelWithSecret(radioId, slot, link.name, link.secret)
            val joined = port.fetchChannel(radioId, slot) ?: return fail(ChannelSheetError.LoadFailed)
            val completed = ChannelJoinFloodScopeApplier.applyIfNeeded(
                joined, link.regionScope, port::setChannelFloodScope,
            ) { onDiagnostic("Failed to persist channel flood scope after join", it) }
            mutable.update { it.copy(joinSucceeded = true) }
            return ChannelSheetResult.Completed(completed)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onDiagnostic("Failed to join channel from link", failure)
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

/** Join sheet for a hashtag tapped in a message (`meshcoreone://hashtag/<name>`). */
class JoinHashtagFromMessageStateHolder(
    private val port: ChannelSetupPort,
    channelName: String,
    private val onDiagnostic: (String, Throwable?) -> Unit = { _, _ -> },
) {
    /** Lowercased name without `#`, and the full `#name` the channel is created with. */
    val normalizedName: String = channelName.lowercase(Locale.ROOT).removePrefix("#")
    val fullChannelName: String = "#$normalizedName"

    private val mutable = MutableStateFlow(ChannelJoinConfirmationState())
    val state: StateFlow<ChannelJoinConfirmationState> = mutable.asStateFlow()

    suspend fun loadAvailableSlots() {
        val (radioId, slots) = port.loadFreeSlots()
        mutable.update { it.copy(isLoading = false, isMissingDevice = radioId == null, availableSlots = slots) }
    }

    suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId ?: return fail(ChannelSheetError.NoDeviceConnected)
        if (!port.servicesAvailable) return fail(ChannelSheetError.ServicesUnavailable)
        val slot = mutable.value.availableSlots.firstOrNull() ?: return fail(ChannelSheetError.NoSlots)
        if (!ChannelInput.isValidHashtagName(normalizedName)) return fail(ChannelSheetError.InvalidName)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            port.setChannel(radioId, slot, fullChannelName, fullChannelName)
            val joined = port.fetchChannel(radioId, slot) ?: return fail(ChannelSheetError.LoadFailed)
            mutable.update { it.copy(joinSucceeded = true) }
            return ChannelSheetResult.Completed(joined)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onDiagnostic("Failed to join channel", failure)
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

data class ScanChannelQrState(
    val scannedChannel: ChannelLinkResult? = null,
    val selectedSlot: UByte = 1u,
    val isJoining: Boolean = false,
    val error: ScanChannelQrError? = null,
    val cameraPermissionDenied: Boolean = false,
)

/** A QR failure is either a malformed payload or a join failure. */
sealed interface ScanChannelQrError {
    data object InvalidFormat : ScanChannelQrError
    data class Join(val error: ChannelSheetError) : ScanChannelQrError
}

/** Scan-QR-to-join flow. The camera preview itself is platform code; this owns scan result handling and the join. */
class ScanChannelQrStateHolder(
    private val port: ChannelSetupPort,
    availableSlots: List<UByte>,
    private val parser: MeshCoreLinkParsing = MeshCoreLinkParser,
    private val onDiagnostic: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(ScanChannelQrState(selectedSlot = ChannelInput.defaultSlot(availableSlots)))
    val state: StateFlow<ScanChannelQrState> = mutable.asStateFlow()

    fun onScanResult(payload: String) {
        val parsed = parser.parseChannelURL(payload)
        mutable.update {
            if (parsed == null) it.copy(error = ScanChannelQrError.InvalidFormat) else it.copy(scannedChannel = parsed)
        }
    }

    fun onCameraPermissionDenied() = mutable.update { it.copy(cameraPermissionDenied = true) }

    fun scanAgain() = mutable.update { it.copy(scannedChannel = null, error = null) }

    suspend fun join(): ChannelSheetResult {
        val radioId = port.connectedRadioId
        val scanned = mutable.value.scannedChannel
        if (radioId == null || scanned == null) return fail(ChannelSheetError.NoDeviceConnected)
        mutable.update { it.copy(isJoining = true, error = null) }
        try {
            if (!port.servicesAvailable) return fail(ChannelSheetError.ServicesUnavailable)
            val slot = mutable.value.selectedSlot
            port.setChannelWithSecret(radioId, slot, scanned.name, scanned.secret)
            val joined = port.fetchChannelAt(radioId, slot) ?: return ChannelSheetResult.Completed(null)
            val completed = ChannelJoinFloodScopeApplier.applyIfNeeded(
                joined, scanned.regionScope, port::setChannelFloodScope,
            ) { onDiagnostic("Failed to persist channel flood scope after QR join", it) }
            return ChannelSheetResult.Completed(completed)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return fail(failure.asSheetError())
        } finally {
            mutable.update { it.copy(isJoining = false) }
        }
    }

    private fun fail(error: ChannelSheetError): ChannelSheetResult {
        mutable.update { it.copy(error = ScanChannelQrError.Join(error)) }
        return ChannelSheetResult.Stayed
    }
}
