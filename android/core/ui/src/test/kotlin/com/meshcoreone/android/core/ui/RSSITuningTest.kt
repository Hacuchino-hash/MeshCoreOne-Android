// PortedFrom: MC1Tests/Views/Components/RSSITuningTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import kotlin.test.*
import org.junit.Test

class RSSITuningTest : SourceCaseProof() {
    private val strong = RSSITuning.STRONG_THRESHOLD
    private val medium = RSSITuning.MEDIUM_THRESHOLD
    private val band = RSSITuning.TIER_HYSTERESIS

    @OriginalCase("RSSITuningTests::isUsable accepts negative dBm readings and rejects the unavailable sentinels(rssi : Int , expected : Bool)", 5)
    @Test fun usableReadings() = prove(5) {
        for ((rssi, expected) in listOf(-50L to true, -1L to true, -127L to false, 0L to false, 10L to false)) {
            assertEquals(expected, RSSITuning.isUsable(rssi))
        }
    }
    @OriginalCase("RSSITuningTests::smooth returns the new sample unchanged when there is no prior reading()")
    @Test fun firstSample() = prove { assertEquals(-50L, RSSITuning.smooth(-50, null)) }
    @OriginalCase("RSSITuningTests::smooth weights the newest sample at 0.2 against the previous smoothed value()")
    @Test fun smoothing() = prove { assertEquals(-66L, RSSITuning.smooth(-50, -70)) }
    @OriginalCase("RSSITuningTests::first reading maps directly at the tier thresholds(rssi : Int , expected : RSSITuning . SignalTier)", 3)
    @Test fun firstTier() = prove(3) {
        for ((rssi, expected) in listOf(-60L to RSSITuning.SignalTier.STRONG,
            -80L to RSSITuning.SignalTier.MEDIUM, -81L to RSSITuning.SignalTier.WEAK)) {
            assertEquals(expected, RSSITuning.tier(null, rssi))
        }
    }
    @OriginalCase("RSSITuningTests::a reading inside the hysteresis band holds the current tier()")
    @Test fun holds() = prove {
        assertEquals(RSSITuning.SignalTier.STRONG, RSSITuning.tier(RSSITuning.SignalTier.STRONG, strong - band))
        assertEquals(RSSITuning.SignalTier.MEDIUM, RSSITuning.tier(RSSITuning.SignalTier.MEDIUM, -70))
        assertEquals(RSSITuning.SignalTier.WEAK, RSSITuning.tier(RSSITuning.SignalTier.WEAK, medium + band - 1))
    }
    @OriginalCase("RSSITuningTests::a reading clearing the band by the hysteresis margin flips the tier()")
    @Test fun clearsBand() = prove {
        assertEquals(RSSITuning.SignalTier.MEDIUM, RSSITuning.tier(RSSITuning.SignalTier.WEAK, medium + band))
        assertEquals(RSSITuning.SignalTier.WEAK, RSSITuning.tier(RSSITuning.SignalTier.STRONG, medium - band - 1))
    }
    @OriginalCase("RSSITuningTests::from medium, a reading moves up, drops, or holds at the band edges()")
    @Test fun mediumEdges() = prove {
        assertEquals(RSSITuning.SignalTier.MEDIUM, RSSITuning.tier(RSSITuning.SignalTier.MEDIUM, -70))
        assertEquals(RSSITuning.SignalTier.STRONG, RSSITuning.tier(RSSITuning.SignalTier.MEDIUM, strong + band))
        assertEquals(RSSITuning.SignalTier.MEDIUM, RSSITuning.tier(RSSITuning.SignalTier.MEDIUM, medium - band))
        assertEquals(RSSITuning.SignalTier.WEAK, RSSITuning.tier(RSSITuning.SignalTier.MEDIUM, medium - band - 1))
    }
    @OriginalCase("RSSITuningTests::a single strong reading jumps weak straight to strong()")
    @Test fun jumps() = prove {
        assertEquals(RSSITuning.SignalTier.STRONG, RSSITuning.tier(RSSITuning.SignalTier.WEAK, strong + band))
    }
    @OriginalCase("RSSITuningTests::fillLevel maps each tier to its glyph fill(tier : RSSITuning . SignalTier , expected : Double)", 3)
    @Test fun fillLevels() = prove(3) {
        for ((tier, expected) in listOf(RSSITuning.SignalTier.STRONG to 1.0,
            RSSITuning.SignalTier.MEDIUM to 0.66, RSSITuning.SignalTier.WEAK to 0.33)) {
            assertEquals(expected, tier.fillLevel)
        }
    }
    @OriginalCase("RSSITuningTests::color maps each tier to its glyph color()")
    @NativeAdaptation("licensed-native-green-yellow-red-roles")
    @Test fun colorMeanings() = prove {
        assertEquals(listOf(0L, 1L, 2L), RSSITuning.SignalTier.entries.map { it.rawValue })
        assertEquals(3, RSSITuning.SignalTier.entries.map { it.accessibilityResource }.toSet().size)
    }
}
