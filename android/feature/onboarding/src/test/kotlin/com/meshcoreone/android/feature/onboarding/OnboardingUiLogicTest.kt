// AndroidOnly: WP-305 Pure logic behind the Compose steps (animation geometry, formatting, search filter).
package com.meshcoreone.android.feature.onboarding

import com.meshcoreone.android.feature.onboarding.ui.MeshAnimationModel
import com.meshcoreone.android.feature.onboarding.ui.formatPresetFrequency
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class OnboardingUiLogicTest {
    private val delta = 1e-6f

    @Test fun messageStartsAtFirstPathNodeAndTravelsSegments() {
        val m = MeshAnimationModel
        val start = m.messagePosition(0.0)
        assertEquals(m.nodes[3].x, start.x, delta); assertEquals(m.nodes[3].y, start.y, delta)
        val half = m.messagePosition(m.MESSAGE_CYCLE_SECONDS / 6)   // halfway through segment 0 (3 -> 4)
        assertEquals((m.nodes[3].x + m.nodes[4].x) / 2, half.x, delta)
        val second = m.messagePosition(m.MESSAGE_CYCLE_SECONDS / 3) // exactly at node 4
        assertEquals(m.nodes[4].x, second.x, delta); assertEquals(m.nodes[4].y, second.y, delta)
        val wrapped = m.messagePosition(m.MESSAGE_CYCLE_SECONDS * 3)
        assertEquals(start.x, wrapped.x, delta)
    }

    @Test fun edgeOpacityAndNodeRadiusStayInOriginalBounds() {
        val m = MeshAnimationModel
        for (t in 0..400) {
            val time = t / 10.0
            m.edges.indices.forEach { assertTrue(m.edgeOpacity(it, time) in 0.0 - 1e-9..0.6 + 1e-9) }
            m.nodes.indices.forEach {
                val base = if (m.nodes[it].isUser) m.USER_NODE_RADIUS else m.NODE_RADIUS
                assertTrue(m.nodeRadius(it, time) in base * 0.85 - 1e-9..base * 1.15 + 1e-9)
            }
        }
        assertEquals(7, m.edges.size); assertEquals(6, m.nodes.size); assertEquals(1, m.nodes.count { it.isUser })
    }

    @Test fun presetFrequencyUsesThreeDecimalsAndPosixLocale() {
        assertEquals("910.525 MHz", formatPresetFrequency(910.525))
        assertEquals("868.000 MHz", formatPresetFrequency(868.0))
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("433.920 MHz", formatPresetFrequency(433.92))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
