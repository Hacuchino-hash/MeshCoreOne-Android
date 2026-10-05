// PortedFrom: MC1/Services/ImageHeaderDecoder.swift@db14559b39d32322b06477c6ae676112f583db50
// JDK17 javax.imageio is NOT an Android API, so dimensions are parsed directly from the
// PNG header bytes rather than via ImageIO/CGImageSource. Scope mirrors the original test
// suite (MC1Tests/Services/ImageHeaderDecoderTests.swift), which only exercises PNG input;
// other formats are explicitly out of scope for this slice (see WP-218 deviations doc).
package com.meshcoreone.android.core.services.content

/**
 * Shared image header decoder. Reads pixel width and height from an image payload
 * without decoding the full bitmap.
 */
object ImageHeaderDecoder {
    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /**
     * Returns pixel width and height parsed from the image header in [data].
     * Returns `null` if the bytes are not a recognized image or either dimension is
     * missing or non-positive.
     */
    fun decodeDimensions(data: ByteArray): Pair<Int, Int>? = decodePng(data)

    private fun decodePng(data: ByteArray): Pair<Int, Int>? {
        // Signature (8) + IHDR length (4) + "IHDR" (4) + width (4) + height (4).
        val minimumLength = pngSignature.size + 4 + 4 + 4 + 4
        if (data.size < minimumLength) return null
        for (index in pngSignature.indices) {
            if (data[index] != pngSignature[index]) return null
        }

        var offset = pngSignature.size
        val chunkLength = readUInt32BigEndian(data, offset)
        offset += 4
        val chunkType = String(data, offset, 4, Charsets.US_ASCII)
        offset += 4
        if (chunkType != "IHDR" || chunkLength < 8) return null

        val width = readUInt32BigEndian(data, offset).toInt()
        offset += 4
        val height = readUInt32BigEndian(data, offset).toInt()

        if (width <= 0 || height <= 0) return null
        return width to height
    }

    private fun readUInt32BigEndian(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }
}
