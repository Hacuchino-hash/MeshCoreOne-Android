// AndroidOnly: WP-312 camera restoration equivalent to MapCameraStore.swift.
package com.meshcoreone.android.core.maps

data class MapCamera(
    val center: GeoPoint,
    val latitudeSpan: Double,
    val longitudeSpan: Double,
) {
    init {
        require(latitudeSpan.isFinite() && longitudeSpan.isFinite())
        require(latitudeSpan > 0.0 && longitudeSpan > 0.0)
    }

    fun encode(): String = "${center.latitude},${center.longitude},$latitudeSpan,$longitudeSpan"

    companion object {
        fun decode(value: String): MapCamera? {
            val parts = value.split(',')
            if (parts.size != 4) return null
            val values = parts.map { it.toDoubleOrNull() ?: return null }
            return runCatching {
                MapCamera(GeoPoint(values[0], values[1]), values[2], values[3])
            }.getOrNull()
        }
    }
}
