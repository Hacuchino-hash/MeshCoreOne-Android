// AndroidOnly: WP-303 The Wi-Fi/TCP runtime link; the BLE link has no production implementation on main (see WP-303.md).
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.ConnectionTarget
import com.meshcoreone.android.core.contracts.domain.SessionToken
import com.meshcoreone.android.core.model.DevicePlatform
import com.meshcoreone.android.core.model.TransportType
import com.meshcoreone.android.core.protocol.model.DeviceCapabilities
import com.meshcoreone.android.core.protocol.transport.MeshTransport
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransport
import com.meshcoreone.android.core.runtime.ConnectionError
import com.meshcoreone.android.core.runtime.LinkCallbacks
import com.meshcoreone.android.core.runtime.RuntimeLink
import com.meshcoreone.android.core.runtime.RuntimeLinkFactory
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * A TCP radio link. Socket loss is the only link event Wi-Fi reports (there is no auto-reconnect or bond), and it
 * reaches the runtime through the transport's disconnection handler. Registration is synchronous, as the runtime
 * requires; the transport's handler slots are lock-guarded and return immediately.
 */
class WifiRuntimeLink(host: String, port: UShort, private val wifi: WiFiTransport = WiFiTransport()) : RuntimeLink {
    override val transport: MeshTransport get() = wifi
    override val type: TransportType = TransportType.WIFI

    init { runBlocking { wifi.setConnectionInfo(host, port.toInt()) } }

    override fun register(callbacks: LinkCallbacks): AutoCloseable {
        runBlocking { wifi.setDisconnectionHandler { cause -> callbacks.onDisconnected(cause) } }
        return AutoCloseable { runBlocking { wifi.clearDisconnectionHandler() } }
    }

    override suspend fun configure(capabilities: DeviceCapabilities, platform: DevicePlatform) = Unit
    override suspend fun setSessionLive(token: SessionToken?) = Unit
    override suspend fun recordBondVerification(deviceId: UUID, at: Instant) = Unit
    override suspend fun clearBondVerification(deviceId: UUID) = Unit
    override suspend fun mayRefreshBond(deviceId: UUID): Boolean = false
}

/**
 * Builds a link per connect. Wi-Fi targets get a [WifiRuntimeLink]; Bluetooth targets are refused with a typed
 * unsupported-capability error because no `RuntimeLink` over `core:ble`'s `BleTransport` is merged (the mapping of
 * its phase diagnostics, firmware capabilities and bond refresh to [LinkCallbacks] is unowned by any merged WP).
 */
class AndroidRuntimeLinkFactory(private val bluetooth: ((ConnectionTarget.Bluetooth) -> RuntimeLink)? = null) : RuntimeLinkFactory {
    override fun create(target: ConnectionTarget): RuntimeLink = when (target) {
        is ConnectionTarget.WiFi -> WifiRuntimeLink(target.host, target.port)
        is ConnectionTarget.Bluetooth -> bluetooth?.invoke(target)
            ?: throw ConnectionError.UnsupportedCapability("bluetooth runtime link")
    }
}
