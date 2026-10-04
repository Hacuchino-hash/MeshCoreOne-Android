// AndroidOnly: WP-102 Bind original cases, independent Swift packets, source blobs and MIT notice to exact inputs.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.sha256
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinnedCryptoInputsTest {
    private val revision = "db14559b39d32322b06477c6ae676112f583db50"

    @Test
    fun `Every assigned crypto blob and Python cross-reference remains at the read-only pin`() {
        val inputs = mapOf(
            "MeshCore/Sources/MeshCore/Protocol/ChannelCrypto.swift" to "0e53e6958a516369d073a43c0ce8b245282009ce",
            "MeshCore/Sources/MeshCore/Protocol/DirectMessageCrypto.swift" to "049386cb73147a7715fe0ea96358445e253eb0dd",
            "MeshCore/Sources/MeshCore/Protocol/Ed25519ToX25519.swift" to "3730ebd17b7b691fad742d5ff6f57ba27a155644",
            "MeshCore/Tests/MeshCoreTests/ChannelCryptoTests.swift" to "9e04884f23aca6685acd5f07a9f4d8ee0da8d11e",
            "MeshCore/Tests/MeshCoreTests/DirectMessageCryptoTests.swift" to "324033b90084788747edde64071cf7fe70260da2",
            "MeshCore/Tests/MeshCoreTests/Ed25519ToX25519Tests.swift" to "95e82357a2fbbe6cade2845b1384b4980af45adf",
            "MeshCore/Tests/MeshCoreTests/Fixtures/PythonReferenceBytes.swift" to "6535c34bed8e45a5ba9f8cf5b7dbb3b72a244832",
            "MeshCore/LICENSE" to "b81a317438752b8ac23cd7f4ce6e6db1bb58e894",
        )
        for ((path, expected) in inputs) assertEquals(expected, git("rev-parse", "$revision:$path").trim(), path)
    }

    @Test
    fun `All twenty-two original cases have concrete registered native methods`() {
        var count = 0
        val suites = listOf(
            "ChannelCryptoTests.swift" to ChannelCryptoTest::class.java,
            "DirectMessageCryptoTests.swift" to DirectMessageCryptoTest::class.java,
            "Ed25519ToX25519Tests.swift" to Ed25519ToX25519Test::class.java,
        )
        for ((filename, native) in suites) {
            val source = git("show", "$revision:MeshCore/Tests/MeshCoreTests/$filename")
            val original = Regex("@Test\\s+func\\s+(?:`([^`]+)`|([A-Za-z0-9]+))\\(").findAll(source)
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
            val expected = original.map {
                if (it == "Public key conversion round-trip with CryptoKit") {
                    "Public key conversion round-trip with independent RFC keys"
                } else it
            }.toSet()
            val registered = native.declaredMethods.filter { it.isAnnotationPresent(org.junit.jupiter.api.Test::class.java) }
            assertTrue(original.isNotEmpty(), "Zero original case inventory: $filename")
            assertEquals(expected, registered.map { it.name }.toSet(), filename)
            assertTrue(registered.none { it.isAnnotationPresent(org.junit.jupiter.api.Disabled::class.java) })
            assertTrue(!native.isAnnotationPresent(org.junit.jupiter.api.Disabled::class.java))
            count += original.size
        }
        assertEquals(22, count)
        val source = git("show", "$revision:MeshCore/Tests/MeshCoreTests/ChannelCryptoTests.swift")
        assertTrue(source.contains("for txtType: UInt8 in [0, 1, 2]"))
        assertEquals(listOf(0, 1, 2), CryptoFixtures.messages.filter { it.id.startsWith("channel-type-") }.map { it.type?.toInt() })
    }

    @Test
    fun `Actual Swift oracle bytes are consumed without changing their expectations`() {
        val fixture = CryptoFixtures.root.resolve("android").resolve("core").resolve("testing").resolve("fixtures")
            .resolve("reference-codec").resolve("channel-crypto-oracle.json")
        assertEquals("f6a323cf0e351d2dc7eb5bda26cb808ede7fb03dcb2bbe132e7735726deaa1f7", sha256(Bytes(fixture.readBytes())).hexString)
        val source = fixture.readText(Charsets.UTF_8)
        assertTrue(source.contains("\"source_sha\":\"$revision\""))
        val vectors = Regex("\"id\":\"([^\"]+)\",\"packet_hex\":\"([0-9a-f]+)\"").findAll(source)
            .associate { it.groupValues[1] to Bytes.fromHex(it.groupValues[2]) }
        assertEquals(setOf("normal", "high-bit-utf8"), vectors.keys)
        assertEquals(vectors["normal"], CryptoFixtures.message("channel-normal").packet)
        assertEquals(vectors["high-bit-utf8"], CryptoFixtures.message("channel-swift-high-bit").packet)
        CryptoFixtures.assertChannel(CryptoFixtures.message("channel-normal"))
        CryptoFixtures.assertChannel(CryptoFixtures.message("channel-swift-high-bit"))
    }

    @Test
    fun `Independent vector files retain their complete recorded hashes and nonzero counts`() {
        val directory = CryptoFixtures.root.resolve("docs").resolve("android").resolve("evidence").resolve("WP-102")
        val metadata = directory.resolve("vector-generation.json").readText(Charsets.US_ASCII)
        assertTrue(metadata.contains("\"source_sha\": \"$revision\""))
        assertTrue(metadata.contains("\"candidate_expected_bytes\": false"))
        val hashes = Regex("\"((?:message|key|signature)-vectors\\.tsv)\": \"([0-9a-f]{64})\"").findAll(metadata)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(setOf("message-vectors.tsv", "key-vectors.tsv", "signature-vectors.tsv"), hashes.keys)
        for ((name, expected) in hashes) {
            val canonicalText = directory.resolve(name).readText(Charsets.US_ASCII).replace("\r\n", "\n")
            assertEquals(expected, sha256(Bytes(canonicalText.toByteArray(Charsets.US_ASCII))).hexString, name)
        }
        assertEquals(74, CryptoFixtures.messages.size)
        assertEquals(19, CryptoFixtures.keys.size)
        assertEquals(13, CryptoFixtures.signatures.size)
    }

    @Test
    fun `The protocol retains the original MIT notice verbatim`() {
        val original = git("show", "$revision:MeshCore/LICENSE").replace("\r\n", "\n")
        val port = CryptoFixtures.root.resolve("android").resolve("core").resolve("protocol").resolve("LICENSE")
            .readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertEquals(original, port)
    }

    private fun git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "--no-pager", "-C", CryptoFixtures.root.toString()) + arguments).start()
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val error = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Pinned crypto Git read did not finish")
        assertEquals(0, process.exitValue(), "Pinned crypto input is unavailable: ${arguments.toList()} ($error)")
        return text
    }
}
