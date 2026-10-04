// AndroidOnly: WP-105 Typed, atomic LPP validation instead of Swift conversion/precondition traps.
package com.meshcoreone.android.core.protocol.lpp

import java.time.Instant

sealed class LPPEncodingException(message: String) : IllegalArgumentException(message) {
    class ValueTypeMismatch(
        val type: LPPSensorType,
        val expected: Class<out LPPValue>,
        val actual: LPPValue,
    ) : LPPEncodingException("${type.displayName} requires ${expected.simpleName}, not ${actual.javaClass.simpleName}")

    class NonFiniteValue(val type: LPPSensorType, val field: String, val value: Double) :
        LPPEncodingException("${type.displayName} $field must be finite: $value")

    class NumericOutOfRange(
        val type: LPPSensorType,
        val field: String,
        val value: Double,
        val scale: Double,
        val minimumRaw: Long,
        val maximumRaw: Long,
    ) : LPPEncodingException("${type.displayName} $field=$value exceeds raw range $minimumRaw..$maximumRaw at scale $scale")

    class IntegerOutOfRange(
        val type: LPPSensorType,
        val field: String,
        val value: Long,
        val minimum: Long,
        val maximum: Long,
    ) : LPPEncodingException("${type.displayName} $field=$value is outside $minimum..$maximum")

    class RawDataSizeMismatch(val type: LPPSensorType, val actual: Int) :
        LPPEncodingException("${type.displayName} needs ${type.dataSize} raw bytes, received $actual") {
        val expected: Int get() = type.dataSize
    }

    class SubsecondTimestamp(val value: Instant) :
        LPPEncodingException("LPP Unix time requires whole seconds: $value")
}
