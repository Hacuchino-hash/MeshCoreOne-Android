// PortedFrom: MeshCore/Sources/MeshCore/Session/SessionConfiguration.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.config

data class SessionConfiguration(
    val defaultTimeout: Double = 5.0,
    val clientIdentifier: String = "MeshCore-Swift",
    val binaryRequestOverallTimeout: Double = 40.0,
    val binaryRequestRetransmitInterval: Double? = 1.0,
    val contactStreamInactivityTimeout: Double = 15.0,
    val contactStreamHardTimeout: Double = 180.0,
    val channelPipelineWindow: Long = 8,
    val channelPipelineIdleTimeout: Double = 1.5,
    val channelPipelineHardTimeout: Double = 30.0,
    val channelPipelinePostDrainGrace: Double = 0.05,
) {
    companion object {
        const val BINARY_RETRANSMIT_RTT_HEADROOM = 2.0
        const val MILLISECONDS_PER_SECOND = 1000.0
        const val RETRY_ACK_TIMEOUT_MULTIPLIER = 1.2
        val DEFAULT = SessionConfiguration()
    }
}
