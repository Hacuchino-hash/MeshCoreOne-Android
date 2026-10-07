// PortedFrom: MC1Tests/Services/LinkPreviewServiceTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Tests/Utilities/URLSafetyCheckerTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import com.meshcoreone.android.core.services.content.HttpFetchAttempt
import com.meshcoreone.android.core.services.content.LinkPreviewScraper
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.security.NetworkSecurityPolicy
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37], shadows = [FixtureNetworkSecurityPolicyShadow::class])
class OkHttpContentFetchingTest {
    private class HttpFixture(private val respond: (String) -> ByteArray) : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        private val sockets = ConcurrentLinkedQueue<Socket>()
        val requests = ConcurrentLinkedQueue<String>()
        val accepts = AtomicInteger()
        val port: Int get() = server.localPort
        private val worker = Thread {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (error: java.net.SocketException) {
                    if (server.isClosed) break else throw error
                }
                sockets.add(socket)
                accepts.incrementAndGet()
                socket.use {
                    val reader = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val request = reader.readLine() ?: return@use
                    val headers = mutableListOf(request)
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        headers.add(header)
                    }
                    requests.add(headers.joinToString("\n"))
                    it.getOutputStream().write(respond(request))
                    it.getOutputStream().flush()
                }
            }
        }.apply { name = "WP218-owned-http-fixture"; start() }

        override fun close() {
            server.close()
            sockets.forEach(Socket::close)
            worker.join(5_000)
            check(!worker.isAlive) { "HTTP fixture worker leaked" }
        }
    }

    private fun response(
        body: ByteArray, type: String = "image/png", status: String = "200 OK", extra: String = "",
    ): ByteArray = (
        "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            extra + "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII) + body

    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val fixtureHost = "preview-fixture.example"

    private fun controlledClient(lookups: AtomicInteger = AtomicInteger()) = OkHttpContentFetching(
        lookup = { lookups.incrementAndGet(); listOf(loopback) },
        // This exception is only the loopback test transport; the public production default
        // always uses URLSafetyChecker. Redirect IP literals receive no fixture exception.
        addressSafe = { host, address -> host == fixtureHost && address.isLoopbackAddress },
    )

    @Test
    fun `loadImageData rejects a redirect to a private host`() = runBlocking {
        HttpFixture { response(ByteArray(0), status = "302 Found", extra = "Location: http://127.0.0.1/secret.jpg\r\n") }
            .use { server ->
                controlledClient().use { client ->
                    val scraper = LinkPreviewScraper(client, BitmapPreviewImageProcessor(), isUrlSafe = { true })
                    assertNull(scraper.loadImageData("http://$fixtureHost:${server.port}/photo.jpg"))
                    assertEquals(1, server.accepts.get(), "private redirect must be rejected BEFORE another connect")
                }
            }
    }

    @Test
    fun `production DNS policy rejects private and mixed results before any TCP connection`() = runBlocking {
        HttpFixture { response(byteArrayOf(1)) }.use { server ->
            for (addresses in listOf(
                emptyList(), listOf(loopback),
                listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), loopback),
            )) {
                OkHttpContentFetching(lookup = { addresses }).use { client ->
                    assertIs<HttpFetchAttempt.Failed>(
                        client.fetch("http://$fixtureHost:${server.port}/photo.png", 2_000),
                    )
                }
            }
            assertEquals(0, server.accepts.get())
        }
    }

    @Test
    fun `one validated DNS snapshot reaches the actual socket and Range and source user-agent survive`() = runBlocking {
        val lookups = AtomicInteger()
        HttpFixture { response(byteArrayOf(1, 2, 3)) }.use { server ->
            controlledClient(lookups).use { client ->
                val attempt = client.fetch("http://$fixtureHost:${server.port}/photo.png", 2_000, "bytes=0-65535")
                assertIs<HttpFetchAttempt.Started>(attempt)
                attempt.use { assertEquals(listOf<Byte>(1, 2, 3), it.readBounded(3)?.toList()) }
                assertEquals(1, lookups.get())
                val request = server.requests.single()
                assertTrue(request.contains("Range: bytes=0-65535", ignoreCase = true))
                assertTrue(request.contains("User-Agent: MC1LinkPreview/1.0", ignoreCase = true))
            }
        }
    }

    @Test
    fun `decoded gzip bytes enforce the actual cap rather than compressed Content-Length`() = runBlocking {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(ByteArray(16_384)) }
        }.toByteArray()
        HttpFixture { response(compressed, extra = "Content-Encoding: gzip\r\n") }.use { server ->
            controlledClient().use { client ->
                val attempt = client.fetch("http://$fixtureHost:${server.port}/photo.png", 2_000)
                assertIs<HttpFetchAttempt.Started>(attempt)
                attempt.use {
                    assertNull(it.expectedContentLength)
                    assertNull(it.readBounded(8_192))
                }
            }
        }
    }

    @Test
    fun `closed HTTP producer cannot make another request`() = runBlocking {
        val client = OkHttpContentFetching()
        client.close()
        client.close()
        assertIs<HttpFetchAttempt.Failed>(client.fetch("https://example.com", 2_000))
        Unit
    }
}

@Implements(NetworkSecurityPolicy::class)
class FixtureNetworkSecurityPolicyShadow {
    @Implementation
    fun isCleartextTrafficPermitted(): Boolean = true
    @Implementation
    fun isCleartextTrafficPermitted(hostname: String): Boolean = true
}
