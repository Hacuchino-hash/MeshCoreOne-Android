// PortedFrom: MC1Tests/ThemedSurfaceRowFillTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import com.meshcoreone.android.core.designsystem.ThemeRegistry
import kotlin.test.*
import org.junit.Test

class ThemedSurfaceTest : SourceCaseProof() {
    @OriginalCase("ThemedSurfaceRowFillTests::card theme paints rows with the card tier in a normal (non-flattened) context()")
    @Test fun cardTier() = prove {
        val surfaces = assertNotNull(assertNotNull(ThemeRegistry.theme("marine")).surfaces)
        assertEquals(surfaces.card, surfaces.rowFill(false))
    }
    @OriginalCase("ThemedSurfaceRowFillTests::card theme emits no row fill when flattened, so the iPad Settings sidebar keeps native selection()")
    @Test fun flattenedTier() = prove {
        val surfaces = assertNotNull(assertNotNull(ThemeRegistry.theme("marine")).surfaces)
        assertNull(surfaces.rowFill(true))
    }
    @OriginalCase("ThemedSurfaceRowFillTests::canvas-only theme (Ember) leaves rows on the system tier regardless of flattening()")
    @Test fun canvasOnly() = prove {
        val surfaces = assertNotNull(assertNotNull(ThemeRegistry.theme("ember")).surfaces)
        assertNull(surfaces.card); assertNull(surfaces.rowFill(false)); assertNull(surfaces.rowFill(true))
    }
    @OriginalCase("ThemedSurfaceRowFillTests::default theme has no surfaces, so themed row backgrounds are a no-op()")
    @Test fun systemTiers() = prove { assertNull(ThemeRegistry.default.surfaces) }
}
