// AndroidOnly: WP-313 Compose Canvas scale math standing in for Swift Charts' automatic x/y scales, scrub hit-testing and date-axis ticks.
package com.meshcoreone.android.feature.remotenodes.history

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Plot-area coordinates for a [MetricChartModel] of size [plotWidth] x [plotHeight] (origin top-left,
 * y grows downwards, as in Compose `DrawScope`).
 *
 * Scale choices (Swift Charts' automatic scales are not reproducible, documented deviation):
 * - x spans exactly the earliest to the latest plotted date of the drawn series; a single distinct date
 *   is centred.
 * - y uses the chart's explicit `yAxisDomain` when set (Swift `.chartYScale(domain:)`); otherwise the
 *   data minimum to maximum with no zero-inclusion or "nice" rounding. A flat series (min == max) is
 *   padded by 10 % of its magnitude, or by 1 around zero, so it draws as a centred line.
 * - coordinates are not clamped: values outside an explicit domain land outside the plot, as unclipped
 *   Swift Charts marks do.
 */
class MetricChartGeometry(
    private val chart: MetricChartModel,
    val plotWidth: Float,
    val plotHeight: Float,
) {
    private val dates: List<Instant> = chart.drawnSeries.flatMap { s -> s.dataPoints.map { it.date } }

    /** Earliest plotted date, or null for a chart without points. */
    val startDate: Instant? = dates.minOrNull()

    /** Latest plotted date, or null for a chart without points. */
    val endDate: Instant? = dates.maxOrNull()

    /** The y domain the plot maps (explicit or derived), or null for a chart without points. */
    val yDomain: ClosedFloatingPointRange<Double>? = chart.yAxisDomain ?: dataDomain(chart)

    private val spanNanos: Long =
        if (startDate != null && endDate != null) Duration.between(startDate, endDate).toNanos() else 0L

    /** Horizontal position of [date]. */
    fun x(date: Instant): Float {
        val start = startDate ?: return plotWidth / 2
        if (spanNanos == 0L) return plotWidth / 2
        val fraction = Duration.between(start, date).toNanos().toDouble() / spanNanos
        return (fraction * plotWidth).toFloat()
    }

    /** Vertical position of [value] (top of the plot is the domain's upper bound). */
    fun y(value: Double): Float {
        val domain = yDomain ?: return plotHeight / 2
        val span = domain.endInclusive - domain.start
        if (span == 0.0) return plotHeight / 2
        val fraction = (value - domain.start) / span
        return ((1.0 - fraction) * plotHeight).toFloat()
    }

    /**
     * Swift `ChartProxy.value(atX:)`: the date under a horizontal plot position (extrapolated outside the
     * plot). Feed the result to [MetricChartModel.scrubDate] to snap onto a sample.
     */
    fun dateAt(x: Float): Instant? {
        val start = startDate ?: return null
        if (spanNanos == 0L || plotWidth == 0f) return start
        val offset = (x.toDouble() / plotWidth * spanNanos).toLong()
        return start.plusNanos(offset)
    }

    /** Every drawn point's canvas position per series, in series order, for the line and point marks. */
    fun positions(): List<List<Pair<Float, Float>>> =
        chart.drawnSeries.map { s -> s.dataPoints.map { x(it.date) to y(it.value) } }

    /**
     * Swift `AxisMarks(values: .automatic(desiredCount: 4))` approximation: calendar-aligned ticks in
     * [zone] using the smallest step from a fixed ladder (1, 2, 3, 6, 12 h; 1, 2, 3, 7, 14 days; 1, 2,
     * 3, 6 months; years) that yields at most [desiredCount] ticks inside the date span. Labels use
     * [HistoryDateFormat.monthDay].
     */
    fun dateTicks(zone: ZoneId, desiredCount: Int = DESIRED_TICK_COUNT): List<Instant> {
        val start = startDate ?: return emptyList()
        val end = endDate ?: return emptyList()
        if (start == end) return listOf(start)
        for (step in TICK_LADDER) {
            val ticks = ticks(start.atZone(zone), end, step)
            if (ticks.size <= desiredCount) return ticks
        }
        return listOf(start)
    }

    private fun ticks(start: ZonedDateTime, end: Instant, step: TickStep): List<Instant> {
        val ticks = mutableListOf<Instant>()
        var tick = step.firstAtOrAfter(start)
        while (!tick.toInstant().isAfter(end)) {
            ticks += tick.toInstant()
            if (ticks.size > MAX_TICKS_PER_STEP) break
            tick = tick.plus(step.amount, step.unit)
        }
        return ticks
    }

    private class TickStep(val amount: Long, val unit: ChronoUnit) {
        fun firstAtOrAfter(start: ZonedDateTime): ZonedDateTime {
            val aligned = when (unit) {
                ChronoUnit.HOURS -> start.truncatedTo(ChronoUnit.HOURS)
                    .withHour((start.hour / amount.toInt()) * amount.toInt())
                ChronoUnit.DAYS -> start.truncatedTo(ChronoUnit.DAYS)
                ChronoUnit.MONTHS -> start.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1)
                    .withMonth(((start.monthValue - 1) / amount.toInt()) * amount.toInt() + 1)
                else -> start.truncatedTo(ChronoUnit.DAYS).withDayOfYear(1)
            }
            return if (aligned.isBefore(start)) aligned.plus(amount, unit) else aligned
        }
    }

    private companion object {
        const val DESIRED_TICK_COUNT = 4
        const val MAX_TICKS_PER_STEP = 64
        const val FLAT_PADDING_FRACTION = 0.1
        const val FLAT_PADDING_AT_ZERO = 1.0

        val TICK_LADDER: List<TickStep> = listOf(
            TickStep(1, ChronoUnit.HOURS), TickStep(2, ChronoUnit.HOURS), TickStep(3, ChronoUnit.HOURS),
            TickStep(6, ChronoUnit.HOURS), TickStep(12, ChronoUnit.HOURS),
            TickStep(1, ChronoUnit.DAYS), TickStep(2, ChronoUnit.DAYS), TickStep(3, ChronoUnit.DAYS),
            TickStep(7, ChronoUnit.DAYS), TickStep(14, ChronoUnit.DAYS),
            TickStep(1, ChronoUnit.MONTHS), TickStep(2, ChronoUnit.MONTHS), TickStep(3, ChronoUnit.MONTHS),
            TickStep(6, ChronoUnit.MONTHS),
            TickStep(1, ChronoUnit.YEARS), TickStep(2, ChronoUnit.YEARS), TickStep(5, ChronoUnit.YEARS),
            TickStep(10, ChronoUnit.YEARS),
        )

        fun dataDomain(chart: MetricChartModel): ClosedFloatingPointRange<Double>? {
            val values = chart.drawnSeries.flatMap { s -> s.dataPoints.map { it.value } }
            val low = values.minOrNull() ?: return null
            val high = values.maxOrNull() ?: return null
            if (low != high) return low..high
            val padding = if (low == 0.0) FLAT_PADDING_AT_ZERO else abs(low) * FLAT_PADDING_FRACTION
            return (low - padding)..(high + padding)
        }
    }
}
