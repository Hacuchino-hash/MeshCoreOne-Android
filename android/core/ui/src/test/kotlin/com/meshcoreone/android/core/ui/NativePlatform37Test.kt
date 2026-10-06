// AndroidOnly: WP-304 Real API37 native layout/crop/locale boundaries; no physical TalkBack, permission, radio or Live Update claim.
package com.meshcoreone.android.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativePlatform37Test {
    @Test fun theActualRunnerIsApi37AndDoesNotPretendToBeHardware() {
        assertEquals(37, Build.VERSION.SDK_INT)
        assertTrue(ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density > 0)
    }

    @Test fun nativeDecoderAndCropHaveActualPixelDimensionsAndCenteredSourceColors() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint().apply { color = android.graphics.Color.BLUE }
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.color = android.graphics.Color.GREEN
        canvas.drawRect(100f, 0f, 200f, 100f, paint)
        val raw = ByteArrayOutputStream().use {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it))
            Bytes(it.toByteArray())
        }
        val decoder = AvatarImageCache()
        val image = assertIs<AvatarImageState.Ready>(decoder.decode(raw)).image
        assertEquals(200, image.width); assertEquals(100, image.height)
        val cropped = cropAvatarImage(image, AvatarCropGeometry(100.0, CropSize(200.0, 100.0))).asAndroidBitmap()
        assertEquals(512, cropped.width); assertEquals(512, cropped.height)
        assertEquals(android.graphics.Color.BLUE, cropped.getPixel(32, 256))
        assertEquals(android.graphics.Color.GREEN, cropped.getPixel(480, 256))
        assertIs<AvatarImageState.Ready>(decoder.decode(Bytes(raw.toByteArray())))
        decoder.clear()
    }

    @Test fun invalidImageBytesAreExplicitFailureNotAReportedSuccessfulDecode() {
        val failures = mutableListOf<Throwable>()
        val cache = AvatarImageCache(reporter = UiErrorReporter(failures::add))
        assertIs<AvatarImageState.Failed>(cache.decode(Bytes(byteArrayOf(1, 2, 3))))
        assertEquals(1, failures.size)
        assertEquals(AvatarImageState.Absent, cache.decode(null))
    }

    @Test fun sourceCropDecodeLimitBoundsTheActualNativeBitmapAllocation() {
        val source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.MAGENTA)
        val raw = ByteArrayOutputStream().use {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)); Bytes(it.toByteArray())
        }
        val decoded = assertIs<AvatarImageState.Ready>(AvatarImageCache().decode(raw)).image
        assertEquals(1024, decoded.width); assertEquals(512, decoded.height)
    }

    @Test fun icuNamesAndLocaleRegionSortingAreMeasuredOnTheDeclaredCurrentApi() {
        assertEquals("\uD83D\uDC69\u200D\uD83D\uDCBB", avatarInitials("Radio \uD83D\uDC69\u200D\uD83D\uDCBB"))
        assertEquals("AL", avatarInitials("Ada Lovelace"))
        assertEquals(listOf("r2", "r10"), filteredRegions(listOf("r10", "r2"), "", Locale.US))
        assertEquals(1, filteredRegions(listOf("M\u00fcnchen"), "mun", Locale.GERMAN).size)
    }

    @Test fun realJpegExifOrientationIsAppliedByTheNativeDecoderBeforeCropCoordinates() {
        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint().apply { color = android.graphics.Color.BLUE }
        canvas.drawRect(0f, 0f, 60f, 60f, paint)
        paint.color = android.graphics.Color.GREEN
        canvas.drawRect(60f, 0f, 120f, 60f, paint)
        val jpeg = ByteArrayOutputStream().use {
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)); it.toByteArray()
        }
        assertEquals(0xff, jpeg[0].toInt() and 255)
        assertEquals(0xd8, jpeg[1].toInt() and 255)
        val exif = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0,
            0x49, 0x49, 0x2a, 0, 8, 0, 0, 0,
            1, 0, 0x12, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0,
        )
        val length = exif.size + 2
        val oriented = jpeg.copyOfRange(0, 2) + byteArrayOf(0xff.toByte(), 0xe1.toByte(),
            (length ushr 8).toByte(), length.toByte()) + exif + jpeg.copyOfRange(2, jpeg.size)
        val decoded = assertIs<AvatarImageState.Ready>(AvatarImageCache().decode(Bytes(oriented))).image
        assertEquals(60, decoded.width)
        assertEquals(120, decoded.height)
        val bitmap = cropAvatarImage(decoded, AvatarCropGeometry(60.0, CropSize(60.0, 120.0))).asAndroidBitmap()
        assertTrue(android.graphics.Color.blue(bitmap.getPixel(256, 40)) > 200)
        assertTrue(android.graphics.Color.green(bitmap.getPixel(256, 472)) > 200)
    }
}
