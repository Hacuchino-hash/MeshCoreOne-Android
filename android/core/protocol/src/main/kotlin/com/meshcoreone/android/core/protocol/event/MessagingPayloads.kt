// PortedFrom: MeshCore/Sources/MeshCore/Events/MessagingPayloads.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.event

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant

data class MessageSentInfo(val route: UByte, val expectedAck: Bytes, val suggestedTimeoutMs: UInt)

data class ContactMessage(
    val senderPublicKeyPrefix: Bytes,
    val pathLength: UByte,
    val textType: UByte,
    val senderTimestamp: Instant,
    val signature: Bytes?,
    val text: String,
    val snr: Double?,
) {
    private val fields get() = arrayOf(senderPublicKeyPrefix, pathLength, textType, senderTimestamp, signature, text, snr)
    override fun equals(other: Any?): Boolean = other is ContactMessage && eventFieldsEqual(fields, other.fields)
    override fun hashCode(): Int = eventFieldsHash(fields)
}

data class ChannelMessage(
    val channelIndex: UByte,
    val pathLength: UByte,
    val textType: UByte,
    val senderTimestamp: Instant,
    val text: String,
    val snr: Double?,
) {
    private val fields get() = arrayOf(channelIndex, pathLength, textType, senderTimestamp, text, snr)
    override fun equals(other: Any?): Boolean = other is ChannelMessage && eventFieldsEqual(fields, other.fields)
    override fun hashCode(): Int = eventFieldsHash(fields)
}

data class ChannelDatagram(
    val channelIndex: UByte,
    val pathLength: UByte,
    val dataType: UShort,
    val data: Bytes,
    val snr: Double,
) {
    private val fields get() = arrayOf(channelIndex, pathLength, dataType, data, snr)
    override fun equals(other: Any?): Boolean = other is ChannelDatagram && eventFieldsEqual(fields, other.fields)
    override fun hashCode(): Int = eventFieldsHash(fields)
}
