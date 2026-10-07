// AndroidOnly: WP-315 feature-owned consumer ports for elevation (WP-218), RF path analysis (WP-212) and repeater contacts.
package com.meshcoreone.android.feature.tools.los

import com.meshcoreone.android.core.contracts.domain.ContactPersisting
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.Coordinate
import com.meshcoreone.android.core.model.RadioId

/**
 * Elevation lookups the LOS state holder consumes (Swift `ElevationServiceProtocol` plus the
 * `ElevationService.optimalSampleCount`/`sampleCoordinates` statics). The WP-218
 * `core:services` ElevationService is adapted onto this port in app wiring; a failure is any
 * thrown non-cancellation exception, whose message becomes the error status text.
 */
interface LineOfSightElevationSource {
    suspend fun fetchElevation(coordinate: Coordinate): Double

    /** Samples along [path] with distances measured from the first coordinate. */
    suspend fun fetchElevations(path: List<Coordinate>): List<ElevationSample>

    fun optimalSampleCount(distanceMeters: Double): Int

    fun sampleCoordinates(from: Coordinate, to: Coordinate, sampleCount: Int): List<Coordinate>
}

/**
 * The RFCalculator entry points the view model calls (WP-212 owns the math). Implementations
 * must be pure; [LineOfSightStateHolder] may call the analyze functions off the main thread.
 */
interface LineOfSightPathAnalyzer {
    /** Great-circle distance in meters (Swift `RFCalculator.distance(from:to:)`). */
    fun distanceMeters(from: Coordinate, to: Coordinate): Double

    fun analyzePath(
        elevationProfile: List<ElevationSample>,
        pointAHeightMeters: Double,
        pointBHeightMeters: Double,
        frequencyMHz: Double,
        refractionK: Double,
    ): PathAnalysisResult

    fun analyzePathSegment(
        elevationProfile: List<ElevationSample>,
        startHeightMeters: Double,
        endHeightMeters: Double,
        frequencyMHz: Double,
        refractionK: Double,
    ): PathAnalysisResult
}

/** The one persistence read the view model performs (`fetchContacts(radioID:)`). */
fun interface LineOfSightContactSource {
    suspend fun fetchContacts(radioId: RadioId): List<ContactDTO>
}

/** Adapts the shared contacts persistence contract onto [LineOfSightContactSource]. */
fun ContactPersisting.asLineOfSightContactSource(): LineOfSightContactSource =
    LineOfSightContactSource { radioId -> fetchContacts(radioId) }

/** Non-fatal failures the source only logged (repeater load and elevation fetch errors). */
fun interface LineOfSightFailureReporter {
    fun report(operation: String, error: Throwable)

    companion object {
        val NONE: LineOfSightFailureReporter = LineOfSightFailureReporter { _, _ -> }
    }
}
