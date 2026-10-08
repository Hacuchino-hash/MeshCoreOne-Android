// PortedFrom: MC1Services/Sources/MC1Services/RF/ObstructionPoint.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.diagnostics.rf

import java.util.UUID

/**
 * Point where obstruction affects the path.
 *
 * Equality matches the Swift custom `==`: it compares the three measurements with IEEE
 * semantics and ignores [id].
 */
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

    override fun hashCode(): Int = rfFieldsHash(
        rfDoubleHash(distanceFromAMeters),
        rfDoubleHash(obstructionHeightMeters),
        rfDoubleHash(fresnelClearancePercent),
    )

    override fun toString(): String =
        "ObstructionPoint(distanceFromAMeters=$distanceFromAMeters, " +
            "obstructionHeightMeters=$obstructionHeightMeters, fresnelClearancePercent=$fresnelClearancePercent)"
}
