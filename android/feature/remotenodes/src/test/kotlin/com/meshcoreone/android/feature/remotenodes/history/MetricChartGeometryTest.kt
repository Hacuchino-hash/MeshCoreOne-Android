// AndroidOnly: WP-313 Native tests for the Compose Canvas scale math, scrub hit-testing and date ticks.
package com.meshcoreone.android.feature.remotenodes.history

import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.telemetry.ChartAccent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MetricChartGeometryTest {
    private val title = RemoteNodesText.Verbatim("T")

    private fun chart(vararg points: ChartDataPoint, domain: ClosedFloatingPointRange<Double>? = null) =
        MetricChartModel.single(title, "", points.toList(), ChartAccent.BLUE, domain)

    @Test
    fun `x and y map the data extent onto the plot`() {
        val geometry = MetricChartGeometry(chart(point(0, 0.0), point(100, 10.0)), 200f, 100f)
        assertEquals(0f, geometry.x(at(0)))
        assertEquals(100f, geometry.x(at(50)))
        assertEquals(200f, geometry.x(at(100)))
        assertEquals(100f, geometry.y(0.0))
        assertEquals(50f, geometry.y(5.0))
        assertEquals(0f, geometry.y(10.0))
        assertEquals(listOf(listOf(0f to 100f, 200f to 0f)), geometry.positions())
    }

    @Test
    fun `explicit y domain wins and values outside it are not clamped`() {
        val geometry = MetricChartGeometry(chart(point(0, 0.0), point(100, 10.0), domain = 0.0..20.0), 200f, 100f)
        assertEquals(0.0..20.0, geometry.yDomain)
        assertEquals(50f, geometry.y(10.0))
        assertEquals(-50f, geometry.y(30.0))
    }

    @Test
    fun `dateAt inverts x and extrapolates outside the plot`() {
        val geometry = MetricChartGeometry(chart(point(0, 1.0), point(100, 2.0)), 200f, 100f)
        assertEquals(at(50), geometry.dateAt(100f))
        assertEquals(at(-50), geometry.dateAt(-100f))
        val model = chart(point(0, 1.0), point(100, 2.0))
        assertEquals(at(100), model.scrubDate(geometry.dateAt(150f)))
    }

    @Test
    fun `a single date and a flat series are centred`() {
        val geometry = MetricChartGeometry(chart(point(0, 4.0)), 200f, 100f)
        assertEquals(100f, geometry.x(at(0)))
        assertEquals(at(0), geometry.dateAt(10f))
        val domain = geometry.yDomain
        assertEquals(3.6, domain?.start ?: 0.0, 1e-9)
        assertEquals(4.4, domain?.endInclusive ?: 0.0, 1e-9)
        assertEquals(50f, geometry.y(4.0))
        val zero = MetricChartGeometry(chart(point(0, 0.0), point(9, 0.0)), 200f, 100f)
        assertEquals(-1.0..1.0, zero.yDomain)
    }

    @Test
    fun `an empty chart has no domain, dates or ticks`() {
        val geometry = MetricChartGeometry(chart(), 200f, 100f)
        assertNull(geometry.yDomain)
        assertNull(geometry.startDate)
        assertNull(geometry.dateAt(5f))
        assertTrue(geometry.dateTicks(UTC).isEmpty())
        assertEquals(50f, geometry.y(3.0))
    }

    @Test
    fun `date ticks pick the smallest calendar step with at most four ticks`() {
        val jan1 = Instant.parse("2024-01-01T00:00:00Z")
        val month = chart(ChartDataPoint(java.util.UUID.randomUUID(), jan1, 1.0), ChartDataPoint(java.util.UUID.randomUUID(), jan1.plusSeconds(28 * 86_400L), 2.0))
        assertEquals(
            listOf(jan1, Instant.parse("2024-01-15T00:00:00Z"), Instant.parse("2024-01-29T00:00:00Z")),
            MetricChartGeometry(month, 200f, 100f).dateTicks(UTC),
        )
        val hours = chart(
            ChartDataPoint(java.util.UUID.randomUUID(), jan1.plusSeconds(1_800), 1.0),
            ChartDataPoint(java.util.UUID.randomUUID(), jan1.plusSeconds(6 * 3_600L), 2.0),
        )
        assertEquals(
            listOf(2L, 4L, 6L).map { jan1.plusSeconds(it * 3_600) },
            MetricChartGeometry(hours, 200f, 100f).dateTicks(UTC),
        )
        val year = chart(
            ChartDataPoint(java.util.UUID.randomUUID(), Instant.parse("2023-02-10T00:00:00Z"), 1.0),
            ChartDataPoint(java.util.UUID.randomUUID(), Instant.parse("2024-01-20T00:00:00Z"), 2.0),
        )
        assertEquals(
            listOf("2023-04-01", "2023-07-01", "2023-10-01", "2024-01-01").map { Instant.parse("${it}T00:00:00Z") },
            MetricChartGeometry(year, 200f, 100f).dateTicks(UTC),
        )
    }
}
