// PortedFrom: MC1Services/Sources/MC1Services/Errors/BLEError.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ble

sealed interface BleError {
    data object BluetoothUnavailable : BleError
    data object BluetoothUnauthorized : BleError
    data object BluetoothPoweredOff : BleError
    data object DeviceNotFound : BleError
    data class ConnectionFailed(val reason: String) : BleError
    data object ConnectionTimeout : BleError
    data object NotConnected : BleError
    data object CharacteristicNotFound : BleError
    data class WriteError(val reason: String) : BleError
    data object InvalidResponse : BleError
    data object OperationTimeout : BleError
    data object AuthenticationFailed : BleError
    data class PairingFailed(val reason: String) : BleError
    data object DeviceConnectedToOtherApp : BleError

    data object ServiceNotFound : BleError
    data object DescriptorNotFound : BleError
    data class CharacteristicPropertyMissing(val property: GattProperty) : BleError
    data class BondRequired(val state: BondState) : BleError
    data class GattRejected(val operation: GattOperationKind, val status: Int?) : BleError
    data class MtuTooSmall(val actual: Int, val minimum: Int) : BleError
    data class InvalidMtu(val actual: Int) : BleError
    data class FrameTooLarge(val actual: Int, val maximum: Int) : BleError
    data class FirmwareCapabilityUnverified(val actual: Int, val bootstrapMaximum: Int) : BleError
    data object FirmwareCapabilitiesAlreadyVerified : BleError
    data class StaleGeneration(val expected: Long, val actual: Long) : BleError
    data object MultipleReceivers : BleError
    data object NotificationDeliveryFailed : BleError
    data class RssiReadFailed(val status: Int) : BleError
    data class CleanupFailed(val stage: String) : BleError
    data class AbortedOperation(val operation: GattOperationKind) : BleError
}

enum class BleRecovery {
    EnableBluetooth, GrantBluetoothConnect, SelectDevice, PairInSystem,
    RetryWithNewConnection, VerifyFirmwareCapabilities, IncreaseMtu,
    ReduceFrame, UseSingleReceiver, HostConfiguration,
}

class BleTransportException(
    val error: BleError,
    val operation: GattOperationKind? = null,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception("ble.${error.javaClass.simpleName}", cause) {
    val recovery: BleRecovery
        get() = when (error) {
            BleError.BluetoothUnavailable -> BleRecovery.HostConfiguration
            BleError.BluetoothUnauthorized -> BleRecovery.GrantBluetoothConnect
            BleError.BluetoothPoweredOff -> BleRecovery.EnableBluetooth
            BleError.DeviceNotFound -> BleRecovery.SelectDevice
            BleError.AuthenticationFailed, is BleError.PairingFailed, is BleError.BondRequired -> BleRecovery.PairInSystem
            is BleError.FirmwareCapabilityUnverified -> BleRecovery.VerifyFirmwareCapabilities
            is BleError.MtuTooSmall -> BleRecovery.IncreaseMtu
            is BleError.FrameTooLarge -> BleRecovery.ReduceFrame
            BleError.MultipleReceivers -> BleRecovery.UseSingleReceiver
            else -> BleRecovery.RetryWithNewConnection
        }
}

internal fun gattFailure(kind: GattOperationKind, status: Int): BleTransportException {
    val error = when {
        status in setOf(5, 8, 12, 15) -> BleError.AuthenticationFailed
        kind == GattOperationKind.Write -> BleError.WriteError("gatt.status.$status")
        else -> BleError.ConnectionFailed("gatt.status.$status")
    }
    return BleTransportException(error, kind, status)
}
