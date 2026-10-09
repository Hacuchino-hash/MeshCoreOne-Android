// AndroidOnly: WP-303 In-app scan picker port over BluetoothScanPairingService and the BLE scan coordinator.
package com.meshcoreone.android.app.container.onboarding

import com.meshcoreone.android.core.connectivity.ble.BleScanCoordinator
import com.meshcoreone.android.core.connectivity.pairing.BluetoothScanPairingService
import com.meshcoreone.android.core.ui.RSSIScanTracker
import com.meshcoreone.android.core.ui.ScanDiscovery
import com.meshcoreone.android.feature.onboarding.OnboardingScanDiscovery
import com.meshcoreone.android.feature.onboarding.OnboardingScanFallbackPort
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Presented only when companion association is unsupported ([BluetoothScanPairingService] is the pairing service).
 * Each [discoveries] collection owns one scan and one RSSI tracker; cancelling the collection stops the scan.
 */
class ScanFallbackPort(
    private val service: BluetoothScanPairingService,
    private val scans: BleScanCoordinator,
    private val clock: Clock = Clock.systemUTC(),
) : OnboardingScanFallbackPort {
    override val isRequested: StateFlow<Boolean> get() = service.isPresenting

    override fun discoveries(): Flow<List<OnboardingScanDiscovery>> = channelFlow {
        val tracker = RSSIScanTracker<UUID>(clock)
        launch {
            tracker.consume(scans.startBleScanning().map { ScanDiscovery(it.id, it.name, it.rssi.toLong()) })
        }
        tracker.devices.collect { devices ->
            send(devices.values.map { OnboardingScanDiscovery(it.discovery.id, it.discovery.name, it.tier) })
        }
    }

    override fun select(id: UUID) = service.select(id)
    override fun cancel() = service.cancel()
}
