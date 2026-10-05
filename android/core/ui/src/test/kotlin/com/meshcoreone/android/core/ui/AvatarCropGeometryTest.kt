// PortedFrom: MC1Tests/Views/Components/AvatarCropGeometryTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import kotlin.test.*
import org.junit.Test

class AvatarCropGeometryTest : SourceCaseProof() {
    @OriginalCase("AvatarCropGeometryTests::fill size covers the crop square for a landscape image()")
    @Test fun landscape() = prove {
        assertEquals(CropSize(200.0, 100.0), AvatarCropGeometry(100.0, CropSize(200.0, 100.0)).baseDisplaySize)
    }
    @OriginalCase("AvatarCropGeometryTests::fill size covers the crop square for a portrait image()")
    @Test fun portrait() = prove {
        assertEquals(CropSize(100.0, 200.0), AvatarCropGeometry(100.0, CropSize(100.0, 200.0)).baseDisplaySize)
    }
    @OriginalCase("AvatarCropGeometryTests::zero image size falls back to the crop square()")
    @Test fun zeroImage() = prove {
        assertEquals(CropSize(100.0, 100.0), AvatarCropGeometry(100.0, CropSize.ZERO).baseDisplaySize)
    }
    @OriginalCase("AvatarCropGeometryTests::scale clamps to 1...4()")
    @Test fun scaleBounds() = prove {
        val geometry = AvatarCropGeometry(100.0, CropSize(100.0, 100.0))
        assertEquals(listOf(1.0, 1.0, 2.0, 4.0), listOf(0.5, 1.0, 2.0, 8.0).map(geometry::clampedScale))
    }
    @OriginalCase("AvatarCropGeometryTests::offset clamps so the crop square stays covered()")
    @Test fun covered() = prove {
        val geometry = AvatarCropGeometry(100.0, CropSize(100.0, 100.0), 2.0)
        assertEquals(CropOffset(50.0, -50.0), geometry.clampedOffset(CropOffset(1000.0, -1000.0)))
    }
    @OriginalCase("AvatarCropGeometryTests::live pinch reclamps offset to the live scale()")
    @Test fun livePinch() = prove {
        val geometry = AvatarCropGeometry(100.0, CropSize(100.0, 100.0), 4.0, CropOffset(150.0, 0.0))
        assertEquals(CropTransform(2.0, CropOffset(50.0, 0.0)), geometry.liveTransform(0.5, CropOffset.ZERO))
    }
    @OriginalCase("AvatarCropGeometryTests::draw rect at scale 1 offset 0 matches the centered fill()")
    @Test fun renderedRect() = prove {
        val rect = AvatarCropGeometry(100.0, CropSize(200.0, 100.0)).imageDrawRect()
        assertEquals(-256.0, rect.x, 0.001); assertEquals(0.0, rect.y, 0.001)
        assertEquals(1024.0, rect.width, 0.001); assertEquals(512.0, rect.height, 0.001)
    }
    @OriginalCase("AvatarCropGeometryTests::crop size shrinks on a short landscape canvas()")
    @Test fun shortWindow() = prove {
        assertEquals(216.0, AvatarCropGeometry.cropSize(CropSize(667.0, 280.0), false))
    }
    @OriginalCase("AvatarCropGeometryTests::crop size uses the regular cap on iPad()")
    @Test fun expandedWindow() = prove {
        assertEquals(480.0, AvatarCropGeometry.cropSize(CropSize(1024.0, 768.0), true))
    }

    @Test fun resizePanZoomAndInvalidGeometryDoNotCreateUncoveredOrNonfinitePixels() {
        val original = AvatarCropGeometry(100.0, CropSize(300.0, 100.0), 4.0, CropOffset(550.0, 150.0))
        assertEquals(4.0, original.resized(50.0).scale)
        assertEquals(CropOffset(275.0, 75.0), original.resized(50.0).offset)
        assertEquals(1.0, original.zoomBy(-100.0).scale)
        assertFailsWith<IllegalArgumentException> { original.clampedScale(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { AvatarCropGeometry(0.0, CropSize.ZERO) }
        assertEquals(1.0, AvatarCropGeometry.cropSize(CropSize(20.0, 20.0), false))
    }
}
