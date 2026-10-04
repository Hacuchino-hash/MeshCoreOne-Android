// AndroidOnly: WP-004 JVM consumption of actual Swift-produced data, not the WP-203 backup implementation.
package com.meshcoreone.android.core.testing

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.Test

class ReferenceCodecFixtureTest {
    private fun fixture(name: String): ByteArray {
        val input = javaClass.getResourceAsStream("/reference-codec/$name")
        assertNotNull(input, "Real Swift-produced fixture must be present")
        return input.use { it.readBytes() }
    }

    private fun inflateFixture(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(input)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) throw DataFormatException("Incomplete or invalid reference stream")
                if (output.size() + count > 1_048_576) {
                    throw DataFormatException("Unexpected oversized test fixture")
                }
                output.write(buffer, 0, count)
            }
            if (inflater.remaining != 0) throw DataFormatException("Trailing reference data")
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    @Test
    fun actualSwiftCompressionDecodesToExactSwiftExportBytesOnTheJvm() {
        val compressed = fixture("reference-envelope.meshcoreone")
        val expected = fixture("reference-envelope.json")
        assertEquals(1835, compressed.size)
        assertEquals(5544, expected.size)
        assertContentEquals(expected, inflateFixture(compressed))
    }

    @Test
    fun authoritativeSwiftFixtureRetainsUnixFractionsBinaryUuidAndUtf8() {
        val expected = fixture("reference-envelope.json")
        val digest = Bytes(MessageDigest.getInstance("SHA-256").digest(expected)).hexString
        assertEquals("a953245efdc5c6f913914fe9c40928eb12332b68dc34ebcacd3df0c00eb1cc8c", digest)
        val text = String(expected, StandardCharsets.UTF_8)
        assertContains(text, "\"exportDate\":1700000500.9876542")
        assertContains(text, "\"avatarImageData\":\"AID\\/\"")
        assertContains(text, "\"radioID\":\"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"")
        assertContains(text, "Hi\u4f60\ud83d\ude00\u05e9\u05dc\u05d5\u05dd")
    }

    @Test
    fun truncatedActualReferenceCompressionIsNotAcceptedAsComplete() {
        val compressed = fixture("reference-envelope.meshcoreone")
        assertFailsWith<DataFormatException> {
            inflateFixture(compressed.copyOf(compressed.size / 2))
        }
    }
}
