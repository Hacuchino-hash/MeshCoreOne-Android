// AndroidOnly: WP-004 Executed fixture-reader assertions, not candidate protocol/backup parity.
package com.meshcoreone.android.core.testing

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class ReferenceVectorFixtureTest {
    private fun fixtureText(): String {
        val input = javaClass.getResourceAsStream("/protocol-vectors.tsv")
        assertNotNull(input, "Independent tracked fixture must be on the actual test classpath")
        return input.use { String(it.readBytes(), StandardCharsets.UTF_8) }
    }

    private fun read(text: String) = ReferenceVectorFixture.read(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))

    @Test
    fun actualIndependentFixturesAreDiscoveredAndDecoded() {
        val vectors = read(fixtureText())
        assertEquals(49, vectors.size)
        assertEquals(vectors.size, vectors.map { it.id }.toSet().size)
        val timestamp = vectors.single { it.id == "python.setTime_1704067200" }
        assertContentEquals(byteArrayOf(6, 0x80.toByte(), 0, 0x92.toByte(), 0x65), timestamp.bytes.toByteArray())
        assertEquals("6535c34bed8e45a5ba9f8cf5b7dbb3b72a244832", timestamp.sourceBlob)
        assertTrue(timestamp.sourceLine > 0)
    }

    @Test
    fun signedJvmBytesPreserveHighBitAndSourceEndianBytes() {
        val vectors = read(fixtureText())
        val highBit = vectors.single { it.id == "swift.lpp-high-bit-uint32" }.bytes.toByteArray()
        assertEquals(128, highBit[2].toInt() and 0xff)
        val endian = vectors.single { it.id == "swift.uint32-little-endian" }
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), endian.bytes.toByteArray())
    }

    @Test
    fun immutableFixtureBytesHaveContentEqualityAndDefensiveCopies() {
        val original = byteArrayOf(1, 0x80.toByte())
        val value = Bytes(original)
        original[0] = 9
        val returned = value.toByteArray()
        returned[1] = 0
        assertEquals(Bytes(byteArrayOf(1, 0x80.toByte())), value)
        assertEquals(Bytes(byteArrayOf(1, 0x80.toByte())).hashCode(), value.hashCode())
        assertContentEquals(byteArrayOf(1, 0x80.toByte()), value.toByteArray())
    }

    @Test
    fun duplicateMissingZeroAndStaleFixtureEvidenceFail() {
        val lines = fixtureText().lines()
        for (text in listOf(
            "",
            lines.take(2).joinToString("\n") + "\n",
            fixtureText().replace(ReferenceVectorFixture.SOURCE_SHA, "f".repeat(40)),
            fixtureText() + lines[2] + "\n",
        )) {
            assertFailsWith<FixtureFormatException> { read(text) }
        }
    }

    @Test
    fun invalidHexByteCountAndSourceFieldsFail() {
        val lines = fixtureText().lines()
        val fields = lines[2].split('\t')
        for ((index, value) in listOf(3 to "xyz", 4 to "99999", 6 to "stale", 7 to "0", 8 to "invented-version")) {
            val changed = fields.toMutableList()
            changed[index] = value
            assertFailsWith<FixtureFormatException> {
                read((lines.take(2) + changed.joinToString("\t")).joinToString("\n") + "\n")
            }
        }
    }

    @Test
    fun malformedUtf8IsNotSilentlyReplaced() {
        assertFailsWith<CharacterCodingException> {
            ReferenceVectorFixture.read(ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xfe.toByte())))
        }
    }

    @Test
    fun oversizedFixtureInputFailsBeforeDecoding() {
        assertFailsWith<FixtureFormatException> {
            ReferenceVectorFixture.read(ByteArrayInputStream(ByteArray(1_048_577)))
        }
    }
}
