// PortedFrom: MC1/Services/ImageURLDetector.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.content

import com.meshcoreone.android.core.services.content.ImageDecodeOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedGifImageDecoderTest {
    private fun gif(): ByteArray = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(
        1, 0, 1, 0, 0x80.toByte(), 0, 0, 0, 0, 0, 0xff.toByte(), 0, 0,
        0x21, 0xf9.toByte(), 4, 4, 1, 0, 0, 0,
        0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0,
        0x21, 0xf9.toByte(), 4, 4, 10, 0, 0, 0,
        0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4c, 1, 0, 0x3b,
    )

    @Test
    fun `real native GIF producer returns animation rather than a static fake bitmap`() = runTest {
        val result = AnimatedGifImageDecoder().decode(gif())
        assertIs<ImageDecodeOutcome.Decoded>(result)
        val handle = result.handle
        assertIs<GifDecodedImageHandle>(handle)
        assertTrue(handle.isAnimated)
        assertEquals(listOf(20L, 100L), handle.metadata.frameDelaysMillis)
        assertEquals(120L, handle.metadata.durationMillis)
        assertEquals(8, handle.costBytes)
    }

    @Test
    fun `GIF frame decode budget is checked before native pixel allocation`() = runTest {
        val prefix = gif().copyOfRange(0, 19).also {
            it[6] = 0xff.toByte(); it[7] = 0xff.toByte()
            it[8] = 0xff.toByte(); it[9] = 0xff.toByte()
        }
        val frame = gif().copyOfRange(19, 42)
        val output = java.io.ByteArrayOutputStream()
        output.write(prefix)
        repeat(40) { output.write(frame) }
        output.write(0x3b)
        val oversized = output.toByteArray()
        val result = AnimatedGifImageDecoder().decode(oversized)
        assertIs<ImageDecodeOutcome.Failed>(result)
        assertEquals("GIF frame working set exceeds the decoded byte bound", result.reason)
    }
}
