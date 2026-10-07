// AndroidOnly: WP-206 Reads runtime-permission, adapter, CDM-feature, Location Services and unlock facts from the platform.
package com.meshcoreone.android.core.connectivity.permissions

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.UserManager

/** Platform reader for [PermissionSnapshot]; every query is side-effect free. */
class AndroidPermissionState(context: Context) {
    private val context = context.applicationContext

    fun snapshot(): PermissionSnapshot {
        val sdk = Build.VERSION.SDK_INT
        val granted = ConnectivityPermission.entries.filterTo(linkedSetOf()) {
            sdk >= it.minimumSdk && context.checkSelfPermission(it.manifestName) == PackageManager.PERMISSION_GRANTED
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val location = context.getSystemService(LocationManager::class.java)
        return PermissionSnapshot(
            sdkInt = sdk,
            granted = granted,
            bluetoothAdapterPresent = adapter != null,
            // BluetoothAdapter.isEnabled needs no runtime permission on API 31+.
            bluetoothEnabled = adapter?.isEnabled == true,
            companionDeviceSetupSupported =
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP),
            locationServicesEnabled = location?.isLocationEnabled == true,
            userUnlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked != false,
        )
    }
}
