// AndroidOnly: WP-313 Approximation of Foundation Measurement .measurement(width: .abbreviated, usage: .road) for SNR badge distances.
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.feature.remotenodes.common.SwiftNumberFormat
import com.meshcoreone.android.feature.remotenodes.telemetry.MeasurementSystem
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/** A road-usage unit choice. */
enum class RoadUnit { METER, KILOMETER, FOOT, YARD, MILE }

/** A distance converted and rounded the way Foundation's road usage displays it. */
data class RoadMeasure(val value: Double, val unit: RoadUnit)

/**
 * Reproduces the unit ladder and rounding Foundation applies for `usage: .road`, derived from a
 * swiftc sweep (evidence oracle `map_road_usage.swift.txt`):
 * - metric: kilometres from 0.9 km, else metres; US: miles from 0.5 mi, else feet; UK: miles from
 *   0.5 mi, else yards (Foundation uses yards, not feet, for the UK);
 * - small units: whole numbers below 10, multiples of 10 below 300 m / 100 ft / 100 yd, multiples
 *   of 50 above;
 * - large units: two significant digits but never fewer than the integer digits ("0.95 km",
 *   "1.5 km", "16 km", "123 km");
 * - ties round half-even on the shortest decimal form of the value, as ICU does.
 *
 * Known gaps (documented in the WP-313 report): unit symbols come from a small table covering the
 * shipped app languages (no plural-dependent forms such as Ukrainian "ярди"/"ярдів"), and grouping
 * always applies, so "1235 km" in es/it/pl/pt-PT renders "1.235 km". Neither occurs at LoRa
 * neighbour distances.
 */
object RoadDistanceFormat {
    private const val METERS_PER_KILOMETER = 1_000.0
    private const val KILOMETER_FROM_KILOMETERS = 0.9
    private const val METERS_PER_MILE = 1_609.344
    private const val MILE_FROM_MILES = 0.5
    private const val METERS_PER_FOOT = 0.3048
    private const val METERS_PER_YARD = 0.9144
    private const val WHOLE_BELOW = 10.0
    private const val METRIC_COARSE_FROM = 300.0
    private const val IMPERIAL_COARSE_FROM = 100.0
    private const val FINE_INCREMENT = 10L
    private const val COARSE_INCREMENT = 50L
    private const val LARGE_SIGNIFICANT_DIGITS = 2
    private const val LARGE_INTEGER_FROM = 100.0

    fun format(meters: Double, locale: Locale, system: MeasurementSystem): String {
        val measure = measure(meters, system)
        val number = SwiftNumberFormat.number(measure.value, locale)
        return RoadUnitSymbols.forLocale(locale).compose(number, measure.unit)
    }

    fun measure(meters: Double, system: MeasurementSystem): RoadMeasure {
        if (!meters.isFinite()) return RoadMeasure(meters, RoadUnit.METER)
        return when (system) {
            MeasurementSystem.METRIC -> {
                val kilometers = convert(meters, METERS_PER_KILOMETER)
                if (kilometers >= KILOMETER_FROM_KILOMETERS) {
                    RoadMeasure(large(kilometers), RoadUnit.KILOMETER)
                } else {
                    RoadMeasure(small(meters, METRIC_COARSE_FROM), RoadUnit.METER)
                }
            }
            MeasurementSystem.US -> imperial(meters, METERS_PER_FOOT, RoadUnit.FOOT)
            MeasurementSystem.UK -> imperial(meters, METERS_PER_YARD, RoadUnit.YARD)
        }
    }

    private fun imperial(meters: Double, metersPerUnit: Double, smallUnit: RoadUnit): RoadMeasure {
        val miles = convert(meters, METERS_PER_MILE)
        return if (miles >= MILE_FROM_MILES) {
            RoadMeasure(large(miles), RoadUnit.MILE)
        } else {
            RoadMeasure(small(convert(meters, metersPerUnit), IMPERIAL_COARSE_FROM), smallUnit)
        }
    }

    /**
     * ICU converts by multiplying with the reciprocal factor, not dividing: 4.572 m becomes
     * 14.999999999999998 ft (so "10 ft"), where 4.572 / 0.3048 would give exactly 15 ("20 ft").
     */
    private fun convert(meters: Double, metersPerUnit: Double): Double = meters * (1.0 / metersPerUnit)

    private fun small(value: Double, coarseFrom: Double): Double {
        val increment = when {
            value < WHOLE_BELOW -> 1L
            value < coarseFrom -> FINE_INCREMENT
            else -> COARSE_INCREMENT
        }
        val step = BigDecimal.valueOf(increment)
        return shortestDecimal(value).divide(step).setScale(0, RoundingMode.HALF_EVEN).multiply(step).toDouble()
    }

    private fun large(value: Double): Double {
        val decimal = shortestDecimal(value)
        val rounded = if (value >= LARGE_INTEGER_FROM) {
            decimal.setScale(0, RoundingMode.HALF_EVEN)
        } else {
            decimal.round(MathContext(LARGE_SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN))
        }
        return rounded.toDouble()
    }

    /** `Double.toString` is the shortest round-tripping decimal, which ICU rounds. */
    private fun shortestDecimal(value: Double): BigDecimal = BigDecimal(value.toString())
}

/** Abbreviated unit symbols and the number/unit separator per app language (Foundation output). */
private data class RoadUnitSymbols(
    val separator: String,
    val meter: String = "m",
    val kilometer: String = "km",
    val foot: String = "ft",
    val yard: String = "yd",
    val mile: String = "mi",
) {
    fun compose(number: String, unit: RoadUnit): String {
        val symbol = when (unit) {
            RoadUnit.METER -> meter
            RoadUnit.KILOMETER -> kilometer
            RoadUnit.FOOT -> foot
            RoadUnit.YARD -> yard
            RoadUnit.MILE -> mile
        }
        return number + separator + symbol
    }

    companion object {
        private const val SPACE = " "
        private const val NARROW_NO_BREAK_SPACE = " "
        private val DEFAULT = RoadUnitSymbols(SPACE)
        private val BY_LANGUAGE = mapOf(
            "fr" to RoadUnitSymbols(NARROW_NO_BREAK_SPACE, foot = "pi"),
            "ko" to RoadUnitSymbols(""),
            "zh" to RoadUnitSymbols("", "米", "公里", "英尺", "码", "英里"),
            "ru" to RoadUnitSymbols(SPACE, "м", "км", "фт", "ярд.", "ми"),
            "uk" to RoadUnitSymbols(SPACE, "м", "км", "фт", "ярди", "милі"),
            "pl" to RoadUnitSymbols(SPACE, mile = "mili"),
        )

        fun forLocale(locale: Locale): RoadUnitSymbols = BY_LANGUAGE[locale.language] ?: DEFAULT
    }
}
