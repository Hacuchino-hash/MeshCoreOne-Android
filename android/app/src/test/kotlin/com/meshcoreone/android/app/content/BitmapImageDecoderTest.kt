// AndroidOnly: WP-218 real BitmapFactory-backed ImageDecoding native adapter test. Robolectric
// 4.17 defaults to native graphics mode, so BitmapFactory.decodeByteArray here is the real Skia
// decode path, not a width/height stub -- these cases exercise genuine decode/downsample/failure
// behavior, not a fake. The PNG fixture bytes are generated on the fly via javax.imageio, which
// is a host-JVM-only TEST utility here (this file runs under Robolectric on the desktop JVM, is
// never shipped to a device, and is not part of core:services' pure-JVM production surface that
// WP-218 restricts from java.awt/javax.imageio) -- production decode always goes through the real
// BitmapFactory call below, never through this fixture helper.
package com.meshcoreone.android.app.content

import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
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
        assertTrue(outcome.handle.width <= 256, "expected a downsampled width, got ${outcome.handle.width}")
        assertTrue(outcome.handle.height <= 128, "expected a downsampled height, got ${outcome.handle.height}")
    }

    @Test
    fun `an empty payload fails explicitly rather than returning a silent empty success`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        val outcome = decoder.decode(ByteArray(0))

        assertIs<ImageDecodeOutcome.Failed>(outcome)
    }

    @Test
    fun `garbage bytes that are not any recognized image fail explicitly`() = runTest {
        val decoder = BitmapImageDecoder(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        val outcome = decoder.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        assertIs<ImageDecodeOutcome.Failed>(outcome)
    }
}
