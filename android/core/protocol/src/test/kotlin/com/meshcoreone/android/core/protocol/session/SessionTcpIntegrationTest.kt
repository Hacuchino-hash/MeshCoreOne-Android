// AndroidOnly: WP-107 Real loopback TCP session ingestion with independent framing, enforced split reads and terminal failures.
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiFrameException
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransport
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import kotlin.test.*

private class SplitReadSocket : Socket() {
    val largestRead = AtomicInteger()
    val reads = AtomicInteger()
    override fun getInputStream(): InputStream = object : FilterInputStream(super.getInputStream()) {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val count = `in`.read(bytes, offset, minOf(length, 2))
            if (count > 0) {
                reads.incrementAndGet()
                largestRead.accumulateAndGet(count, ::maxOf)
            }
            return count
        }
    }
}

private class SessionTcpPeer : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    private var client: Socket? = null

    suspend fun accept() = runInterruptible(Dispatchers.IO) {
        client = server.accept().apply { soTimeout = 5000; tcpNoDelay = true }
    }

    suspend fun read(): Bytes = runInterruptible(Dispatchers.IO) {
        val input = checkNotNull(client).getInputStream()
        assertEquals(0x3c, input.read(), "Independent outbound delimiter")
        val low = input.read()
        val high = input.read()
        if (low < 0 || high < 0) throw EOFException("Missing independent outbound frame length")
        val size = low or (high shl 8)
        val data = input.readNBytes(size)
        assertEquals(size, data.size, "Independent frame is not truncated")
        Bytes(data)
    }

    suspend fun send(vararg packets: Bytes) = runInterruptible(Dispatchers.IO) {
        val output = checkNotNull(client).getOutputStream()
        val data = packets.fold(byteArrayOf()) { bytes, packet ->
            bytes + byteArrayOf(0x3e, (packet.size and 255).toByte(), (packet.size ushr 8).toByte()) + packet.toByteArray()
        }
        output.write(data)
        output.flush()
    }

    suspend fun sendTruncatedAndClose() = runInterruptible(Dispatchers.IO) {
        checkNotNull(client).getOutputStream().apply { write(byteArrayOf(0x3e, 10, 0, 0x0c, 1)); flush() }
        checkNotNull(client).close()
    }

    suspend fun closeClient() = runInterruptible(Dispatchers.IO) { checkNotNull(client).close() }
    override fun close() { client?.close(); server.close() }
}

private fun realSession(transport: WiFiTransport, scope: CoroutineScope): MeshCoreSession = MeshCoreSession(
    transport,
    SessionConfiguration(
        defaultTimeout = 2.0, clientIdentifier = "MCore", binaryRequestOverallTimeout = 3.0,
        binaryRequestRetransmitInterval = null, channelPipelineIdleTimeout = 0.5,
        channelPipelineHardTimeout = 3.0, channelPipelinePostDrainGrace = 0.0,
    ),
    coroutineContext = scope.coroutineContext,
)

class SessionTcpIntegrationTest {
    @Test
    fun `real TCP frames traverse one session drain with split reads coalesced pushes and pipelined acknowledged reads`() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                SessionTcpPeer().use { peer ->
                    val socket = SplitReadSocket()
                    val transport = WiFiTransport(socketFactory = { socket })
                    transport.setConnectionInfo("127.0.0.1", peer.port)
                    val session = realSession(transport, this)
                    try {
                        val startup = async { session.start() }
                        peer.accept()
                        assertEquals(hex("01032020202020204d436f7265"), peer.read())
                        peer.send(selfPacket())
                        startup.await()
                        assertEquals("Test", session.currentSelfInfo?.name)
                        assertTrue(transport.isConnected())
                        assertTrue(transport.supportsPipelinedReads())
                        assertFalse(transport.supportsWriteWithoutResponse())
                        val events = session.eventsTracked()
                        val battery = async { session.getBattery() }
                        assertEquals(hex("14"), peer.read())
                        peer.send(ackPacket(hex("11223344")), raw(0x80, filled(0x44, 32)), batteryPacket(4018))
                        assertEquals(4018L, battery.await().level)
                        assertEquals(hex("11223344"), assertIs<MeshEvent.Acknowledgement>(
                            events.stream.first { it is MeshEvent.Acknowledgement },
                        ).code)
                        val channels = async { session.getChannels(listOf(0u, 1u, 2u)) }
                        assertEquals(listOf(hex("1f00"), hex("1f01"), hex("1f02")), List(3) { peer.read() })
                        peer.send(channelPacket(2, "two"), channelPacket(0, "zero"), channelPacket(1, "one"))
                        val result = channels.await()
                        assertTrue(result.missing.isEmpty())
                        assertEquals(listOf("zero", "one", "two"), result.received.map { it.name })
                        assertEquals(2, socket.largestRead.get())
                        assertTrue(socket.reads.get() > 40, "Actual socket input was split across many reads")
                    } finally {
                        withContext(NonCancellable) { session.stop() }
                    }
                    assertFalse(transport.isConnected())
                }
            }
        }
    }

    @Test
    fun `real TCP peer FIN ends the session streams and releases an outstanding request`() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                SessionTcpPeer().use { peer ->
                    val transport = WiFiTransport()
                    transport.setConnectionInfo("127.0.0.1", peer.port)
                    val session = realSession(transport, this)
                    try {
                        val startup = async { session.start() }
                        peer.accept(); peer.read(); peer.send(selfPacket()); startup.await()
                        val stream = session.events()
                        val listener = async { stream.toList() }
                        val battery = async { session.getBattery() }
                        assertEquals(hex("14"), peer.read())
                        peer.closeClient()
                        assertFailsWith<MeshCoreException.ConnectionLost> { battery.await() }
                        assertIs<MeshEvent.ConnectionStateChanged>(listener.await().last())
                        assertNull(session.currentSelfInfo)
                        assertFalse(transport.isConnected())
                    } finally {
                        withContext(NonCancellable) { session.stop() }
                    }
                }
            }
        }
    }

    @Test
    fun `real TCP truncated EOF retains frame failure instead of a battery or timeout success`() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                SessionTcpPeer().use { peer ->
                    val transport = WiFiTransport()
                    transport.setConnectionInfo("127.0.0.1", peer.port)
                    val session = realSession(transport, this)
                    try {
                        val startup = async { session.start() }
                        peer.accept(); peer.read(); peer.send(selfPacket()); startup.await()
                        val battery = async { session.getBattery() }
                        assertEquals(hex("14"), peer.read())
                        peer.sendTruncatedAndClose()
                        val failure = assertFailsWith<MeshCoreException.ConnectionLost> { battery.await() }
                        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is WiFiFrameException })
                        assertFalse(transport.isConnected())
                    } finally {
                        withContext(NonCancellable) { session.stop() }
                    }
                }
            }
        }
    }
}
