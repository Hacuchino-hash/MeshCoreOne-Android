// PortedFrom: MC1Services/Sources/MC1Services/Services/AccessorySetupKitService.swift@db14559b39d32322b06477c6ae676112f583db50
// AndroidOnly: WP-304 Native declined-removal outcome retains its existing distinction.
package com.meshcoreone.android.core.contracts.domain.errors

sealed interface CompanionSetupFault : SourceServiceFault {
    data object SessionNotActive : CompanionSetupFault
    data object SessionInvalidated : CompanionSetupFault
    data object PickerDismissed : CompanionSetupFault
    data object PickerRestricted : CompanionSetupFault
    data object PickerAlreadyActive : CompanionSetupFault
    data class PairingFailed(val reason: String) : CompanionSetupFault
    data object NoBluetoothIdentifier : CompanionSetupFault
    data object DiscoveryTimeout : CompanionSetupFault
    data object ConnectionFailed : CompanionSetupFault
    data object UserCancelled : CompanionSetupFault
}
