// AndroidOnly: WP-316 Swift numeric parsing and C/Foundation number formatting for firmware-parity CLI output (oracle-checked).
package com.meshcoreone.android.feature.tools.diagnostics.text

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Numeric text rules that differ between Swift and the JVM: `Double(String)` accepts hex floats,
 * `inf`/`nan` spellings and rejects `1.0f`; `Int(String)` is ASCII-only; `String(format: "%.3f")`
 * rounds the exact binary value half-even and keeps a negative zero sign; Foundation's
 * `.number.precision(.fractionLength(n))` rounds the shortest decimal half-even.
 */
internal object SwiftNumbers {
    private val DECIMAL = Regex("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val HEXADECIMAL = Regex("([+-]?)0[xX]((?:[0-9a-fA-F]+\\.?[0-9a-fA-F]*|\\.[0-9a-fA-F]+))((?:[pP][+-]?[0-9]+)?)")
    private val INFINITY = Regex("[+-]?(?:inf|infinity)", RegexOption.IGNORE_CASE)
    private val NOT_A_NUMBER = Regex("[+-]?nan(?:\\([0-9A-Za-z_]*\\))?", RegexOption.IGNORE_CASE)
    private val INTEGER = Regex("[+-]?[0-9]+")
    private val INT64_MIN: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
    private val INT64_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

    /** Swift `Double(_ text:)`; overflow yields infinity and underflow zero, as strtod does. */
    fun parseDouble(text: String): Double? {
        if (DECIMAL.matches(text)) return text.toDouble()
        HEXADECIMAL.matchEntire(text)?.let { match ->
            val (sign, mantissa, exponent) = match.destructured
            return "${sign}0x$mantissa${exponent.ifEmpty { "p0" }}".toDouble()
        }
        if (INFINITY.matches(text)) return if (text.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        if (NOT_A_NUMBER.matches(text)) return Double.NaN
        return null
    }

    /** Swift `Int(_ text:)` on a 64-bit platform: optional sign, ASCII digits, nil on overflow. */
    fun parseInt(text: String): Long? {
        if (!INTEGER.matches(text)) return null
        val value = BigInteger(text.removePrefix("+"))
        return if (value < INT64_MIN || value > INT64_MAX) null else value.toLong()
    }

    /** Swift `UInt8(_ text:)`: accepts `+1` and `-0`, rejects any other negative or > 255. */
    fun parseUInt8(text: String): UByte? {
        if (!INTEGER.matches(text)) return null
        val value = BigInteger(text.removePrefix("+"))
        return if (value.signum() < 0 || value > BigInteger.valueOf(255)) null else value.toInt().toUByte()
    }

    /**
     * C `printf("%.<digits>f")` as used by Swift `String(format:)`: exact binary value rounded
     * half-even, `nan`/`inf` spellings, and `-0.000` for negative values that round to zero.
     */
    fun printfFixed(value: Double, digits: Int): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
        val magnitude = BigDecimal(value).abs().setScale(digits, RoundingMode.HALF_EVEN).toPlainString()
        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        return if (negative) "-$magnitude" else magnitude
    }

    /** Swift `(value).rounded()` (schoolbook, ties away from zero) on the exact binary value. */
    fun roundedHalfAwayFromZero(value: Double): BigDecimal =
        BigDecimal(value).setScale(0, RoundingMode.HALF_UP)

    /**
     * Foundation `value.formatted(.number.precision(.fractionLength(digits)))`: the shortest
     * decimal representation rounded half-even, locale grouping, and a retained minus sign when a
     * negative value rounds to zero.
     */
    fun foundationFixed(value: Double, digits: Int, locale: Locale): String {
        if (!value.isFinite()) return NumberFormat.getNumberInstance(locale).format(value)
        val rounded = BigDecimal(value.toString()).setScale(digits, RoundingMode.HALF_EVEN)
        val format = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_EVEN
        }
        val text = format.format(rounded.abs())
        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        return if (negative) format.decimalFormatSymbols.minusSign + text else text
    }

    /** Foundation `Text(value, format: .number)` for an integer: locale digits with grouping. */
    fun foundationInteger(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)
}
