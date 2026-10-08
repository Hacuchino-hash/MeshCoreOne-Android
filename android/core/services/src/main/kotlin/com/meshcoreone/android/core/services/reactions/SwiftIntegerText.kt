// AndroidOnly: WP-216 Swift FixedWidthInteger text parsing (ASCII digits, optional sign) that the reaction parsers rely on.
package com.meshcoreone.android.core.services.reactions

import java.math.BigInteger

/**
 * Swift `FixedWidthInteger.init?(_ text:radix:)` for the integer widths the reaction parsers use.
 *
 * Swift accepts one optional leading `+` or `-` followed by at least one ASCII digit of the radix and
 * nothing else; the value must fit the target type, so an unsigned `-0` parses as 0 while `-1` fails.
 * Unlike Kotlin's `toLongOrNull`, non-ASCII decimal digits (for example `٣` or fullwidth `１`) are
 * rejected. Values verified against the Swift 6.3.2 runtime.
 */
internal object SwiftIntegerText {
    private const val DECIMAL = 10
    private const val LETTER_DIGIT_OFFSET = 10

    /** Longest magnitude worth accumulating: anything wider than 65 bits is out of every supported range. */
    private const val MAX_MAGNITUDE_BITS = 65

    private val UINT64_MAX: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
    private val INT64_MIN: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
    private val INT64_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

    /** `UInt64(text)`. */
    fun uint64(text: String): ULong? =
        value(text, DECIMAL)?.takeIf { it.signum() >= 0 && it <= UINT64_MAX }?.let { it.toLong().toULong() }

    /** `UInt32(text)`. */
    fun uint32(text: String): UInt? = uint64(text)?.takeIf { it <= UInt.MAX_VALUE.toULong() }?.toUInt()

    /** `UInt8(text, radix: radix)`. */
    fun uint8(text: String, radix: Int): UByte? =
        value(text, radix)?.takeIf { it.signum() >= 0 && it <= BigInteger.valueOf(UByte.MAX_VALUE.toLong()) }
            ?.toInt()?.toUByte()

    /** `Int(text)` (Swift `Int` is 64-bit on every supported platform). */
    fun int64(text: String): Long? = value(text, DECIMAL)?.takeIf { it in INT64_MIN..INT64_MAX }?.toLong()

    private fun value(text: String, radix: Int): BigInteger? {
        val negative = text.startsWith('-')
        val start = if (negative || text.startsWith('+')) 1 else 0
        if (start >= text.length) return null
        var magnitude = BigInteger.ZERO
        val bigRadix = BigInteger.valueOf(radix.toLong())
        for (index in start until text.length) {
            val digit = asciiDigit(text[index], radix) ?: return null
            magnitude = magnitude.multiply(bigRadix).add(BigInteger.valueOf(digit.toLong()))
            if (magnitude.bitLength() > MAX_MAGNITUDE_BITS) return null
        }
        return if (negative) magnitude.negate() else magnitude
    }

    private fun asciiDigit(character: Char, radix: Int): Int? {
        val digit = when (character) {
            in '0'..'9' -> character - '0'
            in 'a'..'z' -> character - 'a' + LETTER_DIGIT_OFFSET
            in 'A'..'Z' -> character - 'A' + LETTER_DIGIT_OFFSET
            else -> return null
        }
        return digit.takeIf { it < radix }
    }
}
