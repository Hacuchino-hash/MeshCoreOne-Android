// AndroidOnly: WP-313 Foundation FormatStyle number rendering (.number, .precision(.fractionLength)) reproduced for remote-node displays.
package com.meshcoreone.android.feature.remotenodes.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Swift `Double.formatted(.number...)` behavior, verified against Foundation with swiftc (evidence
 * oracle `wp313_numbers.swift.txt`):
 * - rounding is half-even applied to the shortest decimal representation of the double, so 2.675
 *   with two digits is "2.68" (Java's exact-binary rounding would give "2.67") and 3.85 with one
 *   digit is "3.8";
 * - grouping separators are on ("1,234.5");
 * - a negative value that rounds to zero keeps its sign ("-0", "-0.0"), as does `-0.0`;
 * - `.number` without a precision shows at most six fraction digits and drops trailing zeros.
 */
object SwiftNumberFormat {
    private const val DEFAULT_MAX_FRACTION_DIGITS = 6

    /** `value.formatted(.number.precision(.fractionLength(digits)))`. */
    fun fixed(value: Double, fractionDigits: Int, locale: Locale): String =
        format(value, fractionDigits, fractionDigits, locale)

    /** `value.formatted()` / `.formatted(.number)` for a `Double`. */
    fun number(value: Double, locale: Locale): String = format(value, 0, DEFAULT_MAX_FRACTION_DIGITS, locale)

    /** `value.formatted()` for a Swift integer. */
    fun integer(value: Long, locale: Locale): String = grouping(locale, 0, 0).format(value)

    private fun format(value: Double, minimumDigits: Int, maximumDigits: Int, locale: Locale): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        if (value.isNaN()) return symbols.naN
        val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
        if (value.isInfinite()) return (if (negative) symbols.minusSign.toString() else "") + symbols.infinity
        // Double.toString yields the shortest round-tripping decimal, which Foundation (ICU) rounds.
        val shortest = BigDecimal(value.toString()).abs()
        val rounded = shortest.setScale(maximumDigits, RoundingMode.HALF_EVEN)
        val body = grouping(locale, minimumDigits, maximumDigits).format(rounded)
        return if (negative) symbols.minusSign + body else body
    }

    private fun grouping(locale: Locale, minimumDigits: Int, maximumDigits: Int): DecimalFormat =
        (DecimalFormat.getInstance(locale) as DecimalFormat).apply {
            isGroupingUsed = true
            minimumFractionDigits = minimumDigits
            maximumFractionDigits = maximumDigits
            roundingMode = RoundingMode.HALF_EVEN
        }
}
