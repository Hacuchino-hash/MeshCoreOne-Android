// AndroidOnly: WP-316 Native noise-floor polling, reconnect and chart/statistics presentation cases on a virtual clock.
package com.meshcoreone.android.feature.tools.diagnostics.noisefloor

import com.meshcoreone.android.core.protocol.event.RadioStats
import com.meshcoreone.android.feature.tools.diagnostics.support.EPOCH
import com.meshcoreone.android.feature.tools.diagnostics.support.TestScheduler
import com.meshcoreone.android.feature.tools.diagnostics.support.XmlDiagnosticsText
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

class NoiseFloorPollingAndChartTest {
    private val scheduler = TestScheduler()
    private val text = XmlDiagnosticsText()
    private val holder = NoiseFloorStateHolder(scheduler.scope, text, scheduler.clock)
    private val state get() = holder.state.value

    private class FakeRadio(var noiseFloor: Short = -101, var failing: Boolean = false) : RadioStatsSource {
        var calls = 0
        override suspend fun getStatsRadio(): RadioStats {
            calls++
            if (failing) throw IllegalStateException("timeout")
            return RadioStats(noiseFloor, -70, 6.25, 0u, 0u)
        }
    }

    @Test
    fun `polls every 1_5 s on the virtual clock and stamps readings with clock time`() {
        val radio = FakeRadio()
        holder.startPolling { radio }
        scheduler.runCurrent()
        assertEquals(1, radio.calls)
        scheduler.advanceBy(1499.milliseconds)
        assertEquals(1, radio.calls)
        scheduler.advanceBy(1.milliseconds)
        assertEquals(2, radio.calls)
        assertEquals(EPOCH.plusMillis(1500), state.currentReading?.timestamp)
        assertEquals(-70, state.currentReading?.lastRSSI?.toInt())
    }

    @Test
    fun `a second start only swaps the provider and does not double the poll`() {
        val first = FakeRadio(noiseFloor = -100)
        val second = FakeRadio(noiseFloor = -90)
        holder.startPolling { first }
        holder.startPolling { second }
        scheduler.runCurrent()
        assertEquals(0, first.calls)
        assertEquals(1, second.calls)
        assertEquals(1, scheduler.clock.pendingSleepers)
    }

    @Test
    fun `disconnect and read failures surface errors and the next good reading clears them`() {
        val radio = FakeRadio()
        var connected = false
        holder.startPolling { radio.takeIf { connected } }
        scheduler.runCurrent()
        assertEquals("Device disconnected", state.errorMessage)
        connected = true
        radio.failing = true
        scheduler.advanceBy(1500.milliseconds)
        assertEquals("Unable to read radio stats", state.errorMessage)
        radio.failing = false
        scheduler.advanceBy(1500.milliseconds)
        assertNull(state.errorMessage)
        assertEquals(1, state.readings.size)
    }

    @Test
    fun `chart window is fixed until five minutes then trails the newest reading`() {
        val early = listOf(reading(-100, EPOCH.plusSeconds(10)))
        assertEquals(0.0..300.0, NoiseFloorPresentation.xDomain(early, EPOCH))
        assertEquals(0.0..300.0, NoiseFloorPresentation.xDomain(emptyList(), EPOCH))
        val late = listOf(reading(-100, EPOCH.plusMillis(400_500)))
        assertEquals(100.5..400.5, NoiseFloorPresentation.xDomain(late, EPOCH))
        assertEquals(listOf(120.0, 180.0, 240.0, 300.0, 360.0), NoiseFloorPresentation.xAxisTicks(100.5..400.5))
        assertEquals("6:00", NoiseFloorPresentation.xAxisLabel(360.0))
        assertEquals("1:00", NoiseFloorPresentation.xAxisLabel(119.9))
        assertEquals(listOf(NoiseFloorChartPoint(400.5, -100)), NoiseFloorPresentation.points(late, EPOCH))
    }

    @Test
    fun `trend compares integer half means with a three dB band`() {
        fun trend(vararg values: Int) = NoiseFloorPresentation.trendDescription(values.map { reading(it) }, text)
        assertEquals("stable", trend(-100, -90, -80))
        assertEquals("increasing", trend(-100, -100, -96, -96))
        assertEquals("stable", trend(-100, -100, -97, -97))
        assertEquals("decreasing", trend(-90, -90, -94, -94))
        // Odd count: the middle reading belongs to neither half; Int division truncates toward zero.
        assertEquals("stable", trend(-101, -102, -50, -99, -100))
    }

    @Test
    fun `accessibility labels and stat text follow the source formats`() {
        assertEquals("Noise floor history chart, no data", NoiseFloorPresentation.chartAccessibilityLabel(state, text))
        assertEquals("No reading available", NoiseFloorPresentation.currentReadingAccessibilityLabel(state, text))
        holder.appendReading(reading(-101))
        holder.appendReading(reading(-96))
        assertEquals(
            "Noise floor history: 2 readings, minimum -101 dBm, maximum -96 dBm, average -98 dBm, trend stable",
            NoiseFloorPresentation.chartAccessibilityLabel(state, text),
        )
        assertEquals("-96 dBm, Good", NoiseFloorPresentation.currentReadingAccessibilityLabel(state, text))
        assertEquals("-96", NoiseFloorPresentation.currentValueText(state, text))
        assertEquals(NoiseFloorScreenMode.CONTENT, NoiseFloorPresentation.mode(isConnected = true, state = state))
        assertEquals(NoiseFloorScreenMode.DISCONNECTED, NoiseFloorPresentation.mode(isConnected = false, state = state))
        assertEquals(NoiseFloorScreenMode.COLLECTING, NoiseFloorPresentation.mode(true, NoiseFloorState()))
    }

    @Test
    fun `one-decimal text rounds the shortest decimal half-even like Foundation`() {
        val expected = mapOf(
            -95.5 to "-95.5", -95.45 to "-95.4", -95.55 to "-95.6", -95.25 to "-95.2", 7.25 to "7.2",
            7.35 to "7.4", -1234.56 to "-1,234.6", 0.05 to "0.0", -0.04 to "-0.0",
        )
        val usText = XmlDiagnosticsText(Locale.US)
        expected.forEach { (value, formatted) ->
            assertEquals(formatted, NoiseFloorPresentation.oneDecimalText(value, usText), "value $value")
        }
        assertEquals("-1,234", NoiseFloorPresentation.integerText(-1234, usText))
    }
}
