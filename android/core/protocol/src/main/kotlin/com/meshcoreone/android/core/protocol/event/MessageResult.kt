// PortedFrom: MeshCore/Sources/MeshCore/Session/SessionConfiguration.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

sealed interface MessageResult {
    data class ContactMessage(val message: com.meshcoreone.android.core.protocol.event.ContactMessage) : MessageResult
    data class ChannelMessage(val message: com.meshcoreone.android.core.protocol.event.ChannelMessage) : MessageResult
    data class ChannelDatagram(val datagram: com.meshcoreone.android.core.protocol.event.ChannelDatagram) : MessageResult
    data object NoMoreMessages : MessageResult
}
