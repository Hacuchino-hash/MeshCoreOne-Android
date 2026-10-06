// AndroidOnly: WP-210 Pins the Swift Foundation text semantics (Double description/parsing, hex, rounding) that keep exports byte-identical to iOS.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.TestFactory

class NodeConfigSwiftTextTest {
    @TestFactory
    fun nativeCases() = nodeConfigNativeCases(
        "describe matches Swift Double.description for coordinates and edge values" to {
            val expected = linkedMapOf(
                0.0 to "0.0", -0.0 to "-0.0", 47.6062 to "47.6062", -122.3321 to "-122.3321", 47.43 to "47.43",
                -120.36 to "-120.36", 1.0 to "1.0", 180.0 to "180.0", 0.1 to "0.1", 0.001 to "0.001", 0.0001 to "0.0001",
                0.00001 to "1e-05", -0.000015 to "-1.5e-05", 1.0E-7 to "1e-07", 0.1 + 0.2 to "0.30000000000000004",
                123456.789 to "123456.789", 9007199254740992.0 to "9007199254740992.0",
                1e16 to "1e+16", 1e15 to "1000000000000000.0", 12345678901234567.0 to "1.2345678901234568e+16", 100000.0 to "100000.0",
                Double.MAX_VALUE to "1.7976931348623157e+308", Double.MIN_VALUE to "5e-324",
                Double.NaN to "nan", Double.POSITIVE_INFINITY to "inf", Double.NEGATIVE_INFINITY to "-inf",
            )
            for ((value, text) in expected) assertEquals(text, NodeConfigSwiftText.describe(value), "describe($value)")
        },
        "describe round-trips scaled device coordinates" to {
            for (raw in listOf(1, 7, 10, 999_999, 47_606_200, -122_332_100, 90_000_000, -180_000_000, 123_457)) {
                val value = raw / 1_000_000.0
                assertEquals(value, NodeConfigSwiftText.parseDouble(NodeConfigSwiftText.describe(value)))
            }
        },
        "parseDouble accepts Swift literals and rejects JVM-only syntax" to {
            assertEquals(47.6, NodeConfigSwiftText.parseDouble("47.6"))
            assertEquals(-0.5, NodeConfigSwiftText.parseDouble("-.5"))
            assertEquals(5.0, NodeConfigSwiftText.parseDouble("+5."))
            assertEquals(1500.0, NodeConfigSwiftText.parseDouble("1.5e3"))
            assertEquals(16.0, NodeConfigSwiftText.parseDouble("0x10"))
            assertEquals(3.0, NodeConfigSwiftText.parseDouble("0x1.8p1"))
            assertEquals(Double.NEGATIVE_INFINITY, NodeConfigSwiftText.parseDouble("-Infinity"))
            assertEquals(Double.POSITIVE_INFINITY, NodeConfigSwiftText.parseDouble("inf"))
            assertTrue(assertNotNull(NodeConfigSwiftText.parseDouble("nan")).isNaN())
            for (text in listOf("", " 1", "1 ", "1.5d", "2f", "1e", "abc", "1,5", "0x", "--1", ".")) {
                assertNull(NodeConfigSwiftText.parseDouble(text), "parseDouble('$text')")
            }
        },
        "strictHexBytes requires contiguous even-length ASCII hex" to {
            assertEquals(Bytes.of(0xAB, 0xCD), NodeConfigSwiftText.strictHexBytes("aBcD"))
            assertEquals(Bytes.EMPTY, NodeConfigSwiftText.strictHexBytes(""))
            for (text in listOf("abc", "zz", "ab cd", "ab-", "ａｂ", "+1")) assertNull(NodeConfigSwiftText.strictHexBytes(text), text)
        },
        "graphemeCount counts characters like Swift String.count" to {
            assertEquals(4, NodeConfigSwiftText.graphemeCount("abcd"))
            assertEquals(2, NodeConfigSwiftText.graphemeCount("a🙂"))
            assertEquals(1, NodeConfigSwiftText.graphemeCount("é"))
            assertEquals(1, NodeConfigSwiftText.graphemeCount("👩‍👩‍👧"))
        },
        "rounding is half away from zero and UInt32 conversion saturates instead of trapping" to {
            assertEquals(3.0, NodeConfigSwiftText.roundedHalfAwayFromZero(2.5))
            assertEquals(-3.0, NodeConfigSwiftText.roundedHalfAwayFromZero(-2.5))
            assertEquals(0.0, NodeConfigSwiftText.roundedHalfAwayFromZero(0.49999999999999994))
            assertEquals(512_002.0, NodeConfigSwiftText.roundedHalfAwayFromZero(512.002 * 1000))
            assertEquals(0u, NodeConfigSwiftText.saturatingUInt32(Double.NaN))
            assertEquals(0u, NodeConfigSwiftText.saturatingUInt32(-5.0))
            assertEquals(UInt.MAX_VALUE, NodeConfigSwiftText.saturatingUInt32(1e12))
            assertEquals(42u, NodeConfigSwiftText.saturatingUInt32(42.9))
        },
        "epoch seconds truncate toward zero like Int(timeIntervalSince1970)" to {
            assertEquals(-1, NodeConfigSwiftText.truncatedEpochSeconds(Instant.ofEpochSecond(-2, 500_000_000)))
            assertEquals(5, NodeConfigSwiftText.truncatedEpochSeconds(Instant.ofEpochSecond(5, 999_999_999)))
            assertEquals(0u, Instant.ofEpochSecond(-10).nodeConfigEpochUInt32())
            assertEquals(UInt.MAX_VALUE, Instant.ofEpochSecond(UInt.MAX_VALUE.toLong() + 10).nodeConfigEpochUInt32())
        },
    )
}
