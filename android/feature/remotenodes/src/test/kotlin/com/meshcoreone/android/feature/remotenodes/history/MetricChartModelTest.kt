// AndroidOnly: WP-313 Native tests for the MetricChartView model (no Swift suite covers it): empty state, scrub snapping, readouts and domains.
package com.meshcoreone.android.feature.remotenodes.history

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.OCVPreset
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.support.EPOCH
import com.meshcoreone.android.feature.remotenodes.telemetry.ChartAccent
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

internal fun at(seconds: Long): Instant = EPOCH.plusSeconds(seconds)

internal fun point(seconds: Long, value: Double, id: UUID = UUID.randomUUID()) = ChartDataPoint(id, at(seconds), value)

class MetricChartModelTest {
    private val title = RemoteNodesText.Verbatim("SNR")
    private val a = RemoteNodesText.Verbatim("A")
    private val b = RemoteNodesText.Verbatim("B")

    private fun single(vararg points: ChartDataPoint, unit: String = "dB") =
        MetricChartModel.single(title, unit, points.toList(), ChartAccent.BLUE)

    @Test
    fun `single-series initializer names the series after the chart`() {
        val chart = single(point(0, 1.0))
        assertFalse(chart.isMultiSeries)
        assertEquals(listOf(ChartSeries(title, ChartAccent.BLUE, chart.series[0].dataPoints)), chart.series)
    }

    @Test
    fun `hasEnoughData needs two points in one series`() {
        assertFalse(single(point(0, 1.0)).hasEnoughData)
        assertTrue(single(point(0, 1.0), point(60, 2.0)).hasEnoughData)
        val split = MetricChartModel(
            title, "", listOf(ChartSeries(a, ChartAccent.BLUE, listOf(point(0, 1.0))), ChartSeries(b, ChartAccent.ORANGE, listOf(point(5, 2.0)))),
        )
        assertTrue(split.isMultiSeries)
        assertFalse(split.hasEnoughData)
    }

    @Test
    fun `empty state sums first values of drawn series and drops empty series`() {
        val chart = MetricChartModel(
            title, "",
            listOf(
                ChartSeries(a, ChartAccent.BLUE, listOf(point(0, 3.0))),
                ChartSeries(b, ChartAccent.ORANGE, listOf(point(0, 4.0))),
                ChartSeries(RemoteNodesText.Verbatim("C"), ChartAccent.RED, emptyList()),
            ),
        )
        assertEquals(listOf(a, b), chart.drawnSeries.map { it.name })
        assertEquals(7.0, chart.emptyStateValue)
        assertEquals("7 ", chart.emptyState(EN_US).valueText)
        assertEquals(RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesHistoryCheckBack), chart.emptyState(EN_US).message)
        assertEquals("3.85 V", single(point(0, 3.85), unit = "V").emptyState(EN_US).valueText)
        assertNull(single().emptyStateValue)
        assertNull(single().emptyState(EN_US).valueText)
    }

    @Test
    fun `scrub snaps to the nearest plotted date and keeps the first on ties`() {
        val chart = single(point(0, 5.5), point(100, 6.25))
        assertNull(chart.scrubDate(null))
        assertEquals(at(0), chart.scrubDate(at(40)))
        assertEquals(at(100), chart.scrubDate(at(51)))
        assertEquals(at(0), chart.scrubDate(at(50)))
        assertEquals(at(100), chart.scrubDate(at(5_000)))
        assertTrue(chart.selections(null).isEmpty())
    }

    @Test
    fun `selections keep the first equally near point per series`() {
        val first = point(0, 1.0)
        val chart = single(first, point(100, 2.0))
        assertEquals(listOf(ChartSeriesSelection(chart.series[0], first)), chart.selections(at(50)))
    }

    @Test
    fun `single-series readout shows value with unit and the point date`() {
        val chart = single(point(0, 5.5), point(100, 6.25))
        val readout = chart.readout(at(80), EN_US)
        assertEquals(ChartReadout(false, listOf(ChartReadoutEntry(title, ChartAccent.BLUE, "6.25 dB")), at(100)), readout)
        assertNull(chart.readout(null, EN_US))
        assertEquals("1,234.5 dB", single(point(0, 1234.5), point(9, 1.0)).readout(at(0), EN_US)?.entries?.single()?.valueText)
    }

    @Test
    fun `multi-series readout shows each series nearest value and the shared scrub date`() {
        val chart = MetricChartModel(
            title, "",
            listOf(
                ChartSeries(a, ChartAccent.BLUE, listOf(point(0, 1.0), point(100, 2.0))),
                ChartSeries(b, ChartAccent.ORANGE, listOf(point(60, 7.0))),
            ),
        )
        val readout = chart.readout(at(55), EN_US)
        assertEquals(
            ChartReadout(
                true,
                listOf(ChartReadoutEntry(a, ChartAccent.BLUE, "2"), ChartReadoutEntry(b, ChartAccent.ORANGE, "7")),
                at(60),
            ),
            readout,
        )
    }

    @Test
    fun `sharedDomain spans zero to max with five percent headroom`() {
        val domain = MetricChartModel.sharedDomain(listOf(listOf(point(0, 10.0)), listOf(point(0, 100.0)), emptyList()))
        assertEquals(0.0, domain?.start)
        assertEquals(105.0, domain?.endInclusive ?: 0.0, 1e-9)
        assertNull(MetricChartModel.sharedDomain(listOf(emptyList(), emptyList())))
        assertNull(MetricChartModel.sharedDomain(listOf(listOf(point(0, 0.0)))))
    }

    @Test
    fun `voltage domain converts OCV millivolts, unions data and floors at zero`() {
        val ocv = OCVPreset.LI_ION.ocvArray.toList()
        val plain = MetricChartModel.voltageChartDomain(ocv)
        assertEquals(2.6, plain?.start ?: 0.0, 1e-9)
        assertEquals(4.69, plain?.endInclusive ?: 0.0, 1e-9)

        val outliers = MetricChartModel.voltageChartDomain(ocv, listOf(point(0, 2.9), point(1, 4.4)))
        assertEquals(2.4, outliers?.start ?: 0.0, 1e-9)
        assertEquals(4.9, outliers?.endInclusive ?: 0.0, 1e-9)

        val floored = MetricChartModel.voltageChartDomain(OCVPreset.ALKALINE.ocvArray.toList(), bufferMillivolts = 2000)
        assertEquals(0.0, floored?.start)
        assertEquals(3.58, floored?.endInclusive ?: 0.0, 1e-9)
        assertNull(MetricChartModel.voltageChartDomain(emptyList()))
    }
}
