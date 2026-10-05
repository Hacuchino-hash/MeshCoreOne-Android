// AndroidOnly: WP-205 Test-only API37 connection entry-point shadow; settings and real callback objects remain exercised.
package com.meshcoreone.android.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import java.util.concurrent.Executor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothDevice
import org.robolectric.shadows.ShadowBluetoothGatt

@Implements(BluetoothDevice::class, minSdk = 37)
class Api37DeviceShadow : ShadowBluetoothDevice() {
    @RealObject private lateinit var device: BluetoothDevice
    var onOpen: (BluetoothGatt) -> Unit = { throw AssertionError("API37 GATT open was not scripted") }
    var settings: BluetoothGattConnectionSettings? = null
    var callbackExecutor: Executor? = null

    @Implementation(minSdk = 37)
    fun connectGatt(
        connectionSettings: BluetoothGattConnectionSettings,
        executor: Executor,
        callback: BluetoothGattCallback,
    ): BluetoothGatt {
        settings = connectionSettings
        callbackExecutor = executor
        val gatt = ShadowBluetoothGatt.newInstance(device)
        Shadow.extract<ControlledGattShadow>(gatt).setGattCallback(callback)
        onOpen(gatt)
        return gatt
    }
}
