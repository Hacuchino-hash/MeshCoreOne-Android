// AndroidOnly: WP-314 Ellipsoidal (WGS-84) distance standing in for CLLocation.distance(from:) on the JVM.
package com.meshcoreone.android.feature.tools.trace

import com.meshcoreone.android.core.model.Coordinate
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Vincenty inverse distance on the WGS-84 ellipsoid, the same model `android.location.Location`
 * uses (20 iterations, last estimate kept for nearly antipodal points). It is plain math so it runs
 * in JVM tests. Apple does not document `CLLocation.distance(from:)`; the oracle vectors in
 * `docs/android/evidence/WP-314/` agree to well under 1%.
 */
object GeoDistance {
    private const val SEMI_MAJOR = 6_378_137.0
    private const val FLATTENING = 1 / 298.257223563
    private const val SEMI_MINOR = SEMI_MAJOR * (1 - FLATTENING)
    private const val MAX_ITERATIONS = 20
    private const val TOLERANCE = 1e-12

    fun meters(from: Coordinate, to: Coordinate): Double =
        meters(from.latitude, from.longitude, to.latitude, to.longitude)

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lambdaBase = Math.toRadians(lon2 - lon1)
        val u1 = atan((1 - FLATTENING) * tan(Math.toRadians(lat1)))
        val u2 = atan((1 - FLATTENING) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)
        var lambda = lambdaBase
        var iteration = Iteration.ZERO
        for (step in 0 until MAX_ITERATIONS) {
            iteration = iterate(lambda, sinU1, cosU1, sinU2, cosU2) ?: return 0.0
            val next = lambdaBase + (1 - iteration.c) * FLATTENING * iteration.sinAlpha *
                (iteration.sigma + iteration.c * iteration.sinSigma *
                    (iteration.cos2SigmaM + iteration.c * iteration.cosSigma *
                        (-1 + 2 * iteration.cos2SigmaM * iteration.cos2SigmaM)))
            val converged = abs(next - lambda) < TOLERANCE
            lambda = next
            if (converged) break
        }
        return ellipsoidDistance(iteration)
    }

    private data class Iteration(
        val sinSigma: Double,
        val cosSigma: Double,
        val sigma: Double,
        val sinAlpha: Double,
        val cosSqAlpha: Double,
        val cos2SigmaM: Double,
        val c: Double,
    ) {
        companion object {
            val ZERO = Iteration(0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
    }

    /** One Vincenty step; `null` for coincident points. */
    private fun iterate(lambda: Double, sinU1: Double, cosU1: Double, sinU2: Double, cosU2: Double): Iteration? {
        val sinLambda = sin(lambda)
        val cosLambda = cos(lambda)
        val crossA = cosU2 * sinLambda
        val crossB = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
        val sinSigma = sqrt(crossA * crossA + crossB * crossB)
        if (sinSigma == 0.0) return null
        val cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
        val sigma = atan2(sinSigma, cosSigma)
        val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
        val cosSqAlpha = 1 - sinAlpha * sinAlpha
        val cos2SigmaM = if (cosSqAlpha == 0.0) 0.0 else cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
        val c = FLATTENING / 16 * cosSqAlpha * (4 + FLATTENING * (4 - 3 * cosSqAlpha))
        return Iteration(sinSigma, cosSigma, sigma, sinAlpha, cosSqAlpha, cos2SigmaM, c)
    }

    private fun ellipsoidDistance(state: Iteration): Double {
        val uSquared = state.cosSqAlpha * (SEMI_MAJOR * SEMI_MAJOR - SEMI_MINOR * SEMI_MINOR) / (SEMI_MINOR * SEMI_MINOR)
        val a = 1 + uSquared / 16384 * (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 * (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val cos2 = state.cos2SigmaM
        val deltaSigma = b * state.sinSigma * (cos2 + b / 4 * (state.cosSigma * (-1 + 2 * cos2 * cos2) -
            b / 6 * cos2 * (-3 + 4 * state.sinSigma * state.sinSigma) * (-3 + 4 * cos2 * cos2)))
        return SEMI_MINOR * a * (state.sigma - deltaSigma)
    }
}
