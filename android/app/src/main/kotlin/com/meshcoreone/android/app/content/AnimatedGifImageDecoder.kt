// PortedFrom: MC1/Services/ImageURLDetector.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import com.meshcoreone.android.core.services.content.DecodedImageHandle
import com.meshcoreone.android.core.services.content.GifImageMetadata
import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class GifDecodedImageHandle(
    val drawable: Drawable,
    val metadata: GifImageMetadata,
    override val width: Int,
    override val height: Int,
) : DecodedImageHandle {
    override val costBytes: Int = Math.toIntExact(width.toLong() * height * 4 * metadata.frameDelaysMillis.size)
    val isAnimated: Boolean get() = drawable is AnimatedImageDrawable
}

class AnimatedGifImageDecoder {
    suspend fun decode(data: ByteArray): ImageDecodeOutcome = withContext(Dispatchers.Default) {
        val metadata = GifImageMetadata.parse(data)
            ?: return@withContext ImageDecodeOutcome.Failed("Invalid GIF container")
        val scale = (900.0 / maxOf(metadata.width, metadata.height)).coerceAtMost(1.0)
        val width = (metadata.width * scale).roundToInt().coerceAtLeast(1)
        val height = (metadata.height * scale).roundToInt().coerceAtLeast(1)
        if (width.toLong() * height * metadata.frameDelaysMillis.size > 100L * 1024 * 1024 / 4) {
            return@withContext ImageDecodeOutcome.Failed("GIF frame working set exceeds the decoded byte bound")
        }
        try {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, _, _ ->
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            coroutineContext.ensureActive()
            ImageDecodeOutcome.Decoded(GifDecodedImageHandle(drawable, metadata, width, height))
        } catch (error: IOException) {
            ImageDecodeOutcome.Failed("GIF platform decode failed: ${error.javaClass.simpleName}")
        } catch (_: IllegalArgumentException) {
            ImageDecodeOutcome.Failed("GIF platform decode rejected the container")
        }
    }
}
