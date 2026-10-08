// PortedFrom: MC1Services/Sources/MC1Services/Services/LoginTimeoutConfig.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.model.decodePathLen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Configuration for login timeout based on path length. */
object LoginTimeoutConfig {
    /** Base timeout for direct (0-hop) connections. */
    val directTimeout: Duration = 5.seconds

    /** Additional timeout per hop in the path. */
    val perHopTimeout: Duration = 10.seconds

    /** Maximum timeout regardless of path length. */
    val maximumTimeout: Duration = 60.seconds

    /** Calculate the appropriate timeout based on the encoded path length byte. */
    fun timeout(pathLength: UByte): Duration {
        // An undecodable byte (mode 3, notably the 0xFF flood sentinel) means no known path: the
        // login floods both ways, so budget for the worst case rather than pricing it as zero-hop.
        val decoded = decodePathLen(pathLength) ?: return maximumTimeout
        val total = directTimeout + perHopTimeout * decoded.hopCount
        return minOf(total, maximumTimeout)
    }
}
