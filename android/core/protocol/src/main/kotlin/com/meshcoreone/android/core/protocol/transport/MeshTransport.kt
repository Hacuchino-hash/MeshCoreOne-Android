// PortedFrom: MeshCore/Sources/MeshCore/Transport/MeshTransport.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.transport

import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlinx.coroutines.flow.Flow

interface MeshTransport {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(data: Bytes)
    suspend fun sendWithoutResponse(data: Bytes) = send(data)
    suspend fun supportsWriteWithoutResponse(): Boolean = false
    suspend fun supportsPipelinedReads(): Boolean = supportsWriteWithoutResponse()
    suspend fun receivedData(): Flow<Bytes>
    suspend fun isConnected(): Boolean
}
