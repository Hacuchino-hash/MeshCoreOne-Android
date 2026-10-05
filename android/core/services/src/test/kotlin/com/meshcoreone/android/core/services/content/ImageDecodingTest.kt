// New Kotlin-only test coverage: ImageDecoding/DecodedImageHandle/ImageDecodeOutcome are a new
// typed contract introduced this increment (no corresponding original Swift type existed as a
// protocol -- the Swift source called UIImage(data:) directly). These cases exercise the sealed
// outcome's exhaustiveness and a fake adapter's contract shape rather than any native decode.
package com.meshcoreone.android.core.services.content

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeDecodedImageHandle(
    override val width: Int,
    override val height: Int,
    override val costBytes: Int,
) : DecodedImageHandle

private class FakeImageDecoding(private val outcome: ImageDecodeOutcome) : ImageDecoding {
    var lastMaxDimension: Int? = null
        private set

    override suspend fun decode(data: ByteArray, maxDimension: Int?): ImageDecodeOutcome {
        lastMaxDimension = maxDimension
        return outcome
    }
}

/** Maps every [ImageDecodeOutcome] branch to a short label, exercising exhaustiveness. */
private fun describe(outcome: ImageDecodeOutcome): String = when (outcome) {
    is ImageDecodeOutcome.Decoded -> "decoded:${outcome.handle.width}x${outcome.handle.height}"
    is ImageDecodeOutcome.Unsupported -> "unsupported:${outcome.reason}"
    is ImageDecodeOutcome.Failed -> "failed:${outcome.reason}"
    ImageDecodeOutcome.Cancelled -> "cancelled"
}

class ImageDecodingTest {
    @Test
    fun `decoded outcome exposes the handle's typed fields`() = runTest {
        val handle = FakeDecodedImageHandle(width = 400, height = 200, costBytes = 320_000)
        val decoder = FakeImageDecoding(ImageDecodeOutcome.Decoded(handle))

        val outcome = decoder.decode(byteArrayOf(1, 2, 3), maxDimension = 1024)

        assertEquals("decoded:400x200", describe(outcome))
        assertIs<ImageDecodeOutcome.Decoded>(outcome)
        assertEquals(320_000, outcome.handle.costBytes)
        assertEquals(1024, decoder.lastMaxDimension)
    }

    @Test
    fun `unsupported outcome carries an explicit reason, not a silent empty success`() = runTest {
        val decoder = FakeImageDecoding(ImageDecodeOutcome.Unsupported("bmp not supported"))

        val outcome = decoder.decode(byteArrayOf(0x42, 0x4D), maxDimension = null)

        assertEquals("unsupported:bmp not supported", describe(outcome))
    }

    @Test
    fun `failed outcome is distinct from unsupported`() = runTest {
        val decoder = FakeImageDecoding(ImageDecodeOutcome.Failed("truncated stream"))

        val outcome = decoder.decode(byteArrayOf(), maxDimension = null)

        assertEquals("failed:truncated stream", describe(outcome))
    }

    @Test
    fun `cancelled outcome produces no handle`() = runTest {
        val decoder = FakeImageDecoding(ImageDecodeOutcome.Cancelled)

        val outcome = decoder.decode(byteArrayOf(1), maxDimension = 64)

        assertEquals("cancelled", describe(outcome))
    }

    @Test
    fun `no maxDimension is passed through as null by default`() = runTest {
        val decoder = FakeImageDecoding(ImageDecodeOutcome.Cancelled)

        decoder.decode(byteArrayOf(1))

        assertEquals(null, decoder.lastMaxDimension)
    }
}
