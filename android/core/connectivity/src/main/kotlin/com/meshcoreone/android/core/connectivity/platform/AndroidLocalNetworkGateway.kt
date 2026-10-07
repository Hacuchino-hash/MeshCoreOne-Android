// AndroidOnly: WP-206 ConnectivityManager no-internet WiFi request with network-scoped sockets and DNS.
package com.meshcoreone.android.core.connectivity.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.meshcoreone.android.core.connectivity.wifi.BoundNetwork
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkCallback
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkGateway
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** A platform [Network]: sockets come from its factory and names resolve through its DNS. */
class AndroidBoundNetwork(private val network: Network) : BoundNetwork {
    override val id: String get() = network.toString()
    override fun createSocket(): Socket = network.socketFactory.createSocket()
    /** `.local` mDNS names are not resolved by network-scoped unicast DNS; use an IP or DNS name. */
    override fun resolve(host: String, port: Int): InetSocketAddress = InetSocketAddress(network.getByName(host), port)
}

class AndroidLocalNetworkGateway(context: Context) : LocalNetworkGateway {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        ?: throw IllegalStateException("ConnectivityManager unavailable")

    /** Requires install-time `CHANGE_NETWORK_STATE`; `ACCESS_LOCAL_NETWORK` is checked before requesting. */
    override fun requestLocalWifi(callback: LocalNetworkCallback): AutoCloseable {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val platform = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = callback.onAvailable(AndroidBoundNetwork(network))
            override fun onLost(network: Network) = callback.onLost(AndroidBoundNetwork(network))
            override fun onUnavailable() = callback.onUnavailable()
        }
        manager.requestNetwork(request, platform)
        val released = AtomicBoolean(false)
        return AutoCloseable { if (released.compareAndSet(false, true)) manager.unregisterNetworkCallback(platform) }
    }
}
