// AndroidOnly: WP-109 Actual deployed runner/parser/session/socket, independent frames and injected-clock failure boundaries.
package com.meshcoreone.android.tools.meshcli

import java.io.IOException
import java.io.Writer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import kotlin.test.*

@Timeout(15)
class CliTcpTest {
    @TestFactory
    fun completeReadQueries() = listOf("device", "capabilities", "battery", "time", "contacts", "channels").map { command ->
        DynamicTest.dynamicTest("$command traverses actual TCP session and closes once") {
            runBlocking {
                supervisorScope {
                    CliPeer().use { peer ->
                        val output = CapturedConsole()
                        val socket = ObservedSocket(splitReads = true)
                        val result = async {
                            MeshCli.execute(cliArgs(peer, command), output.console, CliRuntime(socketFactory = { socket }))
                        }
                        peer.handshake()
                        when (command) {
                            "device", "capabilities" -> { peer.expect(22, 3); peer.send(deviceFrame()) }
                            "battery" -> { peer.expect(20); peer.send(batteryFrame()) }
                            "time" -> { peer.expect(5); peer.send(bytes(9) + le32(1_704_067_200)) }
                            "contacts" -> {
                                peer.expect(4)
                                peer.send(contactsStart(1), contactFrame(0xa5, "Mesh\u4f60\u597d"), contactsEnd())
                            }
                            "channels" -> {
                                peer.expect(22, 3); peer.send(deviceFrame(channels = 3))
                                repeat(3) { peer.expect(31, it) }
                                peer.send(channelFrame(2, "two"), channelFrame(0, "zero"), channelFrame(1, "one"))
                            }
                        }
                        assertEquals(0, result.await())
                        peer.expectClientClosed()
                        assertEquals(1, socket.closes.get())
                        assertEquals(1, socket.maximumConcurrentReads.get(), "Exactly one physical receive loop")
                        assertEquals(2, socket.maximumRead.get(), "Actual TCP reads were forcibly split")
                        assertTrue(output.out.toString().contains("\"status\":\"complete\""))
                        assertTrue(output.out.toString().contains("\"command\":\"$command\""))
                        assertEquals("", output.err.toString())
                        if (command in listOf("device", "capabilities")) {
                            assertFalse(output.out.toString().contains("123456"), "No BLE PIN")
                        }
                        if (command == "contacts") {
                            assertTrue(output.out.toString().contains("a5".repeat(32)), "Source-canonical complete public ID")
                            assertTrue(output.out.toString().contains("Mesh\u4f60\u597d"))
                        }
                        assertFalse(output.out.toString().contains(CHANNEL_SECRET_HEX), "No hexadecimal channel secret export")
                        assertFalse(output.out.toString().contains(CHANNEL_SECRET_MARKER), "No plaintext channel secret export")
                    }
                }
            }
        }
    }

    @Test
    fun `ACK advertisements and message content coalesced with read response are not fake success or logs`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console) }
                peer.handshake(); peer.expect(20)
                val message = bytes(17, 0, 0, 0, 0, 0, 0) + le32(1_704_067_200) + fixed("private-message-marker", 22)
                peer.send(
                    bytes(130, 0x11, 0x22, 0x33, 0x44), bytes(128) + ByteArray(32) { 0xa5.toByte() },
                    message, batteryFrame(),
                )
                assertEquals(0, result.await())
                assertTrue(output.out.toString().contains("\"millivolts\":4018"))
                assertFalse((output.out.toString() + output.err).contains("private-message-marker"))
                assertFalse((output.out.toString() + output.err).contains("a5".repeat(32)))
                peer.expectClientClosed()
            }
        }
    }

    @TestFactory
    fun terminalFailures() = listOf("unknown-error", "missing-error", "unsupported", "disabled", "empty", "unknown-packet", "short-battery", "eof", "truncated-eof").map { scenario ->
        DynamicTest.dynamicTest("$scenario is typed nonzero and never a battery result") {
            runBlocking {
                supervisorScope {
                    CliPeer().use { peer ->
                        val output = CapturedConsole()
                        val socket = ObservedSocket()
                        val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console,
                            CliRuntime(socketFactory = { socket })) }
                        peer.handshake(); peer.expect(20)
                        when (scenario) {
                            "unknown-error" -> peer.send(bytes(1, 42), batteryFrame())
                            "missing-error" -> peer.send(bytes(1), batteryFrame())
                            "unsupported" -> peer.send(bytes(1, 1))
                            "disabled" -> peer.send(bytes(15))
                            "empty" -> peer.send(byteArrayOf())
                            "unknown-packet" -> peer.send(bytes(0xff))
                            "short-battery" -> peer.send(bytes(12, 1))
                            "eof" -> peer.finishPeer()
                            "truncated-eof" -> {
                                peer.sendRaw(bytes(0x3e, 10, 0, 12, 1)); peer.finishPeer()
                            }
                        }
                        val expected = when (scenario) {
                            "unsupported", "disabled" -> 7
                            "eof" -> 5
                            else -> 4
                        }
                        assertEquals(expected, result.await())
                        assertEquals("", output.out.toString())
                        assertTrue(output.err.toString().contains("\"exit\":$expected"))
                        assertEquals(1, socket.closes.get())
                        peer.expectClientClosed()
                    }
                }
            }
        }
    }

    @Test
    fun `coalesced device rejection cannot lose the race to a valid singleton response`() = runBlocking {
        repeat(20) {
            supervisorScope {
                CliPeer().use { peer ->
                    val output = CapturedConsole()
                    val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console) }
                    peer.handshake(); peer.expect(20)
                    peer.send(bytes(1, 99), batteryFrame())
                    assertEquals(4, result.await())
                    assertEquals("", output.out.toString())
                    assertTrue(output.err.toString().contains("\"device_code\":99"))
                }
            }
        }
    }

    @TestFactory
    fun contactCompleteness() = listOf("missing-header", "count-mismatch", "duplicate-id", "valid-empty").map { scenario ->
        DynamicTest.dynamicTest("$scenario preserves real contact completeness") {
            runBlocking {
                supervisorScope {
                    CliPeer().use { peer ->
                        val output = CapturedConsole()
                        val result = async { MeshCli.execute(cliArgs(peer, "contacts"), output.console) }
                        peer.handshake(); peer.expect(4)
                        when (scenario) {
                            "missing-header" -> peer.send(contactFrame(1), contactsEnd())
                            "count-mismatch" -> peer.send(contactsStart(2), contactFrame(1), contactsEnd())
                            "duplicate-id" -> peer.send(contactsStart(2), contactFrame(1), contactFrame(1), contactsEnd())
                            else -> peer.send(contactsStart(0), contactsEnd())
                        }
                        assertEquals(if (scenario == "valid-empty") 0 else 6, result.await())
                        assertTrue(output.out.toString().contains(
                            "\"status\":\"${if (scenario == "valid-empty") "complete" else "partial"}\"",
                        ))
                        if (scenario != "valid-empty") assertTrue(output.err.toString().contains("\"kind\":\"partial\""))
                        else assertEquals("", output.err.toString())
                        peer.expectClientClosed()
                    }
                }
            }
        }
    }

    @Test
    fun `channel pipeline uses capability and real acknowledged TCP writes with gaps and ignored indexes`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val socket = ObservedSocket()
                val result = async { MeshCli.execute(cliArgs(peer, "channels", "--indices", "0,2,7"), output.console,
                    CliRuntime(socketFactory = { socket })) }
                peer.handshake(); peer.expect(22, 3); peer.send(deviceFrame())
                peer.expect(31, 0); peer.expect(31, 2); peer.expect(31, 7)
                peer.send(channelFrame(7, "seven"), channelFrame(5, "ignored"), channelFrame(2, "two"), channelFrame(0, "zero"))
                assertEquals(0, result.await())
                assertEquals(5, socket.writes.get(), "One startup, one capability query, three genuine TCP writes")
                assertFalse(output.out.toString().contains("ignored"))
                assertTrue(output.out.toString().indexOf("zero") < output.out.toString().indexOf("two"))
                peer.expectClientClosed()
            }
        }
    }

    @Test
    fun `missing channel is partial at injected idle deadline without serial fake reconciliation`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val clock = ManualCliClock()
                val output = CapturedConsole()
                val socket = ObservedSocket()
                val result = async { MeshCli.execute(cliArgs(peer, "channels", "--indices", "0,1"), output.console,
                    CliRuntime(clock, socketFactory = { socket })) }
                peer.handshake(); peer.expect(22, 3); peer.send(deviceFrame())
                peer.expect(31, 0); peer.expect(31, 1)
                clock.awaitSleepingAt(1250.milliseconds)
                clock.advanceBy(1249.milliseconds)
                assertFalse(result.isCompleted)
                clock.advanceBy(1.milliseconds)
                assertEquals(6, result.await())
                assertTrue(output.out.toString().contains("\"missing\":[0,1]"))
                assertTrue(output.out.toString().contains("\"status\":\"partial\""))
                assertEquals(4, socket.writes.get())
                assertEquals(1, socket.closes.get())
                assertEquals(0, clock.sleeperCount)
            }
        }
    }

    @Test
    fun `overall deadline covers handshake and closes real socket before any next query`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val clock = ManualCliClock()
                val output = CapturedConsole()
                val socket = ObservedSocket()
                val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console,
                    CliRuntime(clock, socketFactory = { socket })) }
                peer.accept(); peer.expect(1, 3, 32, 32, 32, 32, 32, 32, 0x4d, 0x43, 0x6f, 0x72, 0x65)
                clock.awaitSleepingAt(5000.milliseconds)
                clock.advanceBy(4999.milliseconds)
                assertFalse(result.isCompleted)
                clock.advanceBy(1.milliseconds)
                assertEquals(3, result.await())
                assertEquals("", output.out.toString())
                assertEquals(1, socket.writes.get())
                assertEquals(1, socket.closes.get())
                assertEquals(0, clock.sleeperCount)
                peer.expectClientClosed()
            }
        }
    }

    @Test
    fun `caller cancellation reports no result and closes even after owning job cancellation`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val socket = ObservedSocket()
                val clock = ManualCliClock()
                val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console,
                    CliRuntime(clock, socketFactory = { socket })) }
                peer.handshake(); peer.expect(20)
                result.cancel(CancellationException("private-cancel-marker"))
                result.join()
                assertFailsWith<CancellationException> { result.await() }
                assertEquals("", output.out.toString())
                assertFalse(output.err.toString().contains("private-cancel-marker"))
                assertEquals(1, socket.closes.get())
                assertEquals(0, clock.sleeperCount)
                peer.expectClientClosed()
            }
        }
    }

    @Test
    fun `failed teardown never publishes an otherwise valid successful query`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val socket = ObservedSocket(closeFailure = IOException("private-close-marker"))
                val result = async { MeshCli.execute(cliArgs(peer, "battery"), output.console,
                    CliRuntime(socketFactory = { socket })) }
                peer.handshake(); peer.expect(20); peer.send(batteryFrame())
                assertEquals(5, result.await())
                assertEquals("", output.out.toString())
                assertFalse(output.err.toString().contains("private-close-marker"))
                assertTrue(output.err.toString().contains("teardown_failed"))
                assertEquals(1, socket.closes.get())
            }
        }
    }

    @Test
    fun `output IOException is explicit and does not leak or reopen the transport`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val captured = CapturedConsole()
                val bad = object : Writer() {
                    override fun write(chars: CharArray, offset: Int, length: Int) { throw IOException("private-output-marker") }
                    override fun flush() = Unit
                    override fun close() = Unit
                }
                val socket = ObservedSocket()
                val result = async { MeshCli.execute(cliArgs(peer, "battery"), CliConsole(bad, captured.err),
                    CliRuntime(socketFactory = { socket })) }
                peer.handshake(); peer.expect(20); peer.send(batteryFrame())
                assertEquals(8, result.await())
                assertTrue(captured.err.toString().contains("\"kind\":\"output\""))
                assertFalse(captured.err.toString().contains("private-output-marker"))
                assertEquals(1, socket.closes.get())
            }
        }
    }

    @Test
    fun `capability rejection does not send any out of range channel command`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val socket = ObservedSocket()
                val result = async { MeshCli.execute(cliArgs(peer, "channels", "--indices", "7"), output.console,
                    CliRuntime(socketFactory = { socket })) }
                peer.handshake(); peer.expect(22, 3); peer.send(deviceFrame(channels = 2))
                assertEquals(7, result.await())
                assertEquals(2, socket.writes.get())
                assertEquals("", output.out.toString())
            }
        }
    }

    @Test
    fun `device strings stay UTF8 and terminal control characters are escaped`() = runBlocking {
        supervisorScope {
            CliPeer().use { peer ->
                val output = CapturedConsole()
                val result = async { MeshCli.execute(cliArgs(peer, "device"), output.console) }
                peer.handshake(); peer.expect(22, 3); peer.send(deviceFrame("\u4f60\u597d\u001b[2J\n\"\\\u202etail"))
                assertEquals(0, result.await())
                assertTrue(output.out.toString().contains("\u4f60\u597d"))
                assertTrue(output.out.toString().contains("\\u001b"))
                assertTrue(output.out.toString().contains("\\u202e"))
                assertFalse(output.out.toString().contains('\u001b'))
                assertFalse(output.out.toString().contains('\u202e'))
                assertEquals(1, output.out.toString().count { it == '\n' })
            }
        }
    }
}
