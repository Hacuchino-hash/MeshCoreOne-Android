// AndroidOnly: WP-315 documented mirror of the three pure RFCalculator functions FresnelZoneRenderer calls (WP-212 owns RFCalculator).
package com.meshcoreone.android.feature.tools.los

import kotlin.math.sqrt

/**
 * Type-placement decision: `FresnelZoneRenderer.buildProfileSamples` (owned here) computes
 * Fresnel radii and earth bulge directly from `RFCalculator`, and its source tests pin those
 * values. Feature modules cannot reach the WP-212 `core:services` RFCalculator, so these three
 * expressions are mirrored with identical constants and operand order (bit-identical Doubles;
 * no log10, so no correctly-rounded-log concern). Everything else the view model needs from
 * RFCalculator goes through [LineOfSightPathAnalyzer] instead of being duplicated.
 */
internal object LosFresnelMath {
    private const val SPEED_OF_LIGHT: Double = 299_792_458.0
    private const val EARTH_RADIUS_KM: Double = 6371.0
    private const val HZ_PER_MHZ: Double = 1_000_000.0
    private const val METERS_PER_KM: Double = 1000.0

    /** Wavelength in meters; 0 for non-positive frequencies. */
    fun wavelength(frequencyMHz: Double): Double {
        if (!(frequencyMHz > 0)) return 0.0
        val frequencyHz = frequencyMHz * HZ_PER_MHZ
        return SPEED_OF_LIGHT / frequencyHz
    }

    /** First Fresnel zone radius r = sqrt((lambda * d1 * d2) / (d1 + d2)); 0 for non-positive inputs. */
    fun fresnelRadius(frequencyMHz: Double, distanceToAMeters: Double, distanceToBMeters: Double): Double {
        if (!(frequencyMHz > 0 && distanceToAMeters > 0 && distanceToBMeters > 0)) return 0.0
        val lambda = wavelength(frequencyMHz)
        val totalDistance = distanceToAMeters + distanceToBMeters
        return sqrt((lambda * distanceToAMeters * distanceToBMeters) / totalDistance)
    }

    /** Earth bulge h = (d1 * d2) / (2 * k * Re); 0 for non-positive inputs. */
    fun earthBulge(distanceToAMeters: Double, distanceToBMeters: Double, refractionK: Double): Double {
        if (!(distanceToAMeters > 0 && distanceToBMeters > 0 && refractionK > 0)) return 0.0
        val earthRadiusMeters = EARTH_RADIUS_KM * METERS_PER_KM
        val effectiveEarthRadius = refractionK * earthRadiusMeters
        return (distanceToAMeters * distanceToBMeters) / (2 * effectiveEarthRadius)
    }
}
