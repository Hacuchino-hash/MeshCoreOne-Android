// PortedFrom: MC1Services/Sources/MC1Services/Sync/SyncCoordinator.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.contracts.domain.errors

sealed interface SyncFault : SourceServiceFault {
    data object NotConnected : SyncFault
    data class SyncFailed(val reason: String) : SyncFault
    data object AlreadySyncing : SyncFault
}
