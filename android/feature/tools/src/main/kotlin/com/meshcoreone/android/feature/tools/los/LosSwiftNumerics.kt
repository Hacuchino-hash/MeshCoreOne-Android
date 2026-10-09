// AndroidOnly: WP-315 Swift stdlib/Foundation numeric semantics (min/max, clamped, ICU fraction formatting, C printf) for bit-exact LOS parity.
package com.meshcoreone.android.feature.tools.los

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Swift `min(_:_:)`: `y < x ? y : x` (differs from [Math.min] for NaN and signed zero). */
internal fun swiftMin(x: Double, y: Double): Double = if (y < x) y else x

/** Swift `max(_:_:)`: `y >= x ? y : x` (differs from [Math.max] for NaN and signed zero). */
internal fun swiftMax(x: Double, y: Double): Double = if (y >= x) y else x

/** Swift `Comparable.clamped(to:)` as written in the source: `min(max(self, lower), upper)`. */
internal fun Double.swiftClamped(lower: Double, upper: Double): Double = swiftMin(swiftMax(this, lower), upper)

/** Swift `Optional<Double> ==`: both nil, or both present and IEEE-equal. */
internal fun optionalDoubleEquals(left: Double?, right: Double?): Boolean =
    if (left == null || right == null) left == null && right == null else left.toDouble() == right.toDouble()

/** Hash consistent with IEEE equality (`0.0 == -0.0`). */
internal fun ieeeHash(value: Double): Int = if (value == 0.0) 0 else value.hashCode()

/**
 * Foundation `Double.formatted(.number.precision(.fractionLength(n)))` for [locale].
 *
 * ICU rounds the shortest round-trip decimal of the value half-even (oracle: 0.35 -> "0.4",
 * 8.45 -> "8.4", 1.335 -> "1.34"), keeps the sign of values that round to zero ("-0.0") and
 * groups thousands. `Double.toString` is the shortest round-trip form on the JVM test runtime.
 */
internal fun formatFractionLength(value: Double, fractionDigits: Int, locale: Locale): String {
    val format = decimalFormat(fractionDigits, locale)
    val symbols = format.decimalFormatSymbols
    if (value.isNaN()) return symbols.naN
    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
    val body = if (value.isInfinite()) {
        symbols.infinity
    } else {
        format.format(BigDecimal(abs(value).toString()).setScale(fractionDigits, RoundingMode.HALF_EVEN))
    }
    return if (negative) "${symbols.minusSign}$body" else body
}

private fun decimalFormat(fractionDigits: Int, locale: Locale): DecimalFormat {
    val format = NumberFormat.getNumberInstance(locale) as? DecimalFormat
        ?: DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale))
    format.isGroupingUsed = true
    format.minimumFractionDigits = fractionDigits
    format.maximumFractionDigits = fractionDigits
    format.roundingMode = RoundingMode.HALF_EVEN
    return format
}

/**
 * C `String(format: "%.1f", value)` in the POSIX locale: the exact binary value rounded
 * half-even (oracle: 0.15 -> "0.1", 0.05 -> "0.1", 868.55 -> "868.5"), "inf"/"nan" spellings.
 */
internal fun printfOneDecimal(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
    val body = BigDecimal(abs(value)).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
    return if (negative) "-$body" else body
}
