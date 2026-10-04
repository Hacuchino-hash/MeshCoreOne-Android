// PortedFrom: MC1Services/Sources/MC1Services/Extensions/Data+Extensions.swift@db14559b39d32322b06477c6ae676112f583db50
// Test-only GPL application-format adapters; not packaged in the MIT protocol artifact.
package com.meshcoreone.android.core.protocol.primitives

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.Locale

internal fun Bytes.uppercaseHexString(separator: String = ""): String =
    if (separator.isEmpty()) hexString.uppercase(Locale.ROOT) else
        hexString.chunked(2).joinToString(separator) { it.uppercase(Locale.ROOT) }

internal fun parseFormattedHex(text: String): Bytes? {
    val digits = text.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (digits.length % 2 != 0) return null
    return Bytes.parseHex(digits)
}
