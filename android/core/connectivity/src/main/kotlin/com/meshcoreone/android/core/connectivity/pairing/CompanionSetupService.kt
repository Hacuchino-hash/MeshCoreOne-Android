// PortedFrom: MC1Services/Sources/MC1Services/Services/AccessorySetupKitService.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/AccessorySetupKitServicing.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.connectivity.pairing

import com.meshcoreone.android.core.connectivity.ConnectivityClock
import com.meshcoreone.android.core.connectivity.ConnectivityDiagnostics
import java.util.UUID
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** One CompanionDeviceManager association owned by this app. */
data class CompanionAssociation(
    val associationId: Int,
    val deviceId: UUID,
    val address: String,
    val displayName: String,
) {
    val endpoint: BluetoothEndpoint get() = BluetoothEndpoint(deviceId, address, associationId.takeIf { it >= 0 })
}

sealed class CompanionSetupError(message: String) : Exception(message) {
    class SessionNotActive : CompanionSetupError("Bluetooth is not ready.")
    class SessionInvalidated : CompanionSetupError("Companion session ended unexpectedly.")
    class PickerDismissed : CompanionSetupError("Device selection was cancelled.")
    class PickerRestricted : CompanionSetupError("Cannot show device picker.")
    class PickerAlreadyActive : CompanionSetupError("Device picker is already showing.")
    class PairingFailed(val reason: String) : CompanionSetupError("Pairing failed: $reason")
    class NoBluetoothIdentifier : CompanionSetupError("Selected device does not support Bluetooth connection.")
    class DiscoveryTimeout : CompanionSetupError("No devices found.")
    class ConnectionFailed : CompanionSetupError("Could not connect to the device.")
    /** Parity with a declined removal confirmation; CompanionDeviceManager itself never asks. */
    class UserCancelled : CompanionSetupError("Removal was cancelled.")
}

interface CompanionSetupDelegate {
    fun companionSetupDidRemoveAccessory(deviceId: UUID)
    fun companionSetupDidFailPairing(deviceId: UUID)
}

/** Test-injection seam over the companion association service (AccessorySetupKitServicing). */
interface CompanionSetupServicing {
    val pairedAccessories: List<CompanionAssociation>
    val isSessionActive: Boolean
    var delegate: CompanionSetupDelegate?
    suspend fun activateSession()
    suspend fun showPicker(): UUID
    suspend fun removeAccessory(accessory: CompanionAssociation)
    suspend fun renameAccessory(accessory: CompanionAssociation)
    fun accessory(deviceId: UUID): CompanionAssociation?
    fun invalidateSession()
}

/** Results of one chooser request, delivered by the platform gateway on any thread. */
interface CompanionAssociationEvents {
    /** The chooser must be launched by the foreground UI host (IntentSender). */
    fun onChooserPending(chooser: Any)
    fun onAssociationCreated(association: CompanionAssociation)
    fun onDismissed()
    fun onFailure(error: CompanionSetupError, failedAssociation: CompanionAssociation? = null)
}

/** CompanionDeviceManager boundary. Association never opens GATT and never starts a service. */
interface CompanionDeviceGateway {
    val isSupported: Boolean
    fun associations(): List<CompanionAssociation>
    fun associate(events: CompanionAssociationEvents)
    suspend fun disassociate(association: CompanionAssociation)
    /** Presence observation binds `CompanionDeviceService` when the radio is near; it is not a connection. */
    fun observePresence(association: CompanionAssociation, observe: Boolean)
}

/** The foreground UI host that can present the system chooser; absent while backgrounded. */
fun interface CompanionChooserHost {
    fun launch(chooser: Any)
}

/**
 * Companion association session (AccessorySetupKitService). Activation snapshots the app's
 * associations; the picker is the CDM chooser; a cancelled picker whose association still
 * completes is disassociated immediately, mirroring ASK's orphaned-accessory cleanup.
 */
class CompanionSetupService(
    private val gateway: CompanionDeviceGateway,
    private val scope: CoroutineScope,
    private val clock: ConnectivityClock,
    private val diagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
    chooserHost: CompanionChooserHost? = null,
) : CompanionSetupServicing {
    private val lock = Any()
    private var paired: List<CompanionAssociation> = emptyList()
    private var active = false
    private var picker: CancellableContinuation<UUID>? = null
    private var pickerOutcome = "cancelled"
    private var pickerPresentedAt: Duration? = null
    private var host: CompanionChooserHost? = chooserHost

    override var delegate: CompanionSetupDelegate? = null
    override val pairedAccessories: List<CompanionAssociation> get() = synchronized(lock) { paired }
    override val isSessionActive: Boolean get() = synchronized(lock) { active }
    val lastPickerOutcome: String get() = synchronized(lock) { pickerOutcome }

    /** Attach/detach the resumed activity able to launch the chooser IntentSender. */
    fun setChooserHost(value: CompanionChooserHost?) { synchronized(lock) { host = value } }

    override suspend fun activateSession() {
        if (synchronized(lock) { active }) return
        if (!gateway.isSupported) throw CompanionSetupError.SessionNotActive()
        val current = gateway.associations()
        synchronized(lock) {
            paired = current
            active = true
        }
        current.forEach { observe(it, true) }
    }

    /**
     * Re-reads associations (foreground/presence). Associations removed outside the app (Settings)
     * are reported once to the delegate, like ASK `accessoryRemoved`.
     */
    fun refreshAssociations() {
        if (!synchronized(lock) { active }) return
        val current = gateway.associations()
        val (removed, added) = synchronized(lock) {
            val gone = paired.filter { old -> current.none { it.deviceId == old.deviceId } }
            val new = current.filter { now -> paired.none { it.deviceId == now.deviceId } }
            paired = current
            gone to new
        }
        added.forEach { observe(it, true) }
        removed.forEach { notify("refresh.removed") { delegate?.companionSetupDidRemoveAccessory(it.deviceId) } }
    }

    override suspend fun showPicker(): UUID {
        val chooser = synchronized(lock) {
            if (!active) throw CompanionSetupError.SessionNotActive()
            if (picker != null) throw CompanionSetupError.PickerAlreadyActive()
            host ?: throw CompanionSetupError.PickerRestricted()
        }
        return suspendCancellableCoroutine { pending ->
            synchronized(lock) {
                picker = pending
                pickerOutcome = "presented"
                pickerPresentedAt = clock.elapsed
            }
            val events = Events(pending, chooser)
            pending.invokeOnCancellation { handlePickerCancellation(pending, events) }
            try {
                gateway.associate(events)
            } catch (failure: Exception) {
                diagnostics.report("companion.associate", failure)
                resumePicker(pending, Result.failure(CompanionSetupError.PickerRestricted()), "pickerRestricted")
            }
        }
    }

    override suspend fun removeAccessory(accessory: CompanionAssociation) {
        if (!synchronized(lock) { active }) throw CompanionSetupError.SessionNotActive()
        observe(accessory, false)
        gateway.disassociate(accessory)
        val current = gateway.associations()
        synchronized(lock) { paired = current }
    }

    /** CompanionDeviceManager has no system rename surface; callers hide the action. */
    override suspend fun renameAccessory(accessory: CompanionAssociation) {
        if (!synchronized(lock) { active }) throw CompanionSetupError.SessionNotActive()
    }

    override fun accessory(deviceId: UUID): CompanionAssociation? =
        synchronized(lock) { paired.firstOrNull { it.deviceId == deviceId } }

    override fun invalidateSession() {
        val pending = synchronized(lock) {
            active = false
            paired = emptyList()
            picker.also { picker = null }
        }
        pending?.resumeWith(Result.failure(CompanionSetupError.SessionInvalidated()))
    }

    private inner class Events(
        private val pending: CancellableContinuation<UUID>,
        private val host: CompanionChooserHost,
    ) : CompanionAssociationEvents {
        /** Set when the awaiting coroutine was cancelled while this chooser request was outstanding. */
        @Volatile var abandoned = false

        override fun onChooserPending(chooser: Any) {
            try { host.launch(chooser) }
            catch (failure: Exception) {
                diagnostics.report("companion.chooser", failure)
                resumePicker(pending, Result.failure(CompanionSetupError.PickerRestricted()), "pickerRestricted")
            }
        }

        override fun onAssociationCreated(association: CompanionAssociation) {
            val orphan = synchronized(lock) {
                if (abandoned) {
                    pickerOutcome = "orphanedAfterCancellation"
                    true
                } else false
            }
            if (orphan) {
                scope.launch {
                    try { gateway.disassociate(association); synchronized(lock) { paired = gateway.associations() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { diagnostics.report("companion.orphanRemoval", failure) }
                }
                return
            }
            val current = try { gateway.associations() } catch (failure: Exception) {
                diagnostics.report("companion.associations", failure); listOf(association)
            }
            synchronized(lock) { paired = if (current.any { it.deviceId == association.deviceId }) current else current + association }
            observe(association, true)
            diagnostics.report(CompanionLogFormatter.selectionMessage(association.displayName, association.deviceId, elapsed()), null)
            resumePicker(pending, Result.success(association.deviceId), "selected")
        }

        override fun onDismissed() {
            val outcome = synchronized(lock) { pickerOutcome }
            diagnostics.report(
                CompanionLogFormatter.dismissalMessage(outcome, pairedAccessories.size, elapsed(),
                    CompanionDiscoveryCriteria.usesFilteredDiscovery),
                null,
            )
            synchronized(lock) { pickerPresentedAt = null }
            resumePicker(pending, Result.failure(CompanionSetupError.PickerDismissed()), "cancelled")
        }

        override fun onFailure(error: CompanionSetupError, failedAssociation: CompanionAssociation?) {
            if (failedAssociation != null && error is CompanionSetupError.PairingFailed) {
                notify("pairingFailed") { delegate?.companionSetupDidFailPairing(failedAssociation.deviceId) }
                if (accessory(failedAssociation.deviceId) != null) {
                    scope.launch {
                        try { removeAccessory(failedAssociation) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { diagnostics.report("companion.failedRemoval", failure) }
                    }
                }
            }
            val outcome = when (error) {
                is CompanionSetupError.DiscoveryTimeout -> "discoveryTimeout"
                is CompanionSetupError.PickerRestricted -> "pickerRestricted"
                is CompanionSetupError.PairingFailed -> "pairingFailed"
                else -> "connectionFailed"
            }
            resumePicker(pending, Result.failure(error), outcome)
        }
    }

    private fun resumePicker(pending: CancellableContinuation<UUID>, result: Result<UUID>, outcome: String) {
        val owned = synchronized(lock) {
            if (picker !== pending) false
            else {
                picker = null
                pickerOutcome = outcome
                true
            }
        }
        if (owned) pending.resumeWith(result)
    }

    /**
     * The awaiting coroutine was cancelled; CompanionDeviceManager has no programmatic chooser
     * dismissal, so a selection the user still completes is disassociated as an orphan.
     */
    private fun handlePickerCancellation(pending: CancellableContinuation<UUID>, events: Events) {
        synchronized(lock) {
            if (pickerOutcome == "selected") pickerOutcome = "cancelledAfterSelection"
            if (picker !== pending) return
            picker = null
            events.abandoned = true
        }
    }

    private fun observe(association: CompanionAssociation, enabled: Boolean) {
        try { gateway.observePresence(association, enabled) }
        catch (failure: Exception) { diagnostics.report("companion.observePresence", failure) }
    }

    private fun elapsed(): Duration? = synchronized(lock) { pickerPresentedAt }?.let { clock.elapsed - it }

    private inline fun notify(operation: String, action: () -> Unit) {
        try { action() } catch (failure: Exception) { diagnostics.report("companion.delegate.$operation", failure) }
    }
}
