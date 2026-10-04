// AndroidOnly: WP-205 Public Android GATT calls, API31/32 legacy values and API33/37 overloads.
package com.meshcoreone.android.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

internal interface GattCallbackExecutor {
    val handler: Handler
    fun execute(action: () -> Unit): Boolean
    val executor: Executor
        get() = Executor { task ->
            if (!execute { task.run() }) throw RejectedExecutionException("BLE callback looper is unavailable")
        }
}

internal class HandlerGattExecutor(override val handler: Handler) : GattCallbackExecutor {
    override fun execute(action: () -> Unit): Boolean {
        if (Looper.myLooper() == handler.looper) {
            action()
            return true
        }
        return handler.post { action() }
    }
}

internal interface AndroidGattApi {
    fun availability(context: Context): BluetoothAvailability
    fun bondState(context: Context, handle: BleDeviceHandle): BondState
    fun connectLegacy(context: Context, handle: BleDeviceHandle, callback: BluetoothGattCallback, handler: Handler): BluetoothGatt?
    fun connectModern(context: Context, handle: BleDeviceHandle, callback: BluetoothGattCallback, executor: Executor): BluetoothGatt?
    fun discoverServices(gatt: BluetoothGatt): Boolean
    fun requestMtu(gatt: BluetoothGatt, mtu: Int): Boolean
    fun setNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean
    fun readRssi(gatt: BluetoothGatt): Boolean
    fun writeLegacy(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int): Boolean
    fun writeModern(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int): Int
    fun descriptorLegacy(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean
    fun descriptorModern(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Int
    fun disconnect(gatt: BluetoothGatt)
    fun close(gatt: BluetoothGatt)
}

@SuppressLint("MissingPermission")
internal object PlatformGattApi : AndroidGattApi {
    override fun availability(context: Context): BluetoothAvailability {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return BluetoothAvailability.Unauthorized
        }
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return BluetoothAvailability.Unavailable
            if (adapter.isEnabled) BluetoothAvailability.Ready else BluetoothAvailability.PoweredOff
        } catch (_: SecurityException) {
            BluetoothAvailability.Unauthorized
        }
    }

    private fun device(context: Context, handle: BleDeviceHandle): BluetoothDevice {
        when (availability(context)) {
            BluetoothAvailability.Ready -> Unit
            BluetoothAvailability.Unauthorized -> throw BleTransportException(BleError.BluetoothUnauthorized)
            BluetoothAvailability.PoweredOff -> throw BleTransportException(BleError.BluetoothPoweredOff)
            BluetoothAvailability.Unavailable -> throw BleTransportException(BleError.BluetoothUnavailable)
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw BleTransportException(BleError.BluetoothUnavailable)
        return adapter.getRemoteDevice(handle.address)
    }

    override fun bondState(context: Context, handle: BleDeviceHandle): BondState =
        when (device(context, handle).bondState) {
            BluetoothDevice.BOND_NONE -> BondState.None
            BluetoothDevice.BOND_BONDING -> BondState.Bonding
            BluetoothDevice.BOND_BONDED -> BondState.Bonded
            else -> throw BleTransportException(BleError.InvalidResponse)
        }

    @Suppress("DEPRECATION")
    override fun connectLegacy(
        context: Context, handle: BleDeviceHandle, callback: BluetoothGattCallback, handler: Handler,
    ): BluetoothGatt? = device(context, handle).connectGatt(
        context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler,
    )

    @TargetApi(37)
    override fun connectModern(
        context: Context, handle: BleDeviceHandle, callback: BluetoothGattCallback, executor: Executor,
    ): BluetoothGatt? {
        val settings = BluetoothGattConnectionSettings.Builder()
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .setAutoConnectEnabled(false)
            .setAutomaticMtuEnabled(false)
            .setOpportunisticEnabled(false)
            .build()
        return device(context, handle).connectGatt(settings, executor, callback)
    }

    override fun discoverServices(gatt: BluetoothGatt): Boolean = gatt.discoverServices()
    override fun requestMtu(gatt: BluetoothGatt, mtu: Int): Boolean = gatt.requestMtu(mtu)
    override fun readRssi(gatt: BluetoothGatt): Boolean = gatt.readRemoteRssi()
    override fun setNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean =
        gatt.setCharacteristicNotification(characteristic, true)

    @Suppress("DEPRECATION")
    override fun writeLegacy(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int,
    ): Boolean {
        characteristic.writeType = writeType
        if (!characteristic.setValue(value.copyOf())) return false
        return gatt.writeCharacteristic(characteristic)
    }

    @TargetApi(33)
    override fun writeModern(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int,
    ): Int = gatt.writeCharacteristic(characteristic, value.copyOf(), writeType)

    @Suppress("DEPRECATION")
    override fun descriptorLegacy(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        if (!descriptor.setValue(value.copyOf())) return false
        return gatt.writeDescriptor(descriptor)
    }

    @TargetApi(33)
    override fun descriptorModern(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Int =
        gatt.writeDescriptor(descriptor, value.copyOf())

    override fun disconnect(gatt: BluetoothGatt) { gatt.disconnect() }
    override fun close(gatt: BluetoothGatt) { gatt.close() }
}

internal fun immediateFailure(kind: GattOperationKind, status: Int?): BleTransportException =
    when (status) {
        BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION ->
            BleTransportException(BleError.BluetoothUnauthorized, kind, status)
        BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED ->
            BleTransportException(BleError.BluetoothPoweredOff, kind, status)
        BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED ->
            BleTransportException(BleError.BondRequired(BondState.None), kind, status)
        else -> BleTransportException(BleError.GattRejected(kind, status), kind, status)
    }
