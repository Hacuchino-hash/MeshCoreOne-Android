// AndroidOnly: WP-206 System-bound CompanionDeviceService forwarding presence events with API 31/33/36 callback guards.
package com.meshcoreone.android.core.connectivity.platform

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import com.meshcoreone.android.core.connectivity.pairing.DeviceEndpointIdentity
import com.meshcoreone.android.core.connectivity.presence.CompanionPresenceDispatcher
import com.meshcoreone.android.core.connectivity.presence.PresenceEvent
import java.util.UUID

/**
 * Bound by the system while an observed associated radio is nearby. It performs no GATT work:
 * it forwards typed presence events to the host runtime through [CompanionPresenceDispatcher],
 * which buffers them when this binding started a fresh process.
 */
class MeshCompanionDeviceService : CompanionDeviceService() {
    @Deprecated("API 31-32 delivery")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        deviceId(address)?.let { CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(it)) }
    }

    @Deprecated("API 31-32 delivery")
    override fun onDeviceDisappeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        deviceId(address)?.let { CompanionPresenceDispatcher.dispatch(PresenceEvent.Disappeared(it)) }
    }

    @Deprecated("API 33-35 delivery")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) return
        deviceId(associationInfo)?.let { CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(it)) }
    }

    @Deprecated("API 33-35 delivery")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) return
        deviceId(associationInfo)?.let { CompanionPresenceDispatcher.dispatch(PresenceEvent.Disappeared(it)) }
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        val id = deviceIdForAssociation(event.associationId) ?: return
        val mapped = when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, DevicePresenceEvent.EVENT_BT_CONNECTED -> PresenceEvent.Appeared(id)
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED, DevicePresenceEvent.EVENT_BT_DISCONNECTED -> PresenceEvent.Disappeared(id)
            DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED -> PresenceEvent.AssociationRemoved(id)
            else -> return
        }
        CompanionPresenceDispatcher.dispatch(mapped)
    }

    private fun deviceId(address: String): UUID? = runCatching { DeviceEndpointIdentity.deviceId(address) }.getOrNull()

    private fun deviceId(info: AssociationInfo): UUID? = AndroidCompanionDeviceGateway.toAssociation(info)?.deviceId

    private fun deviceIdForAssociation(associationId: Int): UUID? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return null
        return manager.myAssociations.firstOrNull { it.id == associationId }?.let(::deviceId)
    }
}
