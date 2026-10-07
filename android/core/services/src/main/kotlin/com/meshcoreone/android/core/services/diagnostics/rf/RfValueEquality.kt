// AndroidOnly: WP-212 Swift synthesized Double Equatable semantics (signed zero equal; NaN unequal) for RF value types.
package com.meshcoreone.android.core.services.diagnostics.rf

/**
 * Hash for a Double that stays consistent with IEEE `==` equality: `0.0` and `-0.0`
 * compare equal under Swift `==`, so they must hash identically here.
 */
internal fun rfDoubleHash(value: Double): Int = if (value == 0.0) 0.0.hashCode() else value.hashCode()

/** Combines field hashes in declaration order. */
internal fun rfFieldsHash(vararg hashes: Int): Int = hashes.fold(1) { hash, value -> 31 * hash + value }

/** Element-wise Swift `==` over two lists of RF values (each element's `equals` already uses IEEE semantics). */
internal fun <T> rfListsEqual(left: List<T>, right: List<T>): Boolean =
    left.size == right.size && left.indices.all { left[it] == right[it] }
