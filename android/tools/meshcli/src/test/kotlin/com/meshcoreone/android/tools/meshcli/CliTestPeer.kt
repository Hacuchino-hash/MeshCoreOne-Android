// AndroidOnly: WP-109 Independent source-layout frames, real loopback sockets and deterministic monotonic barriers.
package com.meshcoreone.android.tools.meshcli

import com.meshcoreone.android.core.protocol.session.SessionClock
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class ManualCliClock : SessionClock {
    private data class Sleeper(val until: Duration, val continuation: CancellableContinuation<Unit>)
    private val lock = Any()
    private var instant = Duration.ZERO
    private val sleepers = mutableListOf<Sleeper>()
    private val registrations = Channel<Duration>(Channel.UNLIMITED)
    override val now: Duration get() = synchronized(lock) { instant }
    override val wallClock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    val sleeperCount: Int get() = synchronized(lock) { sleepers.size }

    override suspend fun sleepFor(duration: Duration) {
        currentCoroutineContext().ensureActive()
        suspendCancellableCoroutine<Unit> { continuation ->
            val sleeper = synchronized(lock) { Sleeper(instant + duration, continuation) }
            val immediate = synchronized(lock) {
                when {
                    !continuation.isActive -> false
                    sleeper.until <= instant -> true
                    else -> { sleepers += sleeper; false }
                }
            }
            continuation.invokeOnCancellation { synchronized(lock) { sleepers.remove(sleeper) } }
            registrations.trySend(sleeper.until).getOrThrow()
            if (immediate) continuation.resume(Unit)
        }
    }

    suspend fun awaitSleepingAt(until: Duration) {
        while (registrations.receive() != until) Unit
    }

    fun advanceBy(duration: Duration) {
        val ready = synchronized(lock) {
            instant += duration
            sleepers.filter { it.until <= instant }.also { sleepers.removeAll(it.toSet()) }
        }
        ready.forEach { it.continuation.resume(Unit) }
    }
}

internal class ObservedSocket(
    private val splitReads: Boolean = false,
    private val writeFailure: IOException? = null,
    private val closeFailure: IOException? = null,
) : Socket() {
    val closes = AtomicInteger()
    val writes = AtomicInteger()
    val reads = AtomicInteger()
    val maximumRead = AtomicInteger()
    val maximumConcurrentReads = AtomicInteger()
    private val activeReads = AtomicInteger()
    val readCompleted = Channel<Unit>(Channel.UNLIMITED)
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Synchronized
    override fun getInputStream(): InputStream = input ?: object : FilterInputStream(super.getInputStream()) {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            maximumConcurrentReads.accumulateAndGet(activeReads.incrementAndGet(), ::maxOf)
            try {
                return `in`.read(bytes, offset, if (splitReads) minOf(length, 2) else length).also { count ->
                    if (count > 0) {
                        reads.incrementAndGet()
                        maximumRead.accumulateAndGet(count, ::maxOf)
                        readCompleted.trySend(Unit).getOrThrow()
                    }
                }
            } finally {
                activeReads.decrementAndGet()
            }
        }
    }.also { input = it }

    @Synchronized
    override fun getOutputStream(): OutputStream = output ?: object : FilterOutputStream(super.getOutputStream()) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            writes.incrementAndGet()
            writeFailure?.let { throw it }
            out.write(bytes, offset, length)
        }
    }.also { output = it }

    override fun close() {
        closes.incrementAndGet()
        super.close()
        closeFailure?.let { throw it }
    }
}

internal class CliPeer : Closeable {
    private val server = ServerSocket(0, 2, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).apply {
        soTimeout = 5000
    }
    val port: Int get() = server.localPort
    private var client: Socket? = null
    val accepted = AtomicInteger()

    suspend fun accept() = runInterruptible(Dispatchers.IO) {
        client = server.accept().apply { soTimeout = 5000; tcpNoDelay = true }
        accepted.incrementAndGet()
    }

    suspend fun read(): ByteArray = runInterruptible(Dispatchers.IO) {
        val input = checkNotNull(client).getInputStream()
        assertEquals(0x3c, input.read(), "Pinned outbound '<' delimiter")
        val low = input.read()
        val high = input.read()
        if (low < 0 || high < 0) throw EOFException("Missing test peer length")
        val length = low or (high shl 8)
        input.readNBytes(length).also { assertEquals(length, it.size, "Complete independent outbound frame") }
    }

    suspend fun expect(vararg bytes: Int) = assertContentEquals(bytes.map(Int::toByte).toByteArray(), read())

    suspend fun send(vararg packets: ByteArray) = runInterruptible(Dispatchers.IO) {
        val data = ByteArrayOutputStream()
        for (packet in packets) {
            data.write(0x3e)
            data.write(packet.size and 255)
            data.write(packet.size ushr 8)
            data.write(packet)
        }
        checkNotNull(client).getOutputStream().apply { write(data.toByteArray()); flush() }
    }

    suspend fun sendRaw(bytes: ByteArray) = runInterruptible(Dispatchers.IO) {
        checkNotNull(client).getOutputStream().apply { write(bytes); flush() }
    }
    suspend fun finishPeer() = runInterruptible(Dispatchers.IO) { checkNotNull(client).shutdownOutput() }
    suspend fun expectClientClosed() = runInterruptible(Dispatchers.IO) {
        assertEquals(-1, checkNotNull(client).getInputStream().read(), "Actual TCP owner was closed")
    }
    override fun close() { client?.close(); server.close() }
}

internal class CapturedConsole {
    val out = StringWriter()
    val err = StringWriter()
    val console = CliConsole(out, err)
}

internal fun cliArgs(peer: CliPeer, command: String, vararg additional: String): Array<String> =
    arrayOf("--host", "127.0.0.1", "--port", peer.port.toString(), "--deadline-ms", "5000", command, *additional)

internal fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()
internal fun le32(value: Long): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
internal fun fixed(text: String, length: Int): ByteArray = text.toByteArray(Charsets.UTF_8).copyOf(length)

// Pinned Swift fixtures: SELF_INFO's 57-byte prefix, v10 DEVICE_INFO's 79+2 bytes, contact's 147 bytes.
internal fun selfFrame(): ByteArray = bytes(5, 1, 20, 30) + ByteArray(32) { 0x11 } +
    ByteArray(8) + bytes(0, 0, 0, 0) + le32(915_000) + le32(250_000) + bytes(11, 8) + fixed("Peer", 4)
internal fun deviceFrame(model: String = "T-Deck", channels: Int = 8): ByteArray =
    bytes(13, 10, 50, channels) + le32(123_456) + fixed("2025-01-01", 12) +
        fixed(model, 40) + fixed("1.14.0", 20) + bytes(1, 2)
internal fun batteryFrame(value: Int = 4018): ByteArray = bytes(12, value and 255, value ushr 8)
internal const val CHANNEL_SECRET_MARKER = "private-channel!"
internal const val CHANNEL_SECRET_HEX = "707269766174652d6368616e6e656c21"
internal fun channelFrame(index: Int, name: String = "General"): ByteArray =
    bytes(18, index) + fixed(name, 32) + CHANNEL_SECRET_MARKER.toByteArray(Charsets.UTF_8)
internal fun contactsStart(count: Long): ByteArray = bytes(2) + le32(count)
internal fun contactsEnd(): ByteArray = bytes(4) + le32(1_704_067_200)
internal fun contactFrame(key: Int, name: String = "Peer"): ByteArray =
    bytes(3) + ByteArray(32) { key.toByte() } + bytes(1, 0, 255) + ByteArray(64) +
        fixed(name, 32) + le32(1_704_067_200) + ByteArray(8) + le32(1_704_067_200)

internal suspend fun CliPeer.handshake() {
    accept()
    expect(1, 3, 32, 32, 32, 32, 32, 32, 0x4d, 0x43, 0x6f, 0x72, 0x65)
    send(selfFrame())
}
