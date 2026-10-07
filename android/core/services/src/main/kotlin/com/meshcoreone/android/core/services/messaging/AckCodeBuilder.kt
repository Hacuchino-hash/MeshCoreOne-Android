// PortedFrom: MC1Services/Sources/MC1Services/Services/AckCodeBuilder.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.sha256

object AckCodeBuilder {
    fun expectedAck(timestamp: UInt, attempt: UByte, text: String, senderPublicKey: Bytes): Bytes {
        require(attempt < 5u) { "Attempt exceeds the four-direct-plus-one-flood index range" }
        val input = ByteWriter().appendUInt32LE(timestamp)
            .appendUInt8((attempt.toInt() and 3).toUByte())
            .append(Bytes.utf8(text)).append(senderPublicKey).toBytes()
        return sha256(input).prefix(4)
    }
}
