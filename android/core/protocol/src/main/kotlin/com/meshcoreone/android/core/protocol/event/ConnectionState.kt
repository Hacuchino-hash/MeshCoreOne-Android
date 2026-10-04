// PortedFrom: MeshCore/Sources/MeshCore/Events/ConnectionState.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Long) : ConnectionState
    data class Failed(val error: MeshTransportError) : ConnectionState
}
