// AndroidOnly: WP-206 Companion presence events drive opportunistic reconnect with pre-unlock, revocation and process-death fallbacks.
package com.meshcoreone.android.core.connectivity.presence

import com.meshcoreone.android.core.contracts.domain.Capability
import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import java.util.UUID

sealed interface PresenceEvent {
    data class Appeared(val deviceId: UUID) : PresenceEvent
    data class Disappeared(val deviceId: UUID) : PresenceEvent
    data class AssociationRemoved(val deviceId: UUID) : PresenceEvent
    /** An association changed (API 36 removal of an id no longer listed): re-read and diff the registry. */
    data object AssociationsChanged : PresenceEvent
}

data class PresenceContext(
    val intent: ConnectionIntent,
    val userDisconnected: Boolean,
    val lastConnectedDeviceId: UUID?,
    val connectionState: DeviceConnectionState,
    val reconnectInProgress: Boolean,
    val userUnlocked: Boolean,
    val bluetoothConnectGranted: Boolean,
    val bluetoothEnabled: Boolean,
)

sealed interface PresenceAction {
    data class Reconnect(val deviceId: UUID) : PresenceAction
    /** Credential-encrypted storage (session keys, preferences) is unavailable before first unlock. */
    data class DeferUntilUnlock(val deviceId: UUID) : PresenceAction
    data class PermissionRevoked(val capability: Capability) : PresenceAction
    data class ForgetAssociation(val deviceId: UUID) : PresenceAction
    /** Call `CompanionSetupService.refreshAssociations()`; removals are reported through the delegate. */
    data object RefreshAssociations : PresenceAction
    data class Ignore(val reason: IgnoreReason) : PresenceAction
}

enum class IgnoreReason {
    NotLastConnected, UserDisconnected, AlreadyConnected, BluetoothOff,
    /** Presence is radio proximity, not a GATT signal: the live link reports its own loss. */
    DisappearanceIsNotLinkLoss,
}

object PresenceReconnectPolicy {
    fun decide(event: PresenceEvent, context: PresenceContext): PresenceAction = when (event) {
        is PresenceEvent.AssociationRemoved -> PresenceAction.ForgetAssociation(event.deviceId)
        PresenceEvent.AssociationsChanged -> PresenceAction.RefreshAssociations
        is PresenceEvent.Disappeared -> PresenceAction.Ignore(IgnoreReason.DisappearanceIsNotLinkLoss)
        is PresenceEvent.Appeared -> when {
            event.deviceId != context.lastConnectedDeviceId -> PresenceAction.Ignore(IgnoreReason.NotLastConnected)
            context.userDisconnected || context.intent == ConnectionIntent.UserDisconnected ->
                PresenceAction.Ignore(IgnoreReason.UserDisconnected)
            !context.userUnlocked -> PresenceAction.DeferUntilUnlock(event.deviceId)
            !context.bluetoothConnectGranted -> PresenceAction.PermissionRevoked(Capability.BLUETOOTH_CONNECT)
            !context.bluetoothEnabled -> PresenceAction.Ignore(IgnoreReason.BluetoothOff)
            context.connectionState != DeviceConnectionState.DISCONNECTED || context.reconnectInProgress ->
                PresenceAction.Ignore(IgnoreReason.AlreadyConnected)
            else -> PresenceAction.Reconnect(event.deviceId)
        }
    }
}

fun interface PresenceEventSink {
    fun onPresence(event: PresenceEvent)
}

/**
 * Process-level hand-off from the system-bound `CompanionDeviceService` to the host runtime.
 * When the binding started a fresh process (process death) before the host attached its sink,
 * the latest event per device is retained (bounded by association count) and replayed on attach.
 */
object CompanionPresenceDispatcher {
    private val lock = Any()
    private var sink: PresenceEventSink? = null
    private val pending = linkedMapOf<Any, PresenceEvent>()
    private const val MAX_PENDING = 32

    fun attach(value: PresenceEventSink) {
        val replay = synchronized(lock) {
            sink = value
            pending.values.toList().also { pending.clear() }
        }
        replay.forEach(value::onPresence)
    }

    fun detach(value: PresenceEventSink) { synchronized(lock) { if (sink === value) sink = null } }

    fun dispatch(event: PresenceEvent) {
        val target = synchronized(lock) {
            sink ?: run {
                val key = key(event)
                pending.remove(key)
                pending[key] = event
                while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
                null
            }
        }
        target?.onPresence(event)
    }

    private fun key(event: PresenceEvent): Any = when (event) {
        is PresenceEvent.Appeared -> event.deviceId
        is PresenceEvent.Disappeared -> event.deviceId
        is PresenceEvent.AssociationRemoved -> event.deviceId
        PresenceEvent.AssociationsChanged -> PresenceEvent.AssociationsChanged
    }

    internal fun resetForTest() { synchronized(lock) { sink = null; pending.clear() } }
}
