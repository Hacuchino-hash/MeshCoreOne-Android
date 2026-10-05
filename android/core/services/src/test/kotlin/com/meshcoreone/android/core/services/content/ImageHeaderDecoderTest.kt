// PortedFrom: MC1Tests/Services/ImageHeaderDecoderTests.swift@db14559b39d32322b06477c6ae676112f583db50
// The original test renders a real PNG via CGContext/CGImageDestination; this port builds
// the equivalent PNG signature + IHDR chunk bytes directly, since ImageHeaderDecoder only
// ever reads that header (it never decodes pixel data).
package com.meshcoreone.android.core.services.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageHeaderDecoderTest {
    private fun makePngHeader(width: Int, height: Int): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val bytes = mutableListOf<Byte>()
        bytes.addAll(signature.toList())
        bytes.addAll(uInt32BigEndian(13)) // IHDR chunk length
        bytes.addAll("IHDR".toByteArray(Charsets.US_ASCII).toList())
        bytes.addAll(uInt32BigEndian(width))
        bytes.addAll(uInt32BigEndian(height))
        return bytes.toByteArray()
    }

    private fun uInt32BigEndian(value: Int): List<Byte> = listOf(
        (value ushr 24 and 0xFF).toByte(),
        (value ushr 16 and 0xFF).toByte(),
        (value ushr 8 and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    @Test
    fun `decodeDimensions returns pixel dims for a valid PNG`() {
        val png = makePngHeader(width = 480, height = 270)
        val dims = ImageHeaderDecoder.decodeDimensions(png)
        assertEquals(480, dims?.first)
        assertEquals(270, dims?.second)
    }

    @Test
    fun `decodeDimensions returns null for non-image bytes`() {
        val dims = ImageHeaderDecoder.decodeDimensions("not an image".toByteArray(Charsets.UTF_8))
        assertNull(dims)
    }

    @Test
    fun `decodeDimensions returns null for empty data`() {
        val dims = ImageHeaderDecoder.decodeDimensions(ByteArray(0))
        assertNull(dims)
    }
}
