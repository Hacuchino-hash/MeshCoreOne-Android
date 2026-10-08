// PortedFrom: MC1Services/Sources/MC1Services/RF/SegmentAnalysisResult.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.diagnostics.rf

/** Result for a single path segment (A to R, or R to B). Equality uses Swift IEEE Double semantics. */
data class SegmentAnalysisResult(
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

    override fun hashCode(): Int = rfFieldsHash(
        startLabel.hashCode(),
        endLabel.hashCode(),
        clearanceStatus.hashCode(),
        rfDoubleHash(distanceMeters),
        rfDoubleHash(worstClearancePercent),
    )
}
