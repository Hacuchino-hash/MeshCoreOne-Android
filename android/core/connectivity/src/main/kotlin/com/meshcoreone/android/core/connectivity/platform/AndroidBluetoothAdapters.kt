// AndroidOnly: WP-206 Platform system-link probe, NUS scanner and bond/PIN broadcast adapters (BLUETOOTH_CONNECT/SCAN guarded).
package com.meshcoreone.android.core.connectivity.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import com.meshcoreone.android.core.ble.BondState
import com.meshcoreone.android.core.connectivity.ConnectivityDiagnostics
import com.meshcoreone.android.core.connectivity.ble.BleScanGateway
import com.meshcoreone.android.core.connectivity.ble.DiscoveredDevice
import com.meshcoreone.android.core.connectivity.ble.SystemLinkProbe
import com.meshcoreone.android.core.connectivity.bond.BondEvent
import com.meshcoreone.android.core.connectivity.bond.BondGateway
import com.meshcoreone.android.core.connectivity.pairing.CompanionDiscoveryCriteria
import com.meshcoreone.android.core.connectivity.pairing.DeviceEndpointIdentity
import java.util.UUID

private fun Context.bluetoothAdapter(): BluetoothAdapter? = getSystemService(BluetoothManager::class.java)?.adapter

/** OS-level GATT connections; a revoked `BLUETOOTH_CONNECT` reads as "not connected", reported. */
class AndroidSystemLinkProbe(
    context: Context,
    private val diagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
) : SystemLinkProbe {
    private val context = context.applicationContext

    override suspend fun isDeviceConnectedToSystem(deviceId: UUID): Boolean = deviceId in systemConnectedDeviceIds()

    override suspend fun systemConnectedDeviceIds(): Set<UUID> = try {
        context.getSystemService(BluetoothManager::class.java)
            ?.getConnectedDevices(BluetoothProfile.GATT)
            ?.mapNotNullTo(linkedSetOf()) { runCatching { DeviceEndpointIdentity.deviceId(it.address) }.getOrNull() }
            ?: emptySet()
    } catch (denied: SecurityException) {
        diagnostics.report("ble.systemConnected", denied)
        emptySet()
    }
}

class ScanFailedException(val errorCode: Int) : Exception("ble.scanFailed.$errorCode")

/** NUS-filtered low-latency scan for the in-app picker (requires `BLUETOOTH_SCAN`, `neverForLocation`). */
class AndroidBleScanGateway(
    context: Context,
    private val diagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
) : BleScanGateway {
    private val context = context.applicationContext
    private val lock = Any()
    private var callback: ScanCallback? = null

    override fun startScan(onDevice: (DiscoveredDevice) -> Unit, onFailure: (Throwable) -> Unit) {
        val scanner = context.bluetoothAdapter()?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth LE scanner unavailable")
        val next = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result, onDevice)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { deliver(it, onDevice) }
            override fun onScanFailed(errorCode: Int) = onFailure(ScanFailedException(errorCode))
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(CompanionDiscoveryCriteria.bluetoothServiceUuid)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        synchronized(lock) { callback = next }
        try {
            scanner.startScan(listOf(filter), settings, next)
        } catch (denied: SecurityException) {
            synchronized(lock) { if (callback === next) callback = null }
            throw denied // typed BluetoothUnauthorized by the link-failure classifier
        }
    }

    override fun stopScan() {
        val current = synchronized(lock) { callback.also { callback = null } } ?: return
        try {
            context.bluetoothAdapter()?.bluetoothLeScanner?.stopScan(current)
        } catch (revoked: SecurityException) {
            // BLUETOOTH_SCAN revoked mid-scan: the platform already tore the scan down.
            diagnostics.report("ble.stopScan", revoked)
        }
    }

    private fun deliver(result: ScanResult, onDevice: (DiscoveredDevice) -> Unit) {
        val endpoint = runCatching { DeviceEndpointIdentity.endpoint(result.device.address, null) }.getOrNull() ?: return
        // The advertised local name needs no BLUETOOTH_CONNECT, unlike BluetoothDevice.getName().
        onDevice(DiscoveredDevice(endpoint.deviceId, endpoint.address, result.scanRecord?.deviceName, result.rssi))
    }
}

/** Bond creation plus `ACTION_BOND_STATE_CHANGED` / `ACTION_PAIRING_REQUEST` observation. */
class AndroidBondGateway(
    context: Context,
    private val diagnostics: ConnectivityDiagnostics = ConnectivityDiagnostics.NONE,
) : BondGateway {
    private val context = context.applicationContext

    /** Both calls need BLUETOOTH_CONNECT; a revoked grant surfaces as SecurityException (BluetoothUnauthorized). */
    override fun bondState(address: String): BondState = try {
        state(device(address).bondState)
    } catch (denied: SecurityException) {
        diagnostics.report("bond.state", denied)
        throw denied
    }

    override fun createBond(address: String): Boolean = try {
        device(address).createBond()
    } catch (denied: SecurityException) {
        diagnostics.report("bond.create", denied)
        throw denied
    }

    override fun register(listener: (BondEvent) -> Unit): AutoCloseable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val address = intent.device()?.address ?: return
                    when (intent.action) {
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> listener(BondEvent.StateChanged(
                            address,
                            state(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)),
                            state(intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)),
                            intent.getIntExtra(EXTRA_UNBOND_REASON, -1).takeIf { it >= 0 },
                        ))
                        BluetoothDevice.ACTION_PAIRING_REQUEST -> listener(BondEvent.PairingRequested(
                            address, intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1),
                        ))
                    }
                } catch (failure: RuntimeException) {
                    diagnostics.report("bond.receiver", failure)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return AutoCloseable {
            try { context.unregisterReceiver(receiver) } catch (failure: IllegalArgumentException) {
                diagnostics.report("bond.unregister", failure)
            }
        }
    }

    private fun device(address: String): BluetoothDevice {
        val adapter = context.bluetoothAdapter() ?: throw IllegalStateException("Bluetooth unavailable")
        return adapter.getRemoteDevice(DeviceEndpointIdentity.canonicalAddress(address))
    }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private fun state(value: Int): BondState = when (value) {
        BluetoothDevice.BOND_BONDED -> BondState.Bonded
        BluetoothDevice.BOND_BONDING -> BondState.Bonding
        else -> BondState.None
    }

    private companion object {
        /** `BluetoothDevice.EXTRA_UNBOND_REASON` is hidden; its key is stable platform behavior. */
        const val EXTRA_UNBOND_REASON = "android.bluetooth.device.extra.REASON"
    }
}
