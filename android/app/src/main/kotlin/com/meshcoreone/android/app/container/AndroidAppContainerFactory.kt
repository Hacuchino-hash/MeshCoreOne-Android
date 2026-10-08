// AndroidOnly: WP-303 Builds the production AppContainer from the Android context.
package com.meshcoreone.android.app.container

import android.app.Application
import com.meshcoreone.android.app.state.ProcessForegroundState
import com.meshcoreone.android.core.connectivity.ConnectivityDiagnostics
import com.meshcoreone.android.core.connectivity.ConnectivityPlatform
import com.meshcoreone.android.core.connectivity.SystemConnectivityClock
import com.meshcoreone.android.core.connectivity.SystemLinkAdopter
import com.meshcoreone.android.core.connectivity.ble.BleLinkInspector
import com.meshcoreone.android.core.connectivity.ble.BleScanCoordinator
import com.meshcoreone.android.core.connectivity.pairing.BluetoothScanPairingService
import com.meshcoreone.android.core.connectivity.pairing.CompanionPairingService
import com.meshcoreone.android.core.connectivity.pairing.CompanionSetupService
import com.meshcoreone.android.core.connectivity.pairing.DevicePairingService
import com.meshcoreone.android.core.connectivity.bond.BondingCoordinator
import com.meshcoreone.android.core.connectivity.bond.PlatformBondInspector
import com.meshcoreone.android.core.connectivity.permissions.AndroidPermissionState
import com.meshcoreone.android.core.connectivity.platform.AndroidBleScanGateway
import com.meshcoreone.android.core.connectivity.platform.AndroidBondGateway
import com.meshcoreone.android.core.connectivity.platform.AndroidCompanionDeviceGateway
import com.meshcoreone.android.core.connectivity.platform.AndroidSystemLinkProbe
import com.meshcoreone.android.core.connectivity.service.AndroidForegroundServiceStarter
import com.meshcoreone.android.core.ble.BluetoothAvailability
import com.meshcoreone.android.core.data.repository.RoomPersistenceStore
import com.meshcoreone.android.core.database.MeshCoreDatabase
import com.meshcoreone.android.core.datastore.MeshCoreStorage
import com.meshcoreone.android.core.services.rendering.DraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The production composition of [AppContainer]. What exists on main is bound; what does not is bound to an explicit
 * unavailable role and listed in WP-303.md: the BLE `RuntimeLink`, the WP-401 notification adapter, the DataStore-backed
 * endpoint/association stores, the Wi-Fi LAN network binding and TalkBack announcements.
 */
object AndroidAppContainerFactory {
    suspend fun create(application: Application, mainScope: CoroutineScope, foreground: ProcessForegroundState): AppContainer {
        val storage = MeshCoreStorage.get(application)
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val database = MeshCoreDatabase.open(application)
        val store = RoomPersistenceStore(database, storeScope)
        val diagnostics = ConnectivityDiagnostics.NONE
        val clock = SystemConnectivityClock()

        val companionGateway = AndroidCompanionDeviceGateway(application, diagnostics)
        val companionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val companionService = CompanionSetupService(
            companionGateway, companionScope, clock, diagnostics, records = SharedPreferencesAssociationRecords(application),
        )
        val pairing: DevicePairingService = if (companionGateway.isSupported) {
            CompanionPairingService(companionService, diagnostics)
        } else {
            BluetoothScanPairingService()
        }
        val probe = AndroidSystemLinkProbe(application, diagnostics)
        val endpoints = SharedPreferencesKnownEndpoints(application)
        // No BLE link is owned by the process yet, so the inspector reports an idle link and adapter availability only.
        val inspector = BleLinkInspector(probe, { null }) { BluetoothAvailability.Ready }
        val connectivity = ConnectivityPlatform(pairing, inspector, endpoints, SystemLinkAdopter { false })

        val bondGateway = AndroidBondGateway(application, diagnostics)
        val bonding = BondingCoordinator(bondGateway, clock)
        val addresses = java.util.concurrent.ConcurrentHashMap<java.util.UUID, String>()
        val holder = java.util.concurrent.atomic.AtomicReference<AppContainer>()
        val environment = AndroidHostEnvironment(
            application,
            reconnecting = { holder.get()?.connectionManager?.reconnectionCoordinator?.reconnectingDeviceId != null },
            lastDevice = { holder.get()?.connectionPort?.lastConnectedDeviceId },
            lan = {
                holder.get()?.connectionManager?.connectedDevice?.connectionMethods
                    ?.let { methods -> methods.isNotEmpty() && methods.all { it is com.meshcoreone.android.core.model.ConnectionMethod.WiFi } } == true
            },
        )
        val container = AppContainer(
            AppContainerDependencies(
                store = store,
                passwords = SecretStoreVault(storage.secrets),
                connectionPreferences = DataStoreConnectionPreferences(storage.preferences),
                connectivity = connectivity,
                linkProbe = probe,
                scans = BleScanCoordinator(AndroidBleScanGateway(application, diagnostics), diagnostics),
                linkFactory = AndroidRuntimeLinkFactory(),
                hostingStarter = AndroidForegroundServiceStarter(application),
                hostEnvironment = environment,
                ensureBonded = { deviceId ->
                    connectivity.endpointFor(deviceId)?.address?.let { address ->
                        addresses[deviceId] = address
                        bonding.ensureBonded(address)
                    }
                },
                bonds = PlatformBondInspector(android.os.Build.VERSION.SDK_INT, bondGateway) { addresses[it] },
                permissionSnapshot = AndroidPermissionState(application)::snapshot,
                companionSetup = companionService.takeIf { companionGateway.isSupported },
                refreshAssociations = { (pairing as? CompanionPairingService)?.let { companionService.refreshAssociations() } },
                notificationDelivery = UnavailableNotificationDelivery,
                notificationPreferences = storage.notificationPreferences(),
                contactPreferences = SharedPreferencesContactFlags(application),
                draftStore = DraftStore(SharedPreferencesDraftDefaults(application)),
                mainScope = mainScope,
                foreground = foreground,
                knownEndpoints = endpoints,
                regionStore = DataStoreRegionSelectionStore(storage.preferences),
                stalePreferences = DataStoreStaleCleanupPreferences(storage.preferences),
                newBootstrapDebugLog = { scope -> com.meshcoreone.android.core.services.diagnostics.DebugLogBuffer(store, scope) },
                onClose = {
                    // The Room database is process-owned (`RoomPersistenceStore.close` flushes and seals the store only);
                    // it closes with the process, and this module cannot reference `RoomDatabase.close`.
                    store.close()
                    storeScope.cancel()
                    companionScope.cancel()
                },
            ),
        )
        holder.set(container)
        return container
    }
}
