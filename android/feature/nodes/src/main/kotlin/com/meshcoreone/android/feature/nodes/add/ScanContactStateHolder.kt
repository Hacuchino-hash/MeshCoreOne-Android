// PortedFrom: MC1/Views/Contacts/ScanContactQRView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.nodes.add

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.ContactFrame
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import com.meshcoreone.android.feature.nodes.deps.NodesContactFailure
import com.meshcoreone.android.feature.nodes.deps.NodesFeatureDependencies
import com.meshcoreone.android.feature.nodes.deps.NodesMessage
import com.meshcoreone.android.feature.nodes.deps.ScannedContact
import com.meshcoreone.android.feature.nodes.model.localizedNameRes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanContactState(
    val scannedContact: ScannedContact? = null,
    val existingContact: ContactDTO? = null,
    val isResolvingScan: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: NodesMessage? = null,
    val cameraPermissionDenied: Boolean = false,
    /** Haptic triggers: bumped on a parsed scan, a successful import and a failure. */
    val selectionFeedback: Int = 0,
    val successFeedback: Int = 0,
    val errorFeedback: Int = 0,
) {
    val title: NodesMessage
        get() = when {
            scannedContact == null -> NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_title)
            existingContact != null -> NodesMessage.Text(existingContact.displayName)
            else -> NodesMessage.res(R.string.l10n_app_contacts_contacts_add_nodetitle)
        }

    fun confirmation(canScanAgain: Boolean = true): ContactAddConfirmation? = scannedContact?.let {
        ContactAddConfirmation(it, existingContact, errorMessage, isImporting, canScanAgain)
    }
}

/** Outcome of the confirmation's primary button. */
sealed interface ScanConfirmOutcome {
    /** Navigate to this contact's detail and dismiss the scanner. */
    data class Completed(val contact: ContactDTO) : ScanConfirmOutcome
    /** Stay on the review (failure shown, or nothing to confirm). */
    data object Stay : ScanConfirmOutcome
}

/**
 * QR contact scanning flow. Decoding camera frames needs an admitted scanner (CameraX + ZXing) and
 * stays in the UI layer, which calls [handleScanResult] with each decoded string.
 */
class ScanContactStateHolder(
    private val dependencies: NodesFeatureDependencies,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ScanContactState())
    val state: StateFlow<ScanContactState> = mutableState.asStateFlow()
    private val session get() = dependencies.session

    fun onCameraPermissionDenied() = mutableState.update { it.copy(cameraPermissionDenied = true) }
    fun dismissError() = mutableState.update { it.copy(errorMessage = null) }

    /** Handles one decoded QR string; later callbacks are ignored once a scan is claimed. */
    fun handleScanResult(result: String) {
        val current = state.value
        if (current.scannedContact != null || current.isImporting || current.isResolvingScan) return
        val parsed = dependencies.uriCodec.parseContactUri(result)
        if (parsed == null) {
            mutableState.update {
                it.copy(errorMessage = NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_error_invalidformat), errorFeedback = it.errorFeedback + 1)
            }
            return
        }
        // Claim the scan before the lookup so a second callback cannot present another contact.
        mutableState.update { it.copy(selectionFeedback = it.selectionFeedback + 1, errorMessage = null, isResolvingScan = true) }
        scope.launch { presentScannedContact(parsed) }
    }

    /** Resolves the saved row first so the first paint is View, not Add. */
    private suspend fun presentScannedContact(parsed: ScannedContact) {
        try {
            val current = state.value
            if (current.scannedContact != null || current.isImporting) return
            val existing = fetchExistingContact(parsed.publicKey)
            mutableState.update { it.copy(existingContact = existing, scannedContact = parsed) }
            val announcement = if (existing != null) {
                NodesMessage.Joined(listOf(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_alreadyadded), NodesMessage.Text(existing.displayName)), ", ")
            } else {
                NodesMessage.Joined(listOf(NodesMessage.Text(parsed.name), NodesMessage.res(parsed.contactType.localizedNameRes())), ", ")
            }
            dependencies.announcer.announce(announcement)
        } finally {
            mutableState.update { it.copy(isResolvingScan = false) }
        }
    }

    private suspend fun fetchExistingContact(publicKey: Bytes): ContactDTO? {
        val radioId = session.currentRadioId() ?: return null
        val store = session.servicesDataStore() ?: session.offlineDataStore() ?: return null
        return try {
            store.fetchContact(radioId, publicKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun resetToScanner() = mutableState.update {
        it.copy(scannedContact = null, existingContact = null, errorMessage = null, isImporting = false, isResolvingScan = false)
    }

    /** Primary button: open a saved contact, or import the scanned one after this explicit confirmation. */
    suspend fun confirm(): ScanConfirmOutcome {
        val current = state.value
        current.existingContact?.let { return ScanConfirmOutcome.Completed(it) }
        val scanned = current.scannedContact ?: return ScanConfirmOutcome.Stay
        return importContact(scanned)
    }

    private suspend fun importContact(contact: ScannedContact): ScanConfirmOutcome {
        if (state.value.isImporting) return ScanConfirmOutcome.Stay
        val contactService = session.contactService()
        val device = session.connectedDevice()
        if (contactService == null || device == null) {
            presentImportFailure(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_error_notconnected))
            return ScanConfirmOutcome.Stay
        }
        mutableState.update { it.copy(isImporting = true, errorMessage = null) }
        try {
            val frame = ContactFrame(
                publicKey = contact.publicKey, type = contact.contactType, flags = 0u, outPathLength = PacketBuilder.FLOOD_PATH_SENTINEL,
                outPath = Bytes.EMPTY, name = contact.name, lastAdvertTimestamp = 0u, latitude = 0.0, longitude = 0.0,
                lastModified = dependencies.clock.wallNow.epochSecond.toUInt(),
            )
            contactService.addOrUpdateContact(device.radioId, frame)
            // The detail screen takes a stored row; the QR payload is not one.
            val added = session.servicesDataStore()?.fetchContact(device.radioId, contact.publicKey)
            if (added == null) {
                presentImportFailure(NodesMessage.res(R.string.l10n_app_contacts_contacts_common_erroroccurred))
                mutableState.update { it.copy(isImporting = false) }
                return ScanConfirmOutcome.Stay
            }
            mutableState.update { it.copy(successFeedback = it.successFeedback + 1) }
            dependencies.announcer.announce(NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_accessibility_added, contact.name))
            return ScanConfirmOutcome.Completed(added)
        } catch (_: NodesContactFailure.ContactTableFull) {
            presentImportFailure(NodesMessage.res(R.string.l10n_app_contacts_contacts_add_error_nodelistfull, device.maxContacts.toInt()))
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(isImporting = false) }
            throw cancelled
        } catch (error: Exception) {
            presentImportFailure(NodesMessage.res(R.string.l10n_app_contacts_contacts_scan_error_importfailed, dependencies.messages.message(error)))
        }
        mutableState.update { it.copy(isImporting = false) }
        return ScanConfirmOutcome.Stay
    }

    private fun presentImportFailure(message: NodesMessage) {
        mutableState.update { it.copy(errorMessage = message, errorFeedback = it.errorFeedback + 1) }
        dependencies.announcer.announce(message)
    }
}
