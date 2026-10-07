// AndroidOnly: WP-206 No-internet WiFi network request, network-bound sockets/DNS and the API 37 LAN permission gate.
package com.meshcoreone.android.core.connectivity.wifi

import com.meshcoreone.android.core.connectivity.ConnectivityClock
import com.meshcoreone.android.core.connectivity.permissions.FeatureReadiness
import com.meshcoreone.android.core.contracts.domain.Capability
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransport
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A platform network; sockets and name resolution are bound to it, never the default route. */
interface BoundNetwork {
    val id: String
    fun createSocket(): Socket
    fun resolve(host: String, port: Int): InetSocketAddress
}

interface LocalNetworkCallback {
    fun onAvailable(network: BoundNetwork)
    fun onLost(network: BoundNetwork)
    fun onUnavailable()
}

/**
 * `ConnectivityManager.requestNetwork` for `TRANSPORT_WIFI` with `NET_CAPABILITY_INTERNET` removed,
 * so a radio's own access point (no internet) satisfies it. Closing releases the request.
 */
fun interface LocalNetworkGateway {
    fun requestLocalWifi(callback: LocalNetworkCallback): AutoCloseable
}

sealed class LocalNetworkFailure(message: String) : IOException(message) {
    class PermissionDenied(val capability: Capability) : LocalNetworkFailure("Local network access is not permitted")
    class Unavailable : LocalNetworkFailure("No WiFi network is available")
    class TimedOut : LocalNetworkFailure("Timed out waiting for a WiFi network")
    class Lost : LocalNetworkFailure("The bound WiFi network was lost")
}

/** One held network request. The transport fails fast once the network is lost. */
class NetworkLease internal constructor(
    val network: BoundNetwork,
    private val registration: AutoCloseable,
    private val lostState: MutableStateFlow<Boolean>,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    val lost: StateFlow<Boolean> = lostState.asStateFlow()

    fun createSocket(): Socket {
        if (lostState.value || synchronized(lock) { closed }) throw LocalNetworkFailure.Lost()
        return network.createSocket()
    }

    fun resolve(host: String, port: Int): InetSocketAddress {
        if (lostState.value) throw LocalNetworkFailure.Lost()
        return network.resolve(host, port)
    }

    /** A WiFi transport whose sockets and DNS are scoped to this network. */
    fun transport(): WiFiTransport = WiFiTransport(socketFactory = ::createSocket, addressResolver = ::resolve)

    override fun close() {
        if (synchronized(lock) { closed.also { closed = true } }) return
        registration.close()
    }
}

/**
 * Acquires the network for a LAN companion radio. LAN denial is typed and affects only the WiFi
 * transport: BLE and cached data stay usable. The request is released on timeout, failure or
 * cancellation; a lease keeps it until closed.
 */
class LocalNetworkBinder(
    private val gateway: LocalNetworkGateway,
    private val clock: ConnectivityClock,
    private val readiness: () -> FeatureReadiness,
    private val timeout: Duration = 10.seconds,
) {
    suspend fun acquire(): NetworkLease {
        when (val ready = readiness()) {
            is FeatureReadiness.Denied -> throw LocalNetworkFailure.PermissionDenied(ready.missing.first().capability)
            is FeatureReadiness.Unsupported -> throw LocalNetworkFailure.PermissionDenied(ready.capability)
            else -> Unit
        }
        val available = CompletableDeferred<BoundNetwork>()
        val bound = AtomicReference<BoundNetwork?>(null)
        val lost = MutableStateFlow(false)
        val registration = gateway.requestLocalWifi(object : LocalNetworkCallback {
            override fun onAvailable(network: BoundNetwork) {
                if (bound.compareAndSet(null, network)) available.complete(network)
                else if (bound.get()?.id == network.id) lost.value = false
            }
            override fun onLost(network: BoundNetwork) {
                if (bound.get()?.id == network.id) lost.value = true
            }
            override fun onUnavailable() { available.completeExceptionally(LocalNetworkFailure.Unavailable()) }
        })
        try {
            val network = coroutineScope {
                val timer = launch {
                    clock.sleep(timeout)
                    available.completeExceptionally(LocalNetworkFailure.TimedOut())
                }
                try { available.await() } finally { timer.cancel() }
            }
            return NetworkLease(network, registration, lost)
        } catch (failure: Throwable) {
            registration.close()
            throw failure
        }
    }
}
