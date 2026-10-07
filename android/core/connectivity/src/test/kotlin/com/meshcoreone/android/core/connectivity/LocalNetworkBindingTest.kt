// AndroidOnly: WP-206 No-internet WiFi binding: permission gate, availability, timeout, loss, release and bound socket/DNS use.
package com.meshcoreone.android.core.connectivity

import com.meshcoreone.android.core.connectivity.permissions.ConnectivityPermission
import com.meshcoreone.android.core.connectivity.permissions.FeatureReadiness
import com.meshcoreone.android.core.connectivity.support.scenario
import com.meshcoreone.android.core.connectivity.support.settle
import com.meshcoreone.android.core.connectivity.wifi.BoundNetwork
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkBinder
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkCallback
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkFailure
import com.meshcoreone.android.core.connectivity.wifi.LocalNetworkGateway
import com.meshcoreone.android.core.contracts.domain.Capability
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.junit.Test

class LocalNetworkBindingTest {
    private class FakeNetwork(override val id: String) : BoundNetwork {
        val created = mutableListOf<Socket>()
        val resolved = mutableListOf<String>()
        override fun createSocket(): Socket = Socket().also { created += it }
        override fun resolve(host: String, port: Int): InetSocketAddress {
            resolved += host
            return InetSocketAddress(InetAddress.getLoopbackAddress(), port)
        }
    }

    private class FakeGateway : LocalNetworkGateway {
        var callback: LocalNetworkCallback? = null
        var requests = 0
        var releases = 0
        override fun requestLocalWifi(callback: LocalNetworkCallback): AutoCloseable {
            requests++
            this.callback = callback
            return AutoCloseable { releases++ }
        }
    }

    @Test fun `LAN permission denial is typed and never requests a network`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Denied(setOf(ConnectivityPermission.ACCESS_LOCAL_NETWORK)) })
        val failure = assertFailsWith<LocalNetworkFailure.PermissionDenied> { binder.acquire() }
        assertEquals(Capability.LOCAL_NETWORK, failure.capability)
        assertEquals(0, gateway.requests)
    }

    @Test fun `an available no-internet network yields a lease bound to it`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Ready() })
        val lease = scope.async { binder.acquire() }
        settle()
        val network = FakeNetwork("wifi-1")
        gateway.callback!!.onAvailable(network)
        val held = lease.await()
        assertEquals("wifi-1", held.network.id)
        held.createSocket().close()
        assertEquals(1, network.created.size)
        held.close(); held.close()
        assertEquals(1, gateway.releases, "Release is idempotent")
    }

    @Test fun `unavailable and timed-out requests fail typed and release the request`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Ready() }, timeout = 10.seconds)
        val unavailable = scope.async { runCatching { binder.acquire() } }
        settle()
        gateway.callback!!.onUnavailable()
        assertIs<LocalNetworkFailure.Unavailable>(unavailable.await().exceptionOrNull())
        assertEquals(1, gateway.releases)
        val timed = scope.async { runCatching { binder.acquire() } }
        settle()
        clock.advanceBy(10.seconds)
        assertIs<LocalNetworkFailure.TimedOut>(timed.await().exceptionOrNull())
        assertEquals(2, gateway.releases)
    }

    @Test fun `cancellation while waiting releases the request`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Ready() })
        val pending = scope.async { binder.acquire() }
        settle()
        pending.cancel()
        assertIs<CancellationException>(runCatching { pending.await() }.exceptionOrNull())
        assertEquals(1, gateway.releases)
    }

    @Test fun `a lost network fails new sockets instead of falling back to the default route`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Ready() })
        val lease = scope.async { binder.acquire() }
        settle()
        val network = FakeNetwork("wifi-1")
        gateway.callback!!.onAvailable(network)
        val held = lease.await()
        gateway.callback!!.onLost(FakeNetwork("other"))
        held.createSocket().close()
        gateway.callback!!.onLost(FakeNetwork("wifi-1"))
        assertTrue(held.lost.value)
        assertFailsWith<LocalNetworkFailure.Lost> { held.createSocket() }
        assertFailsWith<LocalNetworkFailure.Lost> { held.resolve("radio.lan", 5000) }
    }

    @Test fun `the WiFi transport resolves and connects through the bound network`() = scenario {
        val gateway = FakeGateway()
        val binder = LocalNetworkBinder(gateway, clock, { FeatureReadiness.Ready() })
        val lease = scope.async { binder.acquire() }
        settle()
        val network = FakeNetwork("wifi-1")
        gateway.callback!!.onAvailable(network)
        val held = lease.await()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val transport = held.transport()
            transport.setConnectionInfo("meshcore-radio.lan", server.localPort)
            withContext(Dispatchers.IO) {
                transport.connect()
                server.accept().use { assertTrue(transport.isConnected()) }
                transport.disconnect()
            }
        }
        assertEquals(listOf("meshcore-radio.lan"), network.resolved)
        assertEquals(1, network.created.size, "The socket came from the bound network factory")
    }
}
