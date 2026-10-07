// PortedFrom: MC1/Views/Map/MapLine.swift@db14559b39d32322b06477c6ae676112f583db50
// Feature-local mirror pending WP-312's core:maps MapLine (core:maps is still an empty shell).
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.core.model.Coordinate

/** Line rendering family (Swift `MapLine.LineStyle`). */
enum class MapLineStyle {
    LOS,
    TRACE_UNTRACED,
    TRACE_WEAK,
    TRACE_MEDIUM,
    TRACE_GOOD,
    MESSAGE_PATH,

    /** A faint dashed connector threading location reports in time order; not a proven route. */
    LOCATION_TRAIL,
    ;

    /** Host for the `forSNR` factory extension (Swift `MapLine.LineStyle.forSNR`). */
    companion object
}

/**
 * Engine-free polyline (Swift `MapLine`). Swift's mutable `pathIndex` is omitted: the WP-313 builders
 * never set it and Swift's `==` ignores it, so equality here (id, coordinates, style, opacity) matches.
 */
data class MapLine(
    val id: String,
    val coordinates: List<Coordinate>,
    val style: MapLineStyle,
    val opacity: Double,
)
