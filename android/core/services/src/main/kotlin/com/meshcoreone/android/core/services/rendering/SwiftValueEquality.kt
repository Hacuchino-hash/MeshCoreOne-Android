// AndroidOnly: WP-213 Swift Hashable semantics for rendering values carrying Double (signed zero equal, NaN unequal).
package com.meshcoreone.android.core.services.rendering

/**
 * Kotlin data classes compare `Double` with `Double.compare` (NaN == NaN, 0.0 != -0.0); Swift's
 * synthesized `Equatable` uses IEEE 754 (NaN != NaN, 0.0 == -0.0). Rendering values whose equality
 * drives re-render decisions use these helpers to keep Swift's answer.
 */
internal fun swiftFieldsEqual(left: Array<out Any?>, right: Array<out Any?>): Boolean =
    left.size == right.size && left.indices.all { swiftValueEqual(left[it], right[it]) }

internal fun swiftFieldsHash(values: Array<out Any?>): Int =
    values.fold(1) { hash, value -> 31 * hash + swiftValueHash(value) }

private fun swiftValueEqual(left: Any?, right: Any?): Boolean =
    if (left is Double && right is Double) ieeeEqual(left, right) else left == right

/** Statically typed `Double` operands, so `==` is the IEEE 754 comparison. */
private fun ieeeEqual(left: Double, right: Double): Boolean = left == right

private fun swiftValueHash(value: Any?): Int = when (value) {
    is Double -> if (ieeeEqual(value, 0.0)) 0.0.hashCode() else value.hashCode()
    else -> value?.hashCode() ?: 0
}
