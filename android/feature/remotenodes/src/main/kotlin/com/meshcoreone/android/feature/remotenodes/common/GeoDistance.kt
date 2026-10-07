// AndroidOnly: WP-313 Great-circle distance standing in for CLLocation.distance(from:) in remote-node resolution and SNR badges.
package com.meshcoreone.android.feature.remotenodes.common

import com.meshcoreone.android.core.model.Coordinate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haversine distance on the IUGG mean Earth radius. CoreLocation measures on the WGS-84 ellipsoid,
 * so values differ by up to ~0.5 %; they only rank resolver candidates and label SNR badges
 * (documented deviation in WP-313.md).
 */
object GeoDistance {
    private const val MEAN_EARTH_RADIUS_METERS = 6_371_008.8

    fun meters(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * MEAN_EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }
}
