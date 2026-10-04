// AndroidOnly: WP-105 Bind LPP fixtures, named wire IDs, MIT notice and runnable original cases to immutable Git inputs.
package com.meshcoreone.android.core.protocol.lpp

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinnedLPPInputsTest {
    private val revision = "db14559b39d32322b06477c6ae676112f583db50"
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { it.resolve("MeshCore").isDirectory() && it.resolve("android").isDirectory() }

    @Test
    fun `All five assigned input blobs remain exactly pinned`() {
        val inputs = mapOf(
            "MeshCore/LICENSE" to "b81a317438752b8ac23cd7f4ce6e6db1bb58e894",
            "MeshCore/Sources/MeshCore/LPP/LPPDecoder.swift" to "003a46d4395e8c448fb13ef4eda4dc70aa591a87",
            "MeshCore/Sources/MeshCore/LPP/LPPEncoder.swift" to "7ded40d1948b6b2e80d553b08f2731aec640f241",
            "MeshCore/Tests/MeshCoreTests/Fixtures/PythonReferenceBytes.swift" to "6535c34bed8e45a5ba9f8cf5b7dbb3b72a244832",
            "MeshCore/Tests/MeshCoreTests/Validation/LPPPythonReferenceTests.swift" to "1e9b38e49b66ead6bb051d16c6d84468060d6341",
        )
        for ((path, blob) in inputs) assertEquals(blob, git("rev-parse", "$revision:$path").trim(), path)
        val originalLicense = original("MeshCore/LICENSE").replace("\r\n", "\n")
        val protocolLicense = root.resolve("android").resolve("core").resolve("protocol").resolve("LICENSE")
            .readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertEquals(originalLicense, protocolLicense)
    }

    @Test
    fun `The six independent Python fixtures are verbatim immutable source expectations`() {
        val source = original("MeshCore/Tests/MeshCoreTests/Fixtures/PythonReferenceBytes.swift")
        val expected = Regex("static let (lpp_[a-zA-Z0-9_]+) = Data\\(\\[([^]]*)]\\)")
            .findAll(source).associate { match ->
                match.groupValues[1] to Bytes.of(
                    *Regex("0x([0-9A-Fa-f]{2})").findAll(match.groupValues[2])
                        .map { it.groupValues[1].toInt(16) }.toList().toIntArray(),
                )
            }
        assertEquals(6, expected.size, "Missing/zero independent LPP fixture discovery")
        assertEquals(expected, PythonLPPReferenceBytes.bySourceName)
    }

    @Test
    fun `Every named sensor raw ID matches the immutable Swift enum rather than Kotlin ordinal`() {
        val source = original("MeshCore/Sources/MeshCore/LPP/LPPDecoder.swift").substringBefore("public var dataSize")
        val expected = Regex("^\\s*case ([A-Za-z][A-Za-z0-9]*) = ([0-9]+)\\s*$", RegexOption.MULTILINE)
            .findAll(source).associate { match ->
                match.groupValues[1].replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase(Locale.ROOT) to
                    match.groupValues[2].toInt()
            }
        assertEquals(27, expected.size, "Incomplete original sensor enumeration")
        assertEquals(expected, LPPSensorType.entries.associate { it.name to it.rawValue.toInt() })
    }

    @Test
    fun `Every original LPP case has a concrete discovered Kotlin test method`() {
        val mapping = listOf(
            "Temperature 25.5 matches Python" to "Temperature 25_5 matches Python",
            "Temperature negative round trip" to "Temperature negative round trip",
            "Humidity 65 matches Python" to "Humidity 65 matches Python",
            "Analog input 3.3 matches Python" to "Analog input 3_3 matches Python",
            "GPS SF matches Python" to "GPS SF matches Python",
            "GPS decode round trip" to "GPS decode round trip",
            "Barometer 1013 matches Python" to "Barometer 1013 matches Python",
            "Accelerometer 1g matches Python" to "Accelerometer 1g matches Python",
            "Accelerometer decode round trip" to "Accelerometer decode round trip",
            "Multi-sensor payload" to "Multi-sensor payload",
            "Voltage encoding" to "Voltage encoding",
            "Illuminance encoding" to "Illuminance encoding",
            "Digital IO encoding" to "Digital IO encoding",
            "Gyrometer encoding" to "Gyrometer encoding",
            "Load positive decodes as 3-byte signed divided by 1000" to "Load positive decodes as 3-byte signed divided by 1000",
            "Load negative round trips through 24-bit sign extension" to "Load negative round trips through 24-bit sign extension",
            "Load consumes three bytes so the next datum stays aligned" to "Load consumes three bytes so the next datum stays aligned",
            "Generic sensor decodes high bit set as a large positive integer" to "Generic sensor decodes high bit set as a large positive integer",
        )
        val source = original("MeshCore/Tests/MeshCoreTests/Validation/LPPPythonReferenceTests.swift")
        val originals = Regex("func `([^`]+)`\\(").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(18, originals.size, "Missing original case inventory")
        assertEquals(originals, mapping.map { it.first })
        val native = LPPPythonReferenceTest::class.java.declaredMethods
            .filter { it.isAnnotationPresent(org.junit.jupiter.api.Test::class.java) }.map { it.name }.toSet()
        assertEquals(mapping.map { it.second }.toSet(), native)
    }

    private fun original(path: String): String = git("show", "$revision:$path")

    private fun git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "--no-pager", "-C", root.toString()) + arguments).start()
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val error = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Pinned Git read did not finish")
        assertEquals(0, process.exitValue(), "Immutable LPP input is unavailable: ${arguments.toList()} ($error)")
        return text
    }
}
