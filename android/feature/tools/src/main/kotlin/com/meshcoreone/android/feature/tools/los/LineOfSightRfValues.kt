// AndroidOnly: WP-315 feature-local LOS value shapes mirroring the WP-212 RF results (feature modules may not depend on core:services).
package com.meshcoreone.android.feature.tools.los

import com.meshcoreone.android.core.model.Coordinate
import java.util.UUID

/*
 * Type-placement decision: the Swift view model consumes MC1Services/RF value types whose
 * primary port is WP-212 (`core:services/.../diagnostics/rf`). The module contract forbids
 * feature -> core:services edges, so the LOS logic layer owns these minimal mirrors of the
 * fields and derived values it actually reads. App wiring maps WP-212 results onto them.
 * Equality follows Swift IEEE `==` (signed zeros equal, NaN unequal); generated ids are ignored.
 */

/** Elevation sample along the path (Swift `ElevationSample`; Identifiable, no value equality). */
class ElevationSample(
    val coordinate: Coordinate,
    /** Meters above sea level. */
    val elevation: Double,
    val distanceFromAMeters: Double,
) {
    val id: UUID = UUID.randomUUID()

    override fun toString(): String =
        "ElevationSample(coordinate=$coordinate, elevation=$elevation, distanceFromAMeters=$distanceFromAMeters)"
}

/** Clearance status at the worst point along a path. Declaration order is severity order. */
enum class ClearanceStatus(val rawValue: String) {
    CLEAR("Clear"),
    MARGINAL("Marginal"),
    PARTIAL_OBSTRUCTION("Partial obstruction"),
    BLOCKED("Blocked"),
}

/** Point where an obstruction affects the path; equality ignores [id]. */
class ObstructionPoint(
    val distanceFromAMeters: Double,
    val obstructionHeightMeters: Double,
    val fresnelClearancePercent: Double,
) {
    val id: UUID = UUID.randomUUID()

    override fun equals(other: Any?): Boolean =
        other is ObstructionPoint &&
            distanceFromAMeters == other.distanceFromAMeters &&
            obstructionHeightMeters == other.obstructionHeightMeters &&
            fresnelClearancePercent == other.fresnelClearancePercent

    override fun hashCode(): Int =
        listOf(ieeeHash(distanceFromAMeters), ieeeHash(obstructionHeightMeters), ieeeHash(fresnelClearancePercent)).hashCode()

    override fun toString(): String =
        "ObstructionPoint(distanceFromAMeters=$distanceFromAMeters, obstructionHeightMeters=$obstructionHeightMeters, " +
            "fresnelClearancePercent=$fresnelClearancePercent)"
}

/** Complete analysis result for a path (modelled terrain clearance, not measured reception). */
class PathAnalysisResult(
    val distanceMeters: Double,
    val freeSpacePathLoss: Double,
    /** Peak diffraction loss from the single worst knife-edge obstruction (not cumulative). */
    val peakDiffractionLoss: Double,
    val totalPathLoss: Double,
    val clearanceStatus: ClearanceStatus,
    val worstClearancePercent: Double,
    val obstructionPoints: List<ObstructionPoint>,
    val frequencyMHz: Double,
    val refractionK: Double,
) {
    val distanceKm: Double get() = distanceMeters / 1000

    /** First point with the lowest clearance (Swift `min(by:)` keeps the earliest of ties). */
    val worstObstructionPoint: ObstructionPoint?
        get() {
            var worst = obstructionPoints.firstOrNull() ?: return null
            for (point in obstructionPoints) {
                if (point.fresnelClearancePercent < worst.fresnelClearancePercent) worst = point
            }
            return worst
        }

    override fun equals(other: Any?): Boolean =
        other is PathAnalysisResult &&
            distanceMeters == other.distanceMeters &&
            freeSpacePathLoss == other.freeSpacePathLoss &&
            peakDiffractionLoss == other.peakDiffractionLoss &&
            totalPathLoss == other.totalPathLoss &&
            clearanceStatus == other.clearanceStatus &&
            worstClearancePercent == other.worstClearancePercent &&
            obstructionPoints == other.obstructionPoints &&
            frequencyMHz == other.frequencyMHz &&
            refractionK == other.refractionK

    override fun hashCode(): Int = listOf(
        ieeeHash(distanceMeters), ieeeHash(freeSpacePathLoss), ieeeHash(peakDiffractionLoss), ieeeHash(totalPathLoss),
        clearanceStatus, ieeeHash(worstClearancePercent), obstructionPoints, ieeeHash(frequencyMHz), ieeeHash(refractionK),
    ).hashCode()

    override fun toString(): String =
        "PathAnalysisResult(distanceMeters=$distanceMeters, clearanceStatus=$clearanceStatus, " +
            "worstClearancePercent=$worstClearancePercent, obstructions=${obstructionPoints.size})"
}

/** Result for a single relay segment (A to R, or R to B). */
class SegmentAnalysisResult(
    val startLabel: String,
    val endLabel: String,
    val clearanceStatus: ClearanceStatus,
    val distanceMeters: Double,
    val worstClearancePercent: Double,
) {
    val distanceKm: Double get() = distanceMeters / 1000

    override fun equals(other: Any?): Boolean =
        other is SegmentAnalysisResult &&
            startLabel == other.startLabel &&
            endLabel == other.endLabel &&
            clearanceStatus == other.clearanceStatus &&
            distanceMeters == other.distanceMeters &&
            worstClearancePercent == other.worstClearancePercent

    override fun hashCode(): Int =
        listOf(startLabel, endLabel, clearanceStatus, ieeeHash(distanceMeters), ieeeHash(worstClearancePercent)).hashCode()

    override fun toString(): String =
        "SegmentAnalysisResult($startLabel->$endLabel, $clearanceStatus, distanceMeters=$distanceMeters, " +
            "worstClearancePercent=$worstClearancePercent)"
}

/** Combined result when analyzing a path via a repeater. */
data class RelayPathAnalysisResult(
    val segmentAR: SegmentAnalysisResult,
    val segmentRB: SegmentAnalysisResult,
) {
    val totalDistanceMeters: Double get() = segmentAR.distanceMeters + segmentRB.distanceMeters
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000

    /** Overall status is the worst of the two segments. */
    val overallStatus: ClearanceStatus
        get() = ClearanceStatus.entries[maxOf(segmentAR.clearanceStatus.ordinal, segmentRB.clearanceStatus.ordinal)]
}
