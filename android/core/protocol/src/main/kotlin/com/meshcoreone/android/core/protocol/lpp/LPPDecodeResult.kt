// AndroidOnly: WP-105 Preserve partial LPP prefixes with explicit diagnostics and lossless unconsumed bytes.
package com.meshcoreone.android.core.protocol.lpp

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.Collections

sealed interface LPPDecodeDiagnostic {
    val offset: Int

    data class TruncatedHeader(override val offset: Int, val availableBytes: Int) : LPPDecodeDiagnostic {
        val expectedBytes: Int get() = 2
    }

    data class UnknownSensorType(
        override val offset: Int,
        val channel: UByte,
        val typeCode: UByte,
    ) : LPPDecodeDiagnostic

    data class TruncatedValue(
        override val offset: Int,
        val channel: UByte,
        val type: LPPSensorType,
        val availableBytes: Int,
    ) : LPPDecodeDiagnostic {
        val expectedBytes: Int get() = type.dataSize
    }
}

sealed class LPPDecodeResult private constructor(dataPoints: List<LPPDataPoint>, val consumedByteCount: Int) {
    val dataPoints: List<LPPDataPoint> = Collections.unmodifiableList(ArrayList(dataPoints))
    val isComplete: Boolean get() = this is Complete

    class Complete internal constructor(dataPoints: List<LPPDataPoint>, consumedByteCount: Int) :
        LPPDecodeResult(dataPoints, consumedByteCount)

    class Incomplete internal constructor(
        dataPoints: List<LPPDataPoint>,
        consumedByteCount: Int,
        val remainingData: Bytes,
        val diagnostic: LPPDecodeDiagnostic,
    ) : LPPDecodeResult(dataPoints, consumedByteCount)

    fun requireComplete(): List<LPPDataPoint> = when (this) {
        is Complete -> dataPoints
        is Incomplete -> throw LPPDecodingException(diagnostic)
    }
}

class LPPDecodingException(val diagnostic: LPPDecodeDiagnostic) :
    IllegalArgumentException("Incomplete LPP payload: $diagnostic")
