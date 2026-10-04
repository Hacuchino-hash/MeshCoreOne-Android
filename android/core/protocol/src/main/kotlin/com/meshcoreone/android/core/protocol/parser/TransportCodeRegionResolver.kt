// PortedFrom: MeshCore/Sources/MeshCore/Protocol/TransportCodeRegionResolver.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: JCA HMAC and JVM localized numeric collation; callers retain ownership of scope-key caches.
package com.meshcoreone.android.core.protocol.parser

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.EventList
import com.meshcoreone.android.core.protocol.model.sha256
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class RegionScopeKey(val name: String, val key: Bytes)

sealed interface RegionMatchResult {
    data object None : RegionMatchResult
    data class Unique(val name: String) : RegionMatchResult
    data class Ambiguous(val names: EventList<String>) : RegionMatchResult {
        constructor(names: Collection<String>) : this(EventList(names))
    }
}

object TransportCodeRegionResolver {
    fun deriveScopeKey(regionName: String): Bytes? {
        val name = regionName.trimWhitespacesAndNewlines()
        if (name.isEmpty() || name.startsWith("$")) return null
        return sha256(Bytes.utf8(if (name.startsWith("#")) name else "#$name")).prefix(16)
    }

    fun calcTransportCode(scopeKey: Bytes, payloadTypeBits: UByte, payload: Bytes): UShort {
        val mac = Mac.getInstance("HmacSHA256")
        // SecretKeySpec rejects empty keys, although HMAC (and CryptoKit) permits them.
        // Pass those exact bytes to the vetted JCA implementation, without substitution.
        val key = if (scopeKey.isEmpty) EmptyHmacKey else SecretKeySpec(scopeKey.toByteArray(), "HmacSHA256")
        mac.init(key)
        mac.update((payloadTypeBits.toInt() and 15).toByte())
        val result = mac.doFinal(payload.toByteArray())
        return rewriteReservedCode(((result[0].toInt() and 255) or ((result[1].toInt() and 255) shl 8)).toUShort())
    }

    internal fun rewriteReservedCode(rawCode: UShort): UShort = when (rawCode.toInt()) {
        0 -> 1u
        0xffff -> 0xfffeu
        else -> rawCode
    }

    fun matchRegions(
        scopeKeys: Collection<RegionScopeKey>,
        expectedTransportCode0: UShort,
        payloadTypeBits: UByte,
        payload: Bytes,
        locale: Locale = Locale.getDefault(),
    ): RegionMatchResult {
        val names = scopeKeys.mapNotNull { scope ->
            val name = scope.name.trimWhitespacesAndNewlines()
            if (name.isNotEmpty() && calcTransportCode(scope.key, payloadTypeBits, payload) == expectedTransportCode0) name else null
        }.distinctBy { Normalizer.normalize(it, Normalizer.Form.NFC) }.sortedWith(naturalComparator(locale))
        return when (names.size) {
            0 -> RegionMatchResult.None
            1 -> RegionMatchResult.Unique(names.single())
            else -> RegionMatchResult.Ambiguous(names)
        }
    }

    private object EmptyHmacKey : SecretKey {
        override fun getAlgorithm(): String = "HmacSHA256"
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = byteArrayOf()
    }

    private fun naturalComparator(locale: Locale): Comparator<String> {
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.SECONDARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
        return Comparator { left, right ->
            val a = chunks(left)
            val b = chunks(right)
            var result = 0
            for (index in 0 until minOf(a.size, b.size)) {
                val x = a[index]
                val y = b[index]
                result = if (x[0] in '0'..'9' && y[0] in '0'..'9') {
                    val nx = x.trimStart('0')
                    val ny = y.trimStart('0')
                    nx.length.compareTo(ny.length).takeUnless { it == 0 } ?: nx.compareTo(ny)
                } else collator.compare(x, y)
                if (result != 0) break
            }
            if (result == 0) result = a.size.compareTo(b.size)
            if (result == 0) left.compareTo(right) else result
        }
    }

    private fun chunks(name: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (index in 1 until name.length) {
            if ((name[index] in '0'..'9') != (name[index - 1] in '0'..'9')) {
                result += name.substring(start, index)
                start = index
            }
        }
        if (start < name.length) result += name.substring(start)
        return result
    }
}
