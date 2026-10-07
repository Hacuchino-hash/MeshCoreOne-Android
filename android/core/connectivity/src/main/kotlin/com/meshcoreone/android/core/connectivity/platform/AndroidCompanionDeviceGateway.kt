// AndroidOnly: WP-206 CompanionDeviceManager association, chooser result and presence-observation adapters with API 31-37 guards.
package com.meshcoreone.android.core.connectivity.platform

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.bluetooth.le.ScanFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.meshcoreone.android.core.connectivity.ConnectivityDiagnostics
import com.meshcoreone.android.core.connectivity.pairing.CompanionAssociation
import com.meshcoreone.android.core.connectivity.pairing.CompanionAssociationEvents
import com.meshcoreone.android.core.connectivity.pairing.CompanionDeviceGateway
import com.meshcoreone.android.core.connectivity.pairing.CompanionDiscoveryCriteria
import com.meshcoreone.android.core.connectivity.pairing.CompanionSetupError
import com.meshcoreone.android.core.connectivity.pairing.DeviceEndpointIdentity
import java.util.concurrent.Executor

/**
 * CompanionDeviceManager boundary. `associate` only records an association through the system
 * chooser: it never opens GATT, never bonds, and grants no blanket background exemption.
 */
class AndroidCompanionDeviceGateway(
    context: Context,
    private val diagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
) : CompanionDeviceGateway {
    private val context = context.applicationContext
    private val manager: CompanionDeviceManager? = context.getSystemService(CompanionDeviceManager::class.java)
    private val mainExecutor: Executor = context.mainExecutor
    private val lock = Any()
    private var pending: CompanionAssociationEvents? = null

    override val isSupported: Boolean
        get() = manager != null &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    override fun associations(): List<CompanionAssociation> {
        val cdm = manager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations.mapNotNull(::toAssociation)
        } else {
            @Suppress("DEPRECATION")
            cdm.associations.mapNotNull { address ->
                runCatching { DeviceEndpointIdentity.endpoint(address, null) }.getOrNull()
                    ?.let { CompanionAssociation(-1, it.deviceId, it.address, CompanionDiscoveryCriteria.DEFAULT_ACCESSORY_NAME) }
            }
        }
    }

    override fun associate(events: CompanionAssociationEvents) {
        val cdm = manager ?: throw CompanionSetupError.SessionNotActive()
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(CompanionDiscoveryCriteria.bluetoothServiceUuid)).build())
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        synchronized(lock) { pending = events }
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("API 31-32 chooser delivery")
            override fun onDeviceFound(intentSender: IntentSender) = events.onChooserPending(intentSender)
            override fun onAssociationPending(intentSender: IntentSender) = events.onChooserPending(intentSender)
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val association = toAssociation(associationInfo)
                if (association == null) events.onFailure(CompanionSetupError.NoBluetoothIdentifier())
                else events.onAssociationCreated(association)
                finish(events)
            }
            override fun onFailure(errorCode: Int, error: CharSequence?) {
                when (errorCode) {
                    CompanionDeviceManager.RESULT_CANCELED, CompanionDeviceManager.RESULT_USER_REJECTED -> events.onDismissed()
                    CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT -> events.onFailure(CompanionSetupError.DiscoveryTimeout())
                    else -> events.onFailure(CompanionSetupError.ConnectionFailed())
                }
                finish(events)
            }
            override fun onFailure(error: CharSequence?) {
                diagnostics.report("cdm.onFailure", null)
                // Pre-API-35 callbacks carry only an internal (non-localized) reason string.
                if (error?.contains("cancel", ignoreCase = true) == true) events.onDismissed()
                else events.onFailure(CompanionSetupError.ConnectionFailed())
                finish(events)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) cdm.associate(request, mainExecutor, callback)
        else @Suppress("DEPRECATION") cdm.associate(request, callback, Handler(Looper.getMainLooper()))
    }

    /** The UI host forwards the chooser's activity result here (`startIntentSenderForResult`). */
    fun onChooserResult(resultCode: Int, data: Intent?) {
        val events = synchronized(lock) { pending } ?: return
        if (resultCode != Activity.RESULT_OK) {
            events.onDismissed(); finish(events); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return // onAssociationCreated delivers it
        val address = when (val device = legacyDevice(data)) {
            is ScanResult -> device.device.address
            is BluetoothDevice -> device.address
            else -> null
        }
        if (address == null) events.onFailure(CompanionSetupError.NoBluetoothIdentifier())
        else events.onAssociationCreated(associations().firstOrNull { it.address.equals(address, true) }
            ?: DeviceEndpointIdentity.endpoint(address, null).let {
                CompanionAssociation(-1, it.deviceId, it.address, CompanionDiscoveryCriteria.DEFAULT_ACCESSORY_NAME)
            })
        finish(events)
    }

    override suspend fun disassociate(association: CompanionAssociation) {
        val cdm = manager ?: throw CompanionSetupError.SessionNotActive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && association.associationId >= 0) {
            cdm.disassociate(association.associationId)
        } else {
            @Suppress("DEPRECATION") cdm.disassociate(association.address)
        }
    }

    /** Presence observation binds `MeshCompanionDeviceService` while the radio is near (API 36 request form). */
    override fun observePresence(association: CompanionAssociation, observe: Boolean) {
        val start = observe
        val cdm = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && association.associationId >= 0) {
                val request = ObservingDevicePresenceRequest.Builder().setAssociationId(association.associationId).build()
                if (start) cdm.startObservingDevicePresence(request) else cdm.stopObservingDevicePresence(request)
            } else {
                @Suppress("DEPRECATION")
                if (start) cdm.startObservingDevicePresence(association.address) else cdm.stopObservingDevicePresence(association.address)
            }
        } catch (failure: Exception) {
            diagnostics.report(if (start) "cdm.observePresence" else "cdm.stopObservingPresence", failure)
        }
    }

    private fun finish(events: CompanionAssociationEvents) {
        synchronized(lock) { if (pending === events) pending = null }
    }

    private fun legacyDevice(data: Intent?): Any? {
        data ?: return null
        @Suppress("DEPRECATION")
        return data.getParcelableExtra<android.os.Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
    }

    companion object {
        fun toAssociation(info: AssociationInfo): CompanionAssociation? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val address = info.deviceMacAddress?.toString() ?: return null
            val endpoint = runCatching { DeviceEndpointIdentity.endpoint(address, info.id) }.getOrNull() ?: return null
            val name = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: CompanionDiscoveryCriteria.DEFAULT_ACCESSORY_NAME
            return CompanionAssociation(info.id, endpoint.deviceId, endpoint.address, name)
        }
    }
}
