// PortedFrom: MeshCore/Sources/MeshCore/Events/ChannelPayloads.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

import com.meshcoreone.android.core.protocol.bytes.Bytes

data class DefaultFloodScope(val name: String, val scopeKey: Bytes)
data class ChannelInfo(val index: UByte, val name: String, val secret: Bytes)
