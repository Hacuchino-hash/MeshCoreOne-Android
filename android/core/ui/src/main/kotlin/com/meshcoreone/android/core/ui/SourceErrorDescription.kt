// PortedFrom: MC1Services/Sources/MC1Services/Errors/MeshCoreError+LocalizedError.swift@db14559b39d32322b06477c6ae676112f583db50
// Developer-facing frozen English fallback is separate from localized UI copy.
package com.meshcoreone.android.core.ui

import com.meshcoreone.android.core.protocol.config.MeshCoreException

fun MeshCoreException.sourceEnglishDescription(): String = when (this) {
    is MeshCoreException.Timeout -> "The operation timed out. Please try again."
    is MeshCoreException.DeviceError -> when (code.toInt()) {
        1 -> "Command not supported by device firmware."
        2 -> "Item not found on device."
        3 -> "Device storage is full."
        4 -> "Device is in an invalid state for this operation."
        5 -> "Device file system error."
        6 -> "Invalid parameter sent to device."
        else -> "Device error (code ${code.toInt()})."
    }
    is MeshCoreException.ParseError -> "Failed to parse device response: $reason"
    is MeshCoreException.NotConnected -> "Not connected to device."
    is MeshCoreException.CommandFailed -> "Command failed: $reason"
    is MeshCoreException.InvalidResponse -> "Unexpected response from device (expected $expected, got $got)."
    is MeshCoreException.ContactNotFound -> "Contact not found on device."
    is MeshCoreException.DataTooLarge -> "Data too large ($actualSize bytes, maximum is $maxSize)."
    is MeshCoreException.SigningFailed -> "Signing failed: $reason"
    is MeshCoreException.InvalidInput -> "Invalid input: $reason"
    is MeshCoreException.Unknown -> "An unknown error occurred: $reason"
    is MeshCoreException.BluetoothUnavailable -> "Bluetooth is not available on this device."
    is MeshCoreException.BluetoothUnauthorized -> "Bluetooth permission is required. Please enable it in Settings."
    is MeshCoreException.BluetoothPoweredOff -> "Bluetooth is turned off. Please enable Bluetooth to connect."
    is MeshCoreException.ConnectionLost -> cause?.let {
        val description = if (it is MeshCoreException) it.sourceEnglishDescription() else it.localizedMessage
        "Connection to device was lost: $description"
    } ?: "Connection to device was lost."
    is MeshCoreException.SessionNotStarted -> "Session has not been started."
    is MeshCoreException.FeatureDisabled -> "This feature is disabled on the device."
}
