// AndroidOnly: WP-317 Expectations are the printed output of docs/android/evidence/WP-317/oracle.swift.txt.
package com.meshcoreone.android.feature.settings.device

import kotlin.test.Test
import kotlin.test.assertEquals

class RadioOptionsTest {
    @Test
    fun `bandwidth fallback formatting matches NumberFormatter`() {
        val table = mapOf(7799u to "7.8", 7801u to "7.8", 31249u to "31.25", 31251u to "31.25", 1u to "0", 999u to "1", 1005u to "1",
            1015u to "1.02", 1025u to "1.02", 1035u to "1.04", 62499u to "62.5", 125001u to "125", 124999u to "125", 0u to "0",
            62505u to "62.5", 4294967295u to "4294967.3")
        for ((hz, expected) in table) assertEquals(expected, RadioOptions.formatBandwidth(hz), "$hz")
        assertEquals(listOf("7.8", "10.4", "15.6", "20.8", "31.25", "41.7", "62.5", "125", "250", "500"), RadioOptions.bandwidthsHz.map(RadioOptions::formatBandwidth))
    }

    @Test
    fun `nearest bandwidth matches the oracle including ties`() {
        val table = mapOf(7799u to 7800u, 9100u to 7800u, 9101u to 10400u, 13000u to 10400u, 18200u to 15600u, 26000u to 20800u,
            36475u to 31250u, 52100u to 41700u, 93750u to 62500u, 187500u to 125000u, 375000u to 250000u, 0u to 7800u,
            900000u to 500000u, 4294967295u to 500000u, 125000u to 125000u)
        for ((hz, expected) in table) assertEquals(expected, RadioOptions.nearestBandwidth(hz), "$hz")
    }

    @Test
    fun `option ranges`() {
        assertEquals(5..12, RadioOptions.spreadingFactors)
        assertEquals(5..8, RadioOptions.codingRates)
    }
}
