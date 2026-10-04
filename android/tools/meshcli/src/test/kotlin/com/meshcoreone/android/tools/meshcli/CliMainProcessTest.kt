// AndroidOnly: WP-109 Real forked deployed main with production-only classpath, actual TCP and exact process exit.
package com.meshcoreone.android.tools.meshcli

import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

@Timeout(20)
class CliMainProcessTest {
    private class Child(arguments: Array<String>) : AutoCloseable {
        private val reader = Executors.newFixedThreadPool(2)
        private val javaExecutable = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
        private val builder = ProcessBuilder(
            listOf(javaExecutable, "-Xms16m", "-Xmx64m", "-XX:MaxMetaspaceSize=128m", "-XX:+UseSerialGC",
                "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8", "-cp",
                checkNotNull(System.getProperty("meshcli.runtimeClasspath")), MeshCli::class.java.name) + arguments,
        ).apply {
            val keep = setOf("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "LANG", "LC_ALL")
            val safe = environment().filterKeys { it.uppercase(java.util.Locale.ROOT) in keep }
            environment().clear()
            environment().putAll(safe)
        }
        val process: Process = builder.start()
        private val out = reader.submit<String> { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        private val err = reader.submit<String> { process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }

        fun result(): Triple<Int, String, String> {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Actual main must terminate")
            return Triple(process.exitValue(), out.get(2, TimeUnit.SECONDS), err.get(2, TimeUnit.SECONDS))
        }

        override fun close() {
            if (process.isAlive) process.destroyForcibly()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            reader.shutdownNow()
            assertTrue(reader.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `real deployed main prints help and exits zero without a peer`() {
        Child(arrayOf("--help")).use { child ->
            val (code, out, err) = child.result()
            assertEquals(0, code)
            assertTrue(out.startsWith("meshcli --host IP"))
            assertEquals("", err)
        }
    }

    @Test
    fun `real deployed main rejects unknown text with usage exit and sanitized stderr`() {
        Child(arrayOf("--host", "127.0.0.1", "private-password-marker")).use { child ->
            val (code, out, err) = child.result()
            assertEquals(2, code)
            assertEquals("", out)
            assertTrue(err.contains("\"kind\":\"usage\""))
            assertFalse(err.contains("private-password-marker"))
        }
    }

    @Test
    fun `real deployed main reads actual framed TCP and returns actual battery with zero exit`() = runBlocking {
        CliPeer().use { peer ->
            Child(cliArgs(peer, "battery")).use { child ->
                peer.handshake(); peer.expect(20); peer.send(batteryFrame())
                val (code, out, err) = child.result()
                assertEquals(0, code)
                assertTrue(out.contains("\"millivolts\":4018"))
                assertEquals("", err)
                peer.expectClientClosed()
            }
        }
    }

    @Test
    fun `real deployed main preserves unsupported nonzero exit after genuine device rejection`() = runBlocking {
        CliPeer().use { peer ->
            Child(cliArgs(peer, "battery")).use { child ->
                peer.handshake(); peer.expect(20); peer.send(bytes(1, 1))
                val (code, out, err) = child.result()
                assertEquals(7, code)
                assertEquals("", out)
                assertTrue(err.contains("\"code\":\"unsupported_command\""))
                peer.expectClientClosed()
            }
        }
    }
}
