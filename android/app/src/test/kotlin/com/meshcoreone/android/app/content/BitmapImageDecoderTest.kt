// AndroidOnly: WP-218 real BitmapFactory-backed ImageDecoding native adapter test. This module
// declares only org.robolectric:robolectric + the preinstrumented android-all SDK jar (see
// android/gradle/libs.versions.toml) -- no nativeruntime-dist-* artifact and no
// @GraphicsMode(NATIVE) -- so BitmapFactory.decodeByteArray runs under Robolectric's legacy
// shadow, which genuinely parses real format header bytes (PNG/JPEG/etc. magic numbers + IHDR)
// for width/height on real encoded data, but defaults to fabricating a success Bitmap for data
// it can't recognize (see `setAllowInvalidImageData` usage below). These cases still exercise
// genuine decode/downsample/header-driven failure behavior against the real production
// BitmapFactory call path, not a hand-rolled fake. The PNG fixture bytes are generated on the fly
// via javax.imageio, which is a host-JVM-only TEST utility here (this file runs under Robolectric
// on the desktop JVM, is never shipped to a device, and is not part of core:services' pure-JVM
// production surface that WP-218 restricts from java.awt/javax.imageio) -- production decode
// always goes through the real BitmapFactory call below, never through this fixture helper.
package com.meshcoreone.android.app.content

import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import com.meshcoreone.android.core.services.content.BoundedHttpFetching
import com.meshcoreone.android.core.services.content.CachedDecodedPreview
import com.meshcoreone.android.core.services.content.DecodedImageHandle
import com.meshcoreone.android.core.services.content.HttpFetchAttempt
import com.meshcoreone.android.core.services.content.LinkPreviewScraper
import com.meshcoreone.android.core.model.LinkPreviewDataDTO
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37])
class BitmapImageDecoderTest {
    private fun realPngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    @Test
    fun `decodes a real PNG to a handle with the genuine dimensions`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        val png = realPngBytes(width = 64, height = 32)

        val outcome = decoder.decode(png)

        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        assertEquals(64, outcome.handle.width)
        assertEquals(32, outcome.handle.height)
        assertTrue(outcome.handle.costBytes > 0)
    }

    @Test
    fun `downsamples toward maxDimension rather than decoding at full resolution`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        val png = realPngBytes(width = 512, height = 256)

        val outcome = decoder.decode(png, maxDimension = 128)

        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        assertTrue(outcome.handle.width <= 128, "expected a downsampled width, got ${outcome.handle.width}")
        assertTrue(outcome.handle.height <= 128, "expected a downsampled height, got ${outcome.handle.height}")
    }

    @Test
    fun `downsampledImage honors maxPixelSize`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        val outcome = decoder.decode(realPngBytes(64, 64), maxDimension = 32)

        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        assertTrue(maxOf(outcome.handle.width, outcome.handle.height) <= 32)
    }

    @Test
    fun `decoded preview cost reflects real Bitmap byte cost`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        val outcome = decoder.decode(realPngBytes(100, 50))
        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        val entry = CachedDecodedPreview<DecodedImageHandle, DecodedImageHandle>(
            dto = LinkPreviewDataDTO(url = "https://example.invalid/cost"),
            hero = outcome.handle,
            icon = null,
            heroCost = { it.costBytes.toLong() },
            iconCost = { it.costBytes.toLong() },
        )

        assertTrue(entry.cost >= 20_000)
    }

    @Test
    fun `loadImageData returns decoded data for a valid image`() = runTest {
        val png = realPngBytes(4, 4)
        val fetching = object : BoundedHttpFetching {
            override suspend fun fetch(url: String, timeoutMs: Long, rangeHeader: String?): HttpFetchAttempt =
                HttpFetchAttempt.Started(200, "image/png", png.size.toLong(), closeResponse = {}) { receive ->
                    receive(png)
                }
        }
        val scraper = LinkPreviewScraper(fetching, BitmapPreviewImageProcessor(), isUrlSafe = { true })
        val fetched = assertNotNull(scraper.loadImageData("https://example.com/photo.png"))
        val outcome = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined).decode(fetched)

        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        assertTrue(outcome.handle.width > 0)
    }

    @Test
    fun `an empty payload fails explicitly rather than returning a silent empty success`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        val outcome = decoder.decode(ByteArray(0))

        assertIs<ImageDecodeOutcome.Failed>(outcome)
    }

    @Test
    fun `garbage bytes that are not any recognized image fail explicitly`() = runTest {
        // Robolectric's legacy (non-native) graphics shadow defaults to
        // `allowInvalidImageData = true`, which fabricates a 100x100 success Bitmap for ANY
        // byte array BitmapFactory can't actually parse -- the opposite of real Android/Skia,
        // which returns null for unrecognized data. This project does not declare Robolectric's
        // native-graphics runtime artifact (confirmed via android/gradle/libs.versions.toml: no
        // org.robolectric:nativeruntime-dist-* coordinate, no @GraphicsMode(NATIVE) usage), so
        // the header comment above claiming "real Skia decode path" was inaccurate for garbage,
        // non-format bytes specifically (genuine PNG bytes above ARE correctly header-parsed by
        // the legacy shadow, which is why those two tests pass unaided). Forcing
        // allowInvalidImageData=false here restores real-Android null-on-garbage semantics for
        // this one assertion without touching the two genuine-PNG tests' passing behavior.
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        val outcome = decoder.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        assertIs<ImageDecodeOutcome.Failed>(outcome)
    }
}
