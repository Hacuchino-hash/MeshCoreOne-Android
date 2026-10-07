// AndroidOnly: WP-206 Runtime-permission, Location Services and revocation policy for BLE/CDM/LAN connectivity.
package com.meshcoreone.android.core.connectivity.permissions

import com.meshcoreone.android.core.contracts.domain.Capability
import com.meshcoreone.android.core.contracts.domain.ConnectionIssue

/** Runtime permissions this module may request; install-time permissions are declared in the manifest. */
enum class ConnectivityPermission(val manifestName: String, val minimumSdk: Int, val capability: Capability) {
    BLUETOOTH_CONNECT("android.permission.BLUETOOTH_CONNECT", 31, Capability.BLUETOOTH_CONNECT),
    /** Declared `neverForLocation`; only the in-app scan fallback needs it (CDM scans system-side). */
    BLUETOOTH_SCAN("android.permission.BLUETOOTH_SCAN", 31, Capability.BLUETOOTH_SCAN),
    /** Optional: the connected-device notification is still posted-but-hidden without it. */
    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS", 33, Capability.NOTIFICATIONS),
    /** API 37 local-network protection for TCP companion radios on the LAN. */
    ACCESS_LOCAL_NETWORK("android.permission.ACCESS_LOCAL_NETWORK", 37, Capability.LOCAL_NETWORK),
}

enum class PairingMode { Companion, ScanFallback }

enum class ConnectivityFeature { BlePairing, BleConnection, LanConnection, ConnectionNotification }

/** One observation of the platform facts the policy needs; collected by the Android adapter. */
data class PermissionSnapshot(
    val sdkInt: Int,
    val granted: Set<ConnectivityPermission>,
    val bluetoothAdapterPresent: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val companionDeviceSetupSupported: Boolean = true,
    val locationServicesEnabled: Boolean = true,
    val userUnlocked: Boolean = true,
)

sealed interface FeatureReadiness {
    /** Ready; `advisories` never block (e.g. Location Services off with a `neverForLocation` scan). */
    data class Ready(val advisories: Set<Advisory> = emptySet()) : FeatureReadiness
    data class Denied(val missing: Set<ConnectivityPermission>) : FeatureReadiness
    data class Unsupported(val capability: Capability) : FeatureReadiness
    data object BluetoothOff : FeatureReadiness
    /** Credential-encrypted storage is locked (before first unlock); defer until unlock. */
    data object UserLocked : FeatureReadiness
}

enum class Advisory { LocationServicesOff, NotificationsHidden }

object ConnectivityPermissionPolicy {
    fun required(feature: ConnectivityFeature, sdkInt: Int, mode: PairingMode): Set<ConnectivityPermission> {
        val candidates = when (feature) {
            ConnectivityFeature.BlePairing -> when (mode) {
                PairingMode.Companion -> setOf(ConnectivityPermission.BLUETOOTH_CONNECT)
                PairingMode.ScanFallback -> setOf(ConnectivityPermission.BLUETOOTH_SCAN, ConnectivityPermission.BLUETOOTH_CONNECT)
            }
            ConnectivityFeature.BleConnection -> setOf(ConnectivityPermission.BLUETOOTH_CONNECT)
            ConnectivityFeature.LanConnection -> setOf(ConnectivityPermission.ACCESS_LOCAL_NETWORK)
            ConnectivityFeature.ConnectionNotification -> setOf(ConnectivityPermission.POST_NOTIFICATIONS)
        }
        return candidates.filterTo(linkedSetOf()) { sdkInt >= it.minimumSdk }
    }

    fun evaluate(feature: ConnectivityFeature, snapshot: PermissionSnapshot, mode: PairingMode): FeatureReadiness {
        val bluetooth = feature == ConnectivityFeature.BlePairing || feature == ConnectivityFeature.BleConnection
        if (bluetooth && !snapshot.bluetoothAdapterPresent) return FeatureReadiness.Unsupported(Capability.BLUETOOTH_CONNECT)
        if (feature == ConnectivityFeature.BlePairing && mode == PairingMode.Companion && !snapshot.companionDeviceSetupSupported) {
            return FeatureReadiness.Unsupported(Capability.COMPANION_ASSOCIATION)
        }
        val missing = required(feature, snapshot.sdkInt, mode) - snapshot.granted
        if (feature == ConnectivityFeature.ConnectionNotification) {
            return FeatureReadiness.Ready(if (missing.isEmpty()) emptySet() else setOf(Advisory.NotificationsHidden))
        }
        if (missing.isNotEmpty()) return FeatureReadiness.Denied(missing)
        if (bluetooth && !snapshot.bluetoothEnabled) return FeatureReadiness.BluetoothOff
        if (feature == ConnectivityFeature.BleConnection && !snapshot.userUnlocked) return FeatureReadiness.UserLocked
        val advisories = if (feature == ConnectivityFeature.BlePairing && mode == PairingMode.ScanFallback &&
            !snapshot.locationServicesEnabled) setOf(Advisory.LocationServicesOff) else emptySet()
        return FeatureReadiness.Ready(advisories)
    }

    /** Permissions granted before and missing now: the host tears down the affected transport. */
    fun revoked(previous: PermissionSnapshot, current: PermissionSnapshot): Set<ConnectivityPermission> =
        previous.granted - current.granted

    fun issueFor(readiness: FeatureReadiness): ConnectionIssue? = when (readiness) {
        is FeatureReadiness.Ready, FeatureReadiness.UserLocked -> null
        is FeatureReadiness.Denied -> ConnectionIssue.PermissionDenied(readiness.missing.first().capability)
        is FeatureReadiness.Unsupported -> ConnectionIssue.Unsupported(readiness.capability)
        FeatureReadiness.BluetoothOff -> ConnectionIssue.BluetoothOff
    }
}
