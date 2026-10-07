// AndroidOnly: WP-206 Explicit LE bonding and system PIN-entry tracking that AccessorySetupKit performs inside its picker.
package com.meshcoreone.android.core.connectivity.bond

import com.meshcoreone.android.core.ble.BondState
import com.meshcoreone.android.core.connectivity.ConnectivityClock
import com.meshcoreone.android.core.connectivity.pairing.DeviceEndpointIdentity
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Bond broadcasts (`ACTION_BOND_STATE_CHANGED`, `ACTION_PAIRING_REQUEST`) for one address. */
sealed interface BondEvent {
    val address: String
    data class StateChanged(override val address: String, val state: BondState, val previous: BondState, val reason: Int?) : BondEvent
    data class PairingRequested(override val address: String, val variant: Int) : BondEvent
}

/** Platform bond operations; the system dialog collects the PIN/passkey, never this app. */
interface BondGateway {
    fun bondState(address: String): BondState
    fun createBond(address: String): Boolean
    fun register(listener: (BondEvent) -> Unit): AutoCloseable
}

class BondFailure(val reason: Reason, val unbondReason: Int? = null) : Exception("bond.${reason.name}") {
    enum class Reason { CreateBondRejected, AuthenticationFailed, Canceled, RemoteDeviceDown, Removed, TimedOut, Unknown }
    /** Wrong PIN/rejected keys route to the guided pairing-failure recovery. */
    val authentication: Boolean get() = reason == Reason.AuthenticationFailed
}

sealed interface BondProgress {
    data object Idle : BondProgress
    data class Requested(val address: String) : BondProgress
    /** The system pairing dialog is collecting the radio PIN ("User entering PIN"). */
    data class PinEntry(val address: String, val variant: Int) : BondProgress
    data class Bonded(val address: String) : BondProgress
    data class Failed(val address: String, val failure: BondFailure) : BondProgress
}

/**
 * Ensures an LE bond exists before the first encrypted GATT exchange. Already-bonded devices
 * return without a platform request; an in-progress bond is awaited rather than restarted.
 * Listener registration happens before the state re-check so a fast broadcast is not lost.
 */
class BondingCoordinator(
    private val gateway: BondGateway,
    private val clock: ConnectivityClock,
    private val timeout: Duration = 60.seconds,
) {
    private val mutableProgress = MutableStateFlow<BondProgress>(BondProgress.Idle)
    val progress: StateFlow<BondProgress> = mutableProgress.asStateFlow()

    suspend fun ensureBonded(rawAddress: String) {
        val address = DeviceEndpointIdentity.canonicalAddress(rawAddress)
        if (gateway.bondState(address) == BondState.Bonded) return
        val outcome = CompletableDeferred<Unit>()
        val registration = gateway.register { event ->
            if (!event.address.equals(address, ignoreCase = true)) return@register
            when (event) {
                is BondEvent.PairingRequested -> mutableProgress.value = BondProgress.PinEntry(address, event.variant)
                is BondEvent.StateChanged -> when {
                    event.state == BondState.Bonded -> outcome.complete(Unit)
                    event.state == BondState.None && event.previous == BondState.Bonding ->
                        outcome.completeExceptionally(BondFailure(reasonFor(event.reason), event.reason))
                }
            }
        }
        try {
            when (gateway.bondState(address)) {
                BondState.Bonded -> outcome.complete(Unit)
                BondState.Bonding -> Unit
                BondState.None -> if (!gateway.createBond(address)) {
                    outcome.completeExceptionally(BondFailure(BondFailure.Reason.CreateBondRejected))
                }
            }
            if (!outcome.isCompleted) mutableProgress.value = BondProgress.Requested(address)
            coroutineScope {
                val timer = launch {
                    clock.sleep(timeout)
                    // A missed broadcast must not fail a bond the stack completed: re-read before timing out.
                    if (gateway.bondState(address) == BondState.Bonded) outcome.complete(Unit)
                    else outcome.completeExceptionally(BondFailure(BondFailure.Reason.TimedOut))
                }
                try { outcome.await() } finally { timer.cancel() }
            }
            mutableProgress.value = BondProgress.Bonded(address)
        } catch (failure: BondFailure) {
            mutableProgress.value = BondProgress.Failed(address, failure)
            throw failure
        } finally {
            registration.close()
        }
    }

    companion object {
        /** `BluetoothDevice.EXTRA_UNBOND_REASON` values (hidden constants, stable since API 19). */
        fun reasonFor(unbondReason: Int?): BondFailure.Reason = when (unbondReason) {
            1, 2, 6, 7 -> BondFailure.Reason.AuthenticationFailed
            3, 8 -> BondFailure.Reason.Canceled
            4 -> BondFailure.Reason.RemoteDeviceDown
            9 -> BondFailure.Reason.Removed
            else -> BondFailure.Reason.Unknown
        }
    }
}

/** Whether the platform still holds a bond for an endpoint, and whether the app may remove it. */
interface BondInspector {
    /** `CompanionDeviceManager.removeBond` exists from API 36. */
    val canRemoveBonds: Boolean
    fun isBonded(deviceId: UUID): Boolean
}

class PlatformBondInspector(
    private val sdkInt: Int,
    private val gateway: BondGateway,
    private val addressOf: (UUID) -> String?,
) : BondInspector {
    override val canRemoveBonds: Boolean get() = sdkInt >= REMOVE_BOND_SDK
    override fun isBonded(deviceId: UUID): Boolean =
        addressOf(deviceId)?.let { gateway.bondState(it) == BondState.Bonded } ?: false

    companion object { const val REMOVE_BOND_SDK = 36 }
}
