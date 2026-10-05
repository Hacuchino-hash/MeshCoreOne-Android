// AndroidOnly: WP-218 real BitmapFactory-backed ImageDecoding native adapter.
// Native adaptation: core:services/content.ImageDecoding is a pure-JVM producer role (no
// android.graphics import is permitted in core:services); this is the real Android runtime
// decode/downsample/allocation producer the coordinator admitted. core:services' own
// ImageHeaderDecoder remains the independently-tested pure hand-parser for the probe path
// (cheap "what size is this" without a full decode) -- this adapter is not a claim that the
// hand parser is equivalent to BitmapFactory/ImageIO; production decode/render call sites use
// this adapter so real Android formats BitmapFactory supports (PNG/JPEG/GIF/WebP/HEIC/BMP on the
// platforms that register those formats) are genuinely decoded, not just header-probed.
package com.meshcoreone.android.app.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.meshcoreone.android.core.services.content.DecodedImageHandle
import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import com.meshcoreone.android.core.services.content.ImageDecoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Real [DecodedImageHandle] wrapping an actually-decoded [android.graphics.Bitmap]. */
class BitmapDecodedImageHandle(val bitmap: Bitmap) : DecodedImageHandle {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    /**
     * Exact decoded pixel byte cost via the real backing store's row stride, matching
     * `ImageByteCost.bytes(rowBytes, height)` -- the precise branch the pure-JVM original's
     * `cgImage.bytesPerRow * cgImage.height` arithmetic models, now available for real because
     * this handle holds an actual allocated bitmap.
     */
    override val costBytes: Int get() = bitmap.rowBytes * bitmap.height
}

/**
 * Real [ImageDecoding] producer using [BitmapFactory]. Runs decode on [Dispatchers.Default]
 * since `BitmapFactory.decodeByteArray` is a blocking, potentially expensive CPU call that must
 * not run on the caller's dispatcher uninterrupted.
 */
class BitmapImageDecoder(private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default) :
    ImageDecoding {
    override suspend fun decode(data: ByteArray, maxDimension: Int?): ImageDecodeOutcome =
        withContext(dispatcher) {
            try {
                if (data.isEmpty()) return@withContext ImageDecodeOutcome.Failed("empty payload")

                val sampleSize = if (maxDimension != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    coroutineContext.ensureActive()
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        return@withContext ImageDecodeOutcome.Failed("not a decodable image")
                    }
                    sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
                } else {
                    1
                }

                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                coroutineContext.ensureActive()

                if (bitmap == null) {
                    ImageDecodeOutcome.Failed("BitmapFactory could not decode the payload")
                } else {
                    ImageDecodeOutcome.Decoded(BitmapDecodedImageHandle(bitmap))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: OutOfMemoryError) {
                ImageDecodeOutcome.Failed("decode exceeded available memory: ${error.message}")
            } catch (error: IllegalArgumentException) {
                ImageDecodeOutcome.Failed(error.message ?: "invalid image payload")
            }
        }

    /** Smallest power-of-two `inSampleSize` that keeps both dimensions at or under [maxDimension]. */
    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxDimension && height / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
