// AndroidOnly: WP-201 Swift Double/collection value equality (signed zero equal; NaN unequal).
package com.meshcoreone.android.core.model

internal fun sourceFieldsEqual(left: Array<out Any?>, right: Array<out Any?>): Boolean =
    left.size == right.size && left.indices.all { sourceValueEqual(left[it], right[it]) }

private fun sourceValueEqual(left: Any?, right: Any?): Boolean = when {
    left is Double && right is Double -> left == right
    left is Float && right is Float -> left == right
    left is List<*> && right is List<*> ->
        left.size == right.size && left.indices.all { sourceValueEqual(left[it], right[it]) }
    else -> left == right
}

internal fun sourceFieldsHash(values: Array<out Any?>): Int = values.fold(1) { hash, value -> 31 * hash + sourceValueHash(value) }

private fun sourceValueHash(value: Any?): Int = when (value) {
    is Double -> if (value == 0.0) 0.0.hashCode() else value.hashCode()
    is Float -> if (value == 0.0f) 0.0f.hashCode() else value.hashCode()
    is List<*> -> value.fold(1) { hash, element -> 31 * hash + sourceValueHash(element) }
    else -> value?.hashCode() ?: 0
}
