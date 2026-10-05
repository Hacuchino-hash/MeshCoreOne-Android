// PortedFrom: MC1Services/Sources/MC1Services/Errors/ConnectionError.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.runtime

sealed class ConnectionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(val reason: String, cause: Throwable? = null) : ConnectionError("Connection failed: $reason", cause)
    class DeviceNotFound : ConnectionError("Device not found")
    class NotConnected : ConnectionError("Not connected to device")
    class InitializationFailed(val reason: String, cause: Throwable? = null) :
        ConnectionError("Device initialization failed: $reason", cause)
    class UnsupportedCapability(val capability: String) : ConnectionError("Unsupported connection capability: $capability")
    class ForeignPhysicalOwner : ConnectionError("Physical connection is owned by another runtime")
    class RetainedPhysicalLink : ConnectionError("A retained physical link cannot become a fresh protocol generation")
    class InvalidIdentity : ConnectionError("Radio public key must contain exactly 32 bytes")
    class FactoryOwnershipViolation : ConnectionError("Factory must return its registered generation-owned service handle")
}
