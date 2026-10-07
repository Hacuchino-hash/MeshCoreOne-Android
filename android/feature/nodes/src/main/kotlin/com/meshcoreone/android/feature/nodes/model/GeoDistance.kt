// AndroidOnly: WP-311 Great-circle distance standing in for CLLocation.distance(from:) in node sorting and row labels.
package com.meshcoreone.android.feature.nodes.model

import com.meshcoreone.android.core.model.Coordinate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haversine distance in meters on a sphere of the mean Earth radius. CoreLocation measures on the
 * WGS-84 ellipsoid; the two differ by well under 0.5%, so orderings only change for near-equal
 * distances (WP-311 deviations).
 */
object GeoDistance {
    private const val MEAN_EARTH_RADIUS_METERS = 6_371_008.8

    fun meters(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * MEAN_EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }
}
