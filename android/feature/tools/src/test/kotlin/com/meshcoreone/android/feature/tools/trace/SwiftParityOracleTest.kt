// AndroidOnly: WP-314 Vectors recorded from swiftc oracles (docs/android/evidence/WP-314/oracles) for Foundation text, collation and CLLocation distance.
package com.meshcoreone.android.feature.tools.trace

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwiftParityOracleTest {
    @Test
    fun `whitespaces is Zs plus tab and zero width space, not newlines or BOM`() {
        // Recorded scan of every scalar (oracles/ws.out.txt).
        val oracle = "0009 0020 00A0 1680 2000 2001 2002 2003 2004 2005 2006 2007 2008 2009 200A 200B 202F 205F 3000"
            .split(" ").map { it.toInt(16) }
        val measured = (0..0x10FFFF).filter(SwiftText::isWhitespace)
        assertEquals(oracle, measured)
        assertEquals("1A", SwiftText.trimWhitespaces(" 1A\t"))
        assertEquals("1A", SwiftText.trimWhitespaces(" 1A　"))
        assertEquals("\n1A", SwiftText.trimWhitespaces("\n1A"))
        assertEquals("1A ", SwiftText.trimWhitespaces("1A "))
        assertEquals("1A", SwiftText.trimWhitespaces("​1A"))
        assertEquals("﻿1A", SwiftText.trimWhitespaces("﻿1A"))
    }

    @Test
    fun `isHexDigit accepts ASCII and fullwidth single scalars only`() {
        listOf("A", "f", "１", "Ａ", "ａ").forEach { assertTrue(SwiftText.isHexDigit(it), it) }
        listOf("Á", "１́", "g", "١").forEach { assertFalse(SwiftText.isHexDigit(it), it) }
    }

    @Test
    fun `Character counts, comma splitting and case mapping match Swift`() {
        assertEquals(2, SwiftText.characters("ÁB").size)
        assertEquals(1, SwiftText.characters("🇺🇸").size)
        assertEquals(listOf("A3,́B7", "C1"), SwiftText.splitOnComma("A3,́B7,C1"))
        assertEquals("SS", SwiftText.uppercased("ß"))
        assertEquals("FF", SwiftText.uppercased("ﬀ"))
        assertEquals("I", SwiftText.uppercased("ı"))
        assertEquals("I", SwiftText.uppercased("i"))
    }

    @Test
    fun `bulk codes follow the oracle for fullwidth, ligature, canonical duplicates and newline`() {
        val resolve = { _: Bytes -> ResolvedHopCode(Bytes.of(0xFF), "Any") }
        fun statuses(input: String, size: Int = 1) =
            HopCodeParser.classify(input, size, emptySet(), null, resolve).map { it.code to it.status::class.simpleName }
        // UInt8(radix:) rejects fullwidth digits even though Character.isHexDigit accepts them.
        assertEquals(listOf("１Ａ" to "InvalidFormat"), statuses("１Ａ"))
        // The ligature uppercases to "FF", which is valid hex.
        assertEquals(listOf("FF" to "WillAdd"), statuses("ﬀ"))
        // Swift String equality is canonical, so these two spellings de-duplicate.
        assertEquals(1, statuses("É,É").size)
        // A newline is not in .whitespaces, so the code stays invalid.
        assertEquals(listOf("\nA3" to "InvalidFormat"), statuses("\nA3"))
        // A comma followed by a combining mark is one Character, so nothing splits.
        assertEquals(listOf("A3,́B7" to "InvalidFormat"), statuses("A3,́B7"))
        assertEquals(listOf("A3" to "WillAdd", "B7" to "WillAdd"), statuses(" a3 , b7​"))
        assertEquals(listOf("SS" to "InvalidFormat"), statuses("ß"))
        // Fullwidth digits do count when inferring a pasted width.
        assertEquals(0u.toUByte(), TraceHashModes.inferredTraceHashMode("１Ａ"))
        assertEquals(0u.toUByte(), TraceHashModes.inferredTraceHashMode("1A,２Ｂ"))
        assertEquals(null, TraceHashModes.inferredTraceHashMode("ÁB"))
        assertEquals(null, TraceHashModes.inferredTraceHashMode("1A\n,2B"))
    }

    @Test
    fun `localizedStandardCompare and localizedCaseInsensitiveCompare orderings`() {
        val standard = SourceCollation.localizedStandard(Locale.US)
        val insensitive = SourceCollation.localizedCaseInsensitive(Locale.US)
        // (left, right, standard, caseInsensitive) from the oracle.
        val vectors = listOf(
            Triple("Node 9", "node 10", -1 to 1),
            Triple("alpha", "Alpha", -1 to 0),
            Triple("e", "é", -1 to -1),
            Triple("Zed", "apple", 1 to 1),
            Triple("a10", "a9", 1 to -1),
            Triple("B", "a", 1 to 1),
            Triple("x", "X1", -1 to -1),
        )
        for ((left, right, expected) in vectors) {
            assertEquals(expected.first, standard.compare(left, right).sign, "standard $left/$right")
            assertEquals(expected.second, insensitive.compare(left, right).sign, "insensitive $left/$right")
        }
    }

    @Test
    fun `ellipsoidal distance tracks CLLocation within half a percent`() {
        // CLLocation.distance(from:) on macOS for the same pairs.
        val vectors = listOf(
            doubleArrayOf(37.7749, -122.4194, 37.8044, -122.2712, 13458.241070112),
            doubleArrayOf(37.8044, -122.2712, 37.8716, -122.2727, 7459.925978815),
            doubleArrayOf(37.8716, -122.2727, 37.7749, -122.4194, 16793.396830113),
            doubleArrayOf(37.0005, -122.0005, 37.0, -122.0, 71.132032060),
            doubleArrayOf(37.0005, -122.0005, 38.0, -123.0, 142192.932088444),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 111319.490793274),
            doubleArrayOf(51.5, -0.12, 40.7, -74.0, 5586501.473328553),
        )
        for (vector in vectors) {
            val measured = GeoDistance.meters(vector[0], vector[1], vector[2], vector[3])
            assertTrue(abs(measured - vector[4]) / vector[4] < 0.005, "measured $measured vs ${vector[4]}")
        }
        assertEquals(0.0, GeoDistance.meters(10.0, 20.0, 10.0, 20.0))
    }
}
