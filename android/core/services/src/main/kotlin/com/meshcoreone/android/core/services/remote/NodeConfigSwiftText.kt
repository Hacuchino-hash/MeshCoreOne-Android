// AndroidOnly: WP-210 Swift Foundation text semantics (Double description/parsing, Character.isHexDigit, grapheme counts) the node config port must reproduce for byte-identical exports.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.truncate

/**
 * Reproduces the Swift standard-library text behaviors the iOS node config code depends on, so an
 * Android export is byte-identical to an iOS export and an import accepts exactly what iOS accepts.
 * The JVM's `Double.toString`/`toDoubleOrNull` differ from Swift in exponent thresholds, whitespace
 * and type-suffix handling, so they are never used directly for config text.
 */
internal object NodeConfigSwiftText {
    private val grapheme: Pattern = Pattern.compile("\\X")
    private val decimalLiteral = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
    private val hexLiteral = Regex("([+-]?)0[xX]([0-9a-fA-F]+\\.?[0-9a-fA-F]*|\\.[0-9a-fA-F]+)([pP][+-]?\\d+)?")
    private val infinityLiteral = Regex("([+-]?)(inf|infinity)", RegexOption.IGNORE_CASE)
    private val nanLiteral = Regex("[+-]?nan(\\([0-9A-Za-z_]*\\))?", RegexOption.IGNORE_CASE)
    private const val MAX_SIGNIFICANT_DIGITS = 17
    private const val EXPONENTIAL_LOWER_EXPONENT = -3
    private val exponentialUpperMagnitude = Math.scalb(1.0, 53)

    /** Swift `Character.count`-style length: extended grapheme clusters, not UTF-16 units. */
    fun graphemeCount(text: String): Int {
        val matcher = grapheme.matcher(text)
        var count = 0
        while (matcher.find()) count++
        return count
    }

    /**
     * The bytes of a contiguous, even-length ASCII hex string, or null. Equivalent to Swift's
     * `allSatisfy(\.isHexDigit)` guard followed by `Data(hexString:)`: fullwidth digits pass Swift's
     * `isHexDigit` but then fail `UInt8(_:radix:)`, so both paths reject every non-ASCII digit.
     */
    fun strictHexBytes(text: String): Bytes? {
        if (text.length % 2 != 0) return null
        if (!text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return Bytes.parseHex(text)
    }

    /** Swift `Double(String)`: no surrounding whitespace, no type suffixes, hex floats allowed. */
    fun parseDouble(text: String): Double? {
        if (text.isEmpty()) return null
        if (decimalLiteral.matches(text)) return text.toDouble()
        hexLiteral.matchEntire(text)?.let { match ->
            val exponent = match.groupValues[3].ifEmpty { "p0" }
            return "${match.groupValues[1]}0x${match.groupValues[2]}$exponent".toDouble()
        }
        infinityLiteral.matchEntire(text)?.let { match ->
            return if (match.groupValues[1] == "-") Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        }
        if (nanLiteral.matches(text)) return Double.NaN
        return null
    }

    /** Swift `String(describing: Double)`: shortest round-trip digits, Swift's exponent thresholds. */
    fun describe(value: Double): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
        if (value == 0.0) return if (1.0 / value < 0) "-0.0" else "0.0"
        val sign = if (value < 0) "-" else ""
        val (digits, exponent) = shortestDigits(abs(value))
        // SwiftDtoa: exponential when 0.DIGITS x 10^e has e < -3 (0.0001 stays decimal, 0.00001 is
        // "1e-05") or the magnitude exceeds 2^53; decimal otherwise.
        val body = if (exponent < EXPONENTIAL_LOWER_EXPONENT || abs(value) > exponentialUpperMagnitude) {
            exponential(digits, exponent)
        } else {
            decimal(digits, exponent)
        }
        return sign + body
    }

    /** Swift `rounded()` (schoolbook: half away from zero). */
    fun roundedHalfAwayFromZero(value: Double): Double {
        if (!value.isFinite()) return value
        val whole = truncate(value)
        return if (abs(value - whole) >= 0.5) whole + sign(value) else whole
    }

    /**
     * Swift `UInt32(Double)`, except that Swift traps on NaN/out-of-range input; Android saturates
     * instead (NaN maps to 0) because a crash on a malformed device value is never the desired outcome.
     */
    fun saturatingUInt32(value: Double): UInt = when {
        value.isNaN() -> 0u
        value <= 0.0 -> 0u
        value >= UInt.MAX_VALUE.toDouble() -> UInt.MAX_VALUE
        else -> truncate(value).toLong().toUInt()
    }

    /** `Int(date.timeIntervalSince1970)`: whole seconds truncated toward zero, not floored. */
    fun truncatedEpochSeconds(instant: Instant): Long =
        if (instant.epochSecond < 0 && instant.nano > 0) instant.epochSecond + 1 else instant.epochSecond

    /** Digits without leading/trailing zeros and the exponent `e` such that value = 0.DIGITS x 10^e. */
    private fun shortestDigits(magnitude: Double): Pair<String, Int> {
        val exact = BigDecimal(magnitude)
        for (precision in 1..MAX_SIGNIFICANT_DIGITS) {
            val nearest = exact.round(MathContext(precision, RoundingMode.HALF_EVEN))
            val candidates = listOf(
                nearest,
                exact.round(MathContext(precision, RoundingMode.FLOOR)),
                exact.round(MathContext(precision, RoundingMode.CEILING)),
            ).filter { it.toDouble() == magnitude }
            val best = candidates.minByOrNull { it.subtract(exact).abs() } ?: continue
            val unscaled = best.stripTrailingZeros()
            val digits = unscaled.unscaledValue().toString()
            return digits to (digits.length - unscaled.scale())
        }
        val fallback = exact.round(MathContext(MAX_SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val digits = fallback.unscaledValue().toString()
        return digits to (digits.length - fallback.scale())
    }

    private fun exponential(digits: String, exponent: Int): String {
        val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
        val power = exponent - 1
        val magnitude = abs(power).toString().padStart(2, '0')
        return mantissa + "e" + (if (power < 0) "-" else "+") + magnitude
    }

    private fun decimal(digits: String, exponent: Int): String = when {
        exponent <= 0 -> "0." + "0".repeat(-exponent) + digits
        exponent >= digits.length -> digits + "0".repeat(exponent - digits.length) + ".0"
        else -> digits.substring(0, exponent) + "." + digits.substring(exponent)
    }
}
