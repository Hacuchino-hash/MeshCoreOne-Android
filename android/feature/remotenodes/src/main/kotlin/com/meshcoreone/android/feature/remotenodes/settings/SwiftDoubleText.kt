// AndroidOnly: WP-313 Swift Double string interpolation ("\(value)") for CLI command arguments.
package com.meshcoreone.android.feature.remotenodes.settings

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Swift's `Double.description`, which `"set lat \(latitude)"` uses: the shortest round-tripping digits,
 * plain notation with at least one fraction digit ("45.0", "0.0001") for decimal exponents -4 through
 * 15, otherwise exponential with a signed two-digit-minimum exponent ("1e-05", "1.5e+16"). Kotlin's
 * `toString` switches to "1.0E-4" style at different thresholds. Verified with swiftc (evidence
 * oracle `settings_text.swift.txt`).
 */
internal object SwiftDoubleText {
    private const val MIN_PLAIN_EXPONENT = -4
    private const val MAX_PLAIN_EXPONENT = 15

    fun describe(value: Double): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
        val sign = if (value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)) "-" else ""
        if (value == 0.0) return "${sign}0.0"
        val shortest = shortestDigits(Math.abs(value))
        val exponent = shortest.precision() - shortest.scale() - 1
        if (exponent in MIN_PLAIN_EXPONENT..MAX_PLAIN_EXPONENT) {
            val plain = shortest.toPlainString()
            return sign + if (plain.contains('.')) plain else "$plain.0"
        }
        val digits = shortest.unscaledValue().toString()
        val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
        val exponentSign = if (exponent < 0) "-" else "+"
        val exponentDigits = Math.abs(exponent).toString().padStart(2, '0')
        return "$sign${mantissa}e$exponentSign$exponentDigits"
    }

    /**
     * `Double.toString` keeps two significant digits even when one round-trips (4.9E-324 for the
     * smallest subnormal, which Swift prints as 5e-324), so trim digits while the value still round-trips.
     */
    private fun shortestDigits(magnitude: Double): BigDecimal {
        var digits = BigDecimal(magnitude.toString()).stripTrailingZeros()
        while (digits.precision() > 1) {
            val shorter = digits.round(MathContext(digits.precision() - 1, RoundingMode.HALF_EVEN)).stripTrailingZeros()
            if (shorter.toDouble() != magnitude) break
            digits = shorter
        }
        return digits
    }
}
