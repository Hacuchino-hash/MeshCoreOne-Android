// AndroidOnly: WP-217 Swift Date/UInt32 arithmetic used by the seed, over an injected java.time instant.
package com.meshcoreone.android.core.services.simulator

import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToLong

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val UINT32_LIMIT = 4_294_967_296L

/**
 * Swift `UInt32(date.timeIntervalSince1970)`: truncates toward zero and traps outside `0..<2^32`.
 * For the non-negative instants accepted here `epochSecond` (a floor) equals that truncation.
 */
internal fun swiftUInt32Seconds(instant: Instant): UInt {
    require(instant.epochSecond in 0 until UINT32_LIMIT) { "Instant $instant does not fit UInt32 seconds" }
    return instant.epochSecond.toUInt()
}

/** Swift `UInt32 - UInt32`, which traps on underflow instead of wrapping. */
internal fun UInt.swiftMinus(other: UInt): UInt {
    require(this >= other) { "UInt32 subtraction underflow: $this - $other" }
    return this - other
}

/**
 * Swift `date.addingTimeInterval(seconds)`. Every seed offset is a whole number of seconds, so the
 * nanosecond split is exact; fractional offsets round to the nearest nanosecond.
 */
internal fun Instant.addingInterval(seconds: Double): Instant {
    require(seconds.isFinite()) { "Time interval must be finite" }
    val whole = floor(seconds)
    val nanos = ((seconds - whole) * NANOS_PER_SECOND).roundToLong()
    return plusSeconds(whole.toLong()).plusNanos(nanos)
}

internal fun Instant.addingInterval(seconds: Long): Instant = plusSeconds(seconds)
