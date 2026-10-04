// AndroidOnly: WP-109 Actual entry/parser usage boundaries; rejected operations cannot construct a socket.
package com.meshcoreone.android.tools.meshcli

import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class CliArgumentsTest {
    @TestFactory
    fun invalidArguments() = listOf(
        emptyArray(), arrayOf("--host"), arrayOf("--host", "radio.local"), arrayOf("--host", "999.1.1.1"),
        arrayOf("--host", "127.00.0.1"), arrayOf("--host", "127.0.0.1", "--port", "0"),
        arrayOf("--host", "127.0.0.1", "--port", "65536"), arrayOf("--host", "127.0.0.1", "--port", "+5000"),
        arrayOf("--host", "127.0.0.1", "--deadline-ms", "24"),
        arrayOf("--host", "127.0.0.1", "--deadline-ms", "120001"),
        arrayOf("--host", "127.0.0.1", "--deadline-ms", "NaN"),
        arrayOf("--host", "127.0.0.1", "--deadline-ms", "99999999999999999999999999"),
        arrayOf("--host", "127.0.0.1", "--host", "127.0.0.1"),
        arrayOf("--host", "127.0.0.1", "battery", "time"),
        arrayOf("--host", "127.0.0.1", "--indices", "0"),
        arrayOf("--host", "127.0.0.1", "channels", "--indices", "0,0"),
        arrayOf("--host", "127.0.0.1", "channels", "--indices", "0,"),
        arrayOf("--host", "127.0.0.1", "channels", "--indices", "256"),
        arrayOf("--host", "127.0.0.1", "channels", "--indices", ""),
        arrayOf("--host", "[not-an-ip]"), arrayOf("--host", "fe80::1%unsafe"),
    ).mapIndexed { index, args ->
        DynamicTest.dynamicTest("invalid arguments $index reject before any transport") {
            runBlocking {
                val created = AtomicInteger()
                val output = CapturedConsole()
                val result = MeshCli.execute(args, output.console, CliRuntime(socketFactory = {
                    created.incrementAndGet(); Socket()
                }))
                assertEquals(2, result)
                assertEquals(0, created.get())
                assertEquals("", output.out.toString())
                assertTrue(output.err.toString().contains("\"kind\":\"usage\""))
            }
        }
    }

    @TestFactory
    fun forbiddenOperations() = listOf(
        "send", "send-message", "get-message", "login", "logout", "set-radio", "set-name",
        "set-channel", "factory-reset", "reboot", "sign", "export-key", "raw", "admin",
    ).map { command ->
        DynamicTest.dynamicTest("$command has no operation path") {
            runBlocking {
                val output = CapturedConsole()
                assertEquals(2, MeshCli.execute(arrayOf("--host", "127.0.0.1", command), output.console,
                    CliRuntime(socketFactory = { error("Forbidden command must not reach a socket") })))
                assertEquals("", output.out.toString())
                assertTrue(output.err.toString().contains("\"code\":\"unknown_command\""))
            }
        }
    }

    @Test
    fun `help performs no transport construction`() = runBlocking {
        for (flag in listOf("-h", "--help")) {
            val output = CapturedConsole()
            assertEquals(0, MeshCli.execute(arrayOf(flag), output.console,
                CliRuntime(socketFactory = { error("Help must not connect") })))
            assertTrue(output.out.toString().startsWith("meshcli --host IP"))
            assertEquals("", output.err.toString())
        }
    }

    @Test
    fun `unknown user text and escape sequences never enter generic errors`() = runBlocking {
        val output = CapturedConsole()
        val marker = "operator-password-\u001b[2J\nsecret"
        assertEquals(2, MeshCli.execute(arrayOf("--host", "127.0.0.1", marker), output.console))
        assertFalse((output.err.toString() + output.out).contains("operator-password"))
        assertFalse(output.err.toString().contains('\u001b'))
        assertEquals(1, output.err.toString().count { it == '\n' })
    }

    @Test
    fun `IPv4 IPv6 and default options are canonical and DNS free`() {
        for (host in listOf("127.0.0.1", "::1", "[::1]", "::ffff:127.0.0.1")) {
            val value = assertIs<CliInvocation.Query>(CliOptions.parse(arrayOf("--host", host)))
            assertTrue(value.address.isLoopbackAddress)
            assertEquals(5000, value.port)
            assertEquals(5000, value.deadlineMillis)
            assertEquals(CliCommand.DEVICE, value.command)
            assertNull(value.indices)
        }
    }

    @Test
    fun `deadline and index boundary values are admitted without unsigned wrap`() {
        for (deadline in listOf("25", "120000")) {
            val value = assertIs<CliInvocation.Query>(CliOptions.parse(arrayOf(
                "--host", "127.0.0.1", "--port", "65535", "--deadline-ms", deadline,
                "channels", "--indices", "0,255",
            )))
            assertEquals(65535, value.port)
            assertEquals(deadline.toLong(), value.deadlineMillis)
            assertEquals(listOf<UByte>(0u, 255u), value.indices)
        }
    }
}
