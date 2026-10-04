// AndroidOnly: WP-106 Swift scalar equality treats signed zero equally and NaN unequally.
package com.meshcoreone.android.core.protocol.event

internal fun eventFieldsEqual(left: Array<out Any?>, right: Array<out Any?>): Boolean =
    left.size == right.size && left.indices.all { index ->
        val a = left[index]
        val b = right[index]
        // Smart casts select numeric Double equality rather than boxed Any.equals.
        if (a is Double && b is Double) a == b else a == b
    }

internal fun eventFieldsHash(fields: Array<out Any?>): Int = fields.fold(1) { hash, field ->
    val value = if (field is Double && field == 0.0) 0.0.hashCode() else field?.hashCode() ?: 0
    hash * 31 + value
}
