// PortedFrom: MC1Services/Sources/MC1Services/RF/ClearanceStatus.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.diagnostics.rf

/** Clearance status at worst point along path. Declaration order is severity order (best to worst). */
enum class ClearanceStatus(val rawValue: String) {
    CLEAR("Clear"),
    MARGINAL("Marginal"),
    PARTIAL_OBSTRUCTION("Partial obstruction"),
    BLOCKED("Blocked"),
    ;

    companion object {
        /** Mirrors Swift `ClearanceStatus(rawValue:)`; returns null for unknown raw values. */
        fun fromRawValue(rawValue: String): ClearanceStatus? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
