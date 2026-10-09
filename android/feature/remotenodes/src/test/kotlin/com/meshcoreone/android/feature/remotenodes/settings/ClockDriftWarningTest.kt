// AndroidOnly: WP-313 Native checks of the clock-drift warning against swiftc Duration.formatted oracle output.
package com.meshcoreone.android.feature.remotenodes.settings

import com.meshcoreone.android.feature.remotenodes.settings.DriftUnit.DAY
import com.meshcoreone.android.feature.remotenodes.settings.DriftUnit.HOUR
import com.meshcoreone.android.feature.remotenodes.settings.DriftUnit.MINUTE
import com.meshcoreone.android.feature.remotenodes.settings.DriftUnit.SECOND
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ClockDriftWarningTest {
    private fun parts(seconds: Double) = ClockDriftWarning.magnitudeParts(seconds).map { it.unit to it.value }

    @Test
    fun `threshold hides ordinary scatter and sign picks ahead or behind`() {
        assertNull(ClockDriftWarning.of(null))
        assertNull(ClockDriftWarning.of(299.9))
        assertNull(ClockDriftWarning.of(-299.9))
        assertNull(ClockDriftWarning.of(Double.NaN))
        assertEquals(true, ClockDriftWarning.of(300.0)?.ahead)
        assertEquals(false, ClockDriftWarning.of(-3600.0)?.ahead)
    }

    @Test
    fun `magnitude parts match Foundation units formatting`() {
        // en_US oracle: 300 "5 min", 301.5 "5 min, 2 sec", 359.9 "6 min", 3599 "59 min, 59 sec", 3600 "1 hr",
        // 3661 "1 hr, 1 min", 7325.4 "2 hr, 2 min", 86399 "24 hr, 0 min", 86400 "1 day", 90061 "1 day, 1 hr",
        // 172800.6 "2 days, 1 sec", 1000000 "11 days, 14 hr", 300.4 "5 min", 299.6 "5 min".
        assertEquals(listOf(MINUTE to 5L), parts(300.0))
        assertEquals(listOf(MINUTE to 5L, SECOND to 2L), parts(301.5))
        assertEquals(listOf(MINUTE to 6L), parts(359.9))
        assertEquals(listOf(MINUTE to 59L, SECOND to 59L), parts(3599.0))
        assertEquals(listOf(HOUR to 1L), parts(3600.0))
        assertEquals(listOf(HOUR to 1L, MINUTE to 1L), parts(3661.0))
        assertEquals(listOf(HOUR to 2L, MINUTE to 2L), parts(7325.4))
        assertEquals(listOf(HOUR to 24L, MINUTE to 0L), parts(86399.0))
        assertEquals(listOf(DAY to 1L), parts(86400.0))
        assertEquals(listOf(DAY to 1L, HOUR to 1L), parts(90061.0))
        assertEquals(listOf(DAY to 2L, SECOND to 1L), parts(172800.6))
        assertEquals(listOf(DAY to 11L, HOUR to 14L), parts(1_000_000.0))
        assertEquals(listOf(MINUTE to 5L), parts(300.4))
        assertEquals(listOf(MINUTE to 5L), parts(299.6))
    }
}
