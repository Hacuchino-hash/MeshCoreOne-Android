// AndroidOnly: WP-317 Feature-owned service ports for the settings sections; WP-303 adapts the core:services/core:connectivity/core:datastore implementations.
package com.meshcoreone.android.feature.settings.device

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.AdvertLocationPolicy
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.RegionSelection
import com.meshcoreone.android.core.model.RemoveUnfavoritedResult
import com.meshcoreone.android.core.model.TelemetryModes
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.AutoAddConfig
import com.meshcoreone.android.core.protocol.model.SelfInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * The connection facts the sections read (Swift `appState.connectedDevice`, `connectionState` and
 * `canRunSettingsStartupReads`). Holders re-read these through the flows, so a null device means disconnected.
 */
interface SettingsConnection {
    val connectedDevice: StateFlow<DeviceDTO?>
    val connectionState: StateFlow<DeviceConnectionState>

    /** `appState.canRunSettingsStartupReads`: false while contact/channel sync still contends for the radio. */
    val startupReadsAllowed: StateFlow<Boolean>
}

/** Device GPS capability and switch (mirror of `core:services` `DeviceGPSState`). */
data class DeviceGpsState(val isSupported: Boolean, val isEnabled: Boolean)

/**
 * The `SettingsService` slice the sections call. Signatures match core:services `SettingsService` so the
 * adapter is plain delegation, except [applyRadioPresetVerified], which takes the preset id because
 * `RadioPreset` lives in core:services. Failures stay `SettingsServiceException` (core:contracts).
 */
interface SettingsRadioPort {
    suspend fun getSelfInfo(): SelfInfo
    suspend fun setRadioParamsVerified(
        frequencyKHz: UInt, bandwidthKHz: UInt, spreadingFactor: UByte, codingRate: UByte,
        clientRepeat: Boolean? = null, appliedRadioPresetID: String? = null,
    ): SelfInfo
    suspend fun setTxPowerVerified(power: Byte): SelfInfo
    suspend fun applyRadioPresetVerified(presetId: String): SelfInfo
    suspend fun setOtherParamsVerified(
        device: DeviceDTO, autoAddContacts: Boolean? = null, telemetryModes: TelemetryModes? = null,
        advertLocationPolicy: AdvertLocationPolicy? = null, multiAcks: UByte? = null,
    ): SelfInfo
    suspend fun setAutoAddConfigVerified(config: AutoAddConfig): AutoAddConfig
    suspend fun refreshAutoAddConfig()
    suspend fun setPathHashModeVerified(mode: UByte): UByte
    suspend fun getDeviceGPSState(): DeviceGpsState
    suspend fun setDeviceGPSEnabledVerified(enabled: Boolean): DeviceGpsState
    suspend fun refreshDeviceInfo()
    suspend fun getDefaultFloodScope(): String?
    suspend fun setDefaultFloodScopeVerified(name: String?): String?
    suspend fun setBlePin(pin: UInt)
    suspend fun reboot()
    suspend fun factoryReset()
    suspend fun importPrivateKey(key: Bytes)
}

/** One selectable radio preset (mirror of the `core:services` `RadioPreset` fields the UI and write paths use). */
data class RadioPresetOption(
    val id: String,
    val name: String,
    /** `RadioRegion.rawValue`, the section header of the normal picker. */
    val regionLabel: String,
    val repeatSectionHeader: String? = null,
)

/** `RadioPresets`/`RadioRegion` queries the preset section needs; WP-303 adapts `core:services` `RadioPresets`. */
interface RadioCatalogPort {
    /** `RadioPresets.visiblePresets(for:activeID:)`, already ordered by `RadioRegion` then recommendation. */
    fun visiblePresets(region: RegionSelection?, activeId: String?): List<RadioPresetOption>
    val repeatPresets: List<RadioPresetOption>
    fun resolvedPreset(device: DeviceDTO, region: RegionSelection?): RadioPresetOption?
    fun matchingPresetIds(device: DeviceDTO): Set<String>
    fun nearestRepeatPreset(frequencyKHz: UInt): RadioPresetOption?
    fun matchingRepeatPreset(frequencyKHz: UInt): RadioPresetOption?

    /** Frequency a repeat preset writes (kHz), for `setRadioParamsVerified`. */
    fun repeatFrequencyKHz(presetId: String): UInt?
}

/** `RegionalAreas.showsSubdivisionPicker(for:)` (core:services `RegionalAreas`). */
fun interface SubdivisionCatalog {
    fun showsSubdivisionPicker(countryCode: String?): Boolean
}

/** Device-scoped location preferences (core:datastore `DevicePreferences` slice). */
enum class GpsSource { PHONE, DEVICE }

interface DevicePreferencePort {
    fun isAutoUpdateLocationEnabled(deviceId: java.util.UUID): Boolean
    fun setAutoUpdateLocationEnabled(enabled: Boolean, deviceId: java.util.UUID)
    fun gpsSource(deviceId: java.util.UUID): GpsSource
    fun hasSetGpsSource(deviceId: java.util.UUID): Boolean
    fun setGpsSource(source: GpsSource, deviceId: java.util.UUID)
}

/** Phone location authorization (Swift `LocationService`; Android `ACCESS_FINE/COARSE_LOCATION`). */
enum class LocationAuthorization { NOT_DETERMINED, AUTHORIZED, DENIED, RESTRICTED }

interface LocationPermissionPort {
    val authorization: StateFlow<LocationAuthorization>
    val isRequestingLocation: StateFlow<Boolean>
    fun requestPermissionIfNeeded()

    /** `requestCurrentLocation()`: asks for permission when undetermined and waits for the answer. */
    suspend fun requestCurrentLocation()
}

/** Resolves the phone location to a region (`RegionResolver.resolve()`), null on a lookup miss. */
fun interface RegionLookupPort {
    suspend fun resolve(): RegionSelection?
}

/** `appState.regionSelection` (read and written by the preset-location session). */
interface RegionSelectionPort {
    val selection: StateFlow<RegionSelection?>
    fun set(selection: RegionSelection?)
}

/** Runtime notification permission (Swift `UNUserNotificationCenter`; Android `POST_NOTIFICATIONS`). */
enum class NotificationAuthorization { NOT_DETERMINED, DENIED, AUTHORIZED }

interface NotificationPermissionPort {
    suspend fun status(): NotificationAuthorization

    /** Returns whether the user granted it. */
    suspend fun request(): Boolean
}

/** Connection-manager operations behind the danger zone. */
interface DeviceMaintenancePort {
    /** Throws [PairingCancelledException] when the user declines the system removal prompt. */
    suspend fun forgetDevice(deleteData: Boolean)
    suspend fun forgetDevice(id: java.util.UUID)
    suspend fun unfavoritedNodeCount(): Int
    suspend fun removeUnfavoritedNodes(): RemoveUnfavoritedResult
}

/** Mirror of `DevicePairingError.cancelled` (core:connectivity `DevicePairingError.Cancelled`). */
class PairingCancelledException : Exception("Pairing cancelled")

/** Persistence-backed device edits the sections trigger (`ConnectionManager`/`DeviceService`). */
interface DeviceSettingsStorePort {
    suspend fun updateOcvSettings(deviceId: java.util.UUID, preset: String, customArray: String?)
    fun savePreRepeatSettings()
    fun clearPreRepeatSettings()
    fun addKnownRegion(region: String)
    fun removeKnownRegion(region: String)
}

/** Debug-log export/clear (`LogExportService`, `PersistenceStore.clearDebugLogEntries`). */
interface DiagnosticsPort {
    /** Returns the exported file's content URI string, or null when export failed. */
    suspend fun createExportFile(): String?
    suspend fun clearDebugLogs()
}

/** Key generation for the identity sheets (`KeyGenerationService`, core:services). */
interface IdentityKeyPort {
    /** Validates RFC 8032 clamping of a 64-byte expanded key; false when invalid. */
    fun isValidExpandedKey(key: Bytes): Boolean
    suspend fun generateIdentity(hexPrefix: String?): GeneratedIdentity
}

class GeneratedIdentity(val publicKey: Bytes, val expandedPrivateKey: Bytes)

/** Stale-node cleanup settings (app storage keys `autoDeleteStaleNodesDays`, `lastStaleCleanupDate`). */
interface StaleNodeCleanupPort {
    val thresholdDays: StateFlow<Int>
    val lastCleanupEpochSeconds: StateFlow<Double>
    fun setThresholdDays(days: Int)

    /** `appState.performStaleNodeCleanup(force: true)`. */
    fun performCleanup(force: Boolean)
}

/**
 * Mirror of `BLEError.operationTimeout`: the link dropped before the write acknowledgement. Reboot and PIN-change
 * flows treat it as success because the radio restarts before it can answer.
 */
class SettingsOperationTimeoutException : Exception("Operation timed out")
