// AndroidOnly: WP-103 Retain source binary-list prefixes and expose malformed suffixes without silent success.
package com.meshcoreone.android.core.protocol.parser

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.EventList

sealed interface BinaryParseDiagnostic {
    val offset: Int

    data class TruncatedRecord(override val offset: Int, val expected: Int, val available: Int) : BinaryParseDiagnostic
    data class UnknownSensorType(override val offset: Int, val channel: UByte, val type: UByte) : BinaryParseDiagnostic
    data class NegativeResultsCount(override val offset: Int, val count: Short) : BinaryParseDiagnostic
}

class BinaryParseException(val diagnostic: BinaryParseDiagnostic) : Exception("Malformed binary response: $diagnostic")

sealed class BinaryListDecodeResult<out T>(
    entries: Collection<T>,
    val bytesConsumed: Int,
) {
    val entries: EventList<T> = EventList(entries)

    class Complete<T>(entries: Collection<T>, bytesConsumed: Int) : BinaryListDecodeResult<T>(entries, bytesConsumed)
    class Incomplete<T>(
        entries: Collection<T>,
        bytesConsumed: Int,
        val remainingData: Bytes,
        val diagnostic: BinaryParseDiagnostic,
    ) : BinaryListDecodeResult<T>(entries, bytesConsumed)

    fun requireComplete(): List<T> = when (this) {
        is Complete -> entries
        is Incomplete -> throw BinaryParseException(diagnostic)
    }

    internal fun sourcePrefix(): List<T> {
        if (this is Incomplete) parserLogger.warning("Binary response: $diagnostic; preserving the parsed source prefix")
        return entries
    }
}
