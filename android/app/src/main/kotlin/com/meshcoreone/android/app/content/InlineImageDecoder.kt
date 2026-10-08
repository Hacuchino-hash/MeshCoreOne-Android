// PortedFrom: MC1/Services/ImageURLDetector.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import com.meshcoreone.android.core.services.content.ImageDecoding
import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import com.meshcoreone.android.core.services.content.ImageHeaderDecoder

class InlineImageDecoder(
    private val bitmaps: BitmapImageDecoder = BitmapImageDecoder(),
    private val gifs: AnimatedGifImageDecoder = AnimatedGifImageDecoder(),
) : ImageDecoding {
    override suspend fun decode(data: ByteArray, maxDimension: Int?): ImageDecodeOutcome =
        if (ImageHeaderDecoder.isGifData(data)) gifs.decode(data)
        else bitmaps.decode(data, maxDimension ?: 900)
}
