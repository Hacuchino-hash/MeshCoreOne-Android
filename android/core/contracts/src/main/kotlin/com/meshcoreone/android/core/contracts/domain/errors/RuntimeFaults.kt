// PortedFrom: MC1Services/Sources/MC1Services/Errors/ConnectionError.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Utilities/TimeoutUtility.swift@db14559b39d32322b06477c6ae676112f583db50
// Native connection ownership failures retain their existing typed distinctions.
package com.meshcoreone.android.core.contracts.domain.errors

import kotlin.time.Duration

sealed interface ConnectionFault : SourceServiceFault {
    data class ConnectionFailed(val reason: String) : ConnectionFault
    data object DeviceNotFound : ConnectionFault
    data object NotConnected : ConnectionFault
    data class InitializationFailed(val reason: String) : ConnectionFault
    data class UnsupportedCapability(val capability: String) : ConnectionFault
    data object ForeignPhysicalOwner : ConnectionFault
    data object RetainedPhysicalLink : ConnectionFault
    data object InvalidIdentity : ConnectionFault
    data object FactoryOwnershipViolation : ConnectionFault
}

data class RuntimeTimeoutFault(val operationName: String, val timeout: Duration) : SourceServiceFault
