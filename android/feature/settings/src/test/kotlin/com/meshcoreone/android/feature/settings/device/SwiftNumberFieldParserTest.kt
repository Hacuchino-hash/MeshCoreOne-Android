// AndroidOnly: WP-317 Expectations are the printed output of docs/android/evidence/WP-317/oracle.swift.txt and oracle2.swift.txt.
package com.meshcoreone.android.feature.settings.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwiftNumberFieldParserTest {
    private fun double(text: String) = SwiftNumberFieldParser.parsePosixDouble(text)

    @Test
    fun `posix double matches the oracle`() {
        val table = mapOf(
            "915" to 915.0, "915.0" to 915.0, "915.125" to 915.125, "915.1254" to 915.1254, " 915" to 915.0, "915 " to 915.0,
            "915,5" to 915.0, "1,915.5" to 1.0, "1e3" to 1000.0, "-1" to -1.0, "+915" to 915.0, ".5" to 0.5, "5." to 5.0,
            "915abc" to 915.0, "0x10" to 0.0, "٩١٥" to 915.0, "915.5.5" to 915.5, "1_000" to 1.0, "９１５" to 915.0,
            "1e" to 1.0, "1e+2" to 100.0, "1E2" to 100.0, "1e-2" to 0.01, "-1e1" to -10.0, "- 5" to -5.0, "5-" to 5.0,
            "\t915" to 915.0, "915\n" to 915.0, "1.5e1" to 15.0, "1.e1" to 10.0, "-.5" to -0.5, "+.5" to 0.5,
            "Infinity" to Double.POSITIVE_INFINITY, "1e400" to Double.POSITIVE_INFINITY, "1e-400" to 0.0, "0.0005" to 0.0005,
            "915.00049999" to 915.00049999, "9,15" to 9.0, "1 000" to 1.0, "00915.0" to 915.0,
        )
        for ((text, expected) in table) assertEquals(expected, double(text), "[$text]")
        assertTrue(double("nan")!!.isNaN())
        assertTrue(double("NaN")!!.isNaN())
        assertEquals(-0.0, double("-0"))
        for (text in listOf("", "-", "abc", "abc915", "inf", "-inf", "  ")) assertNull(double(text), "[$text]")
    }

    @Test
    fun `integer matches the oracle`() {
        val table = mapOf(
            "20" to 20, "-9" to -9, "+5" to 5, " 7" to 7, "7 " to 7, "1,5" to 1, "1,000" to 1000, "7.9" to 7, "7.0" to 7,
            "7abc" to 7, "٧" to 7, "1e1" to 10, "−5" to -5, "12,34" to 1234, "1,00" to 100, "1,0000" to 10000,
            "1234,567" to 1234567, "1,234,567" to 1234567, "1,000.5" to 1000, "1.5e1" to 15, "1e" to 1, "1e+2" to 100, "1E2" to 100,
            "-1e1" to -10, "- 5" to -5, "+ 5" to 5, "1 000" to 1000, "0" to 0, "-0" to 0, "+0" to 0, "00012" to 12,
            "\t7" to 7, "7\n" to 7, "1.e1" to 10, ".5" to 0, "-.5" to 0, "1.9" to 1, "-1.9" to -1, "4,200" to 4200, "4200.5" to 4200,
            "4200mV" to 4200,
        )
        for ((text, expected) in table) assertEquals(expected, SwiftNumberFieldParser.parseInteger(text), "[$text]")
        for (text in listOf("", "-", "abc", "99999999999999999999", "1e30", "  ")) assertNull(SwiftNumberFieldParser.parseInteger(text), "[$text]")
    }

    @Test
    fun `long range edges match the oracle`() {
        assertEquals(Long.MAX_VALUE, SwiftNumberFieldParser.parseLong("9223372036854775807"))
        assertNull(SwiftNumberFieldParser.parseLong("9223372036854775808"))
        assertEquals(Long.MIN_VALUE, SwiftNumberFieldParser.parseLong("-9223372036854775808"))
    }

    @Test
    fun `ble pin parse is strict UInt32`() {
        val table = mapOf("123456" to 123456u, "000000" to 0u, "099999" to 99999u, "+123456" to 123456u, "12345" to 12345u,
            "1000000" to 1_000_000u, "4294967295" to 4294967295u)
        for ((text, expected) in table) assertEquals(expected, SwiftNumberFieldParser.parseUInt32Strict(text), "[$text]")
        for (text in listOf("-123456", " 123456", "123456 ", "１２３４５６", "123456\n", "", "4294967296", "0x1E240", "1e5", "+"))
            assertNull(SwiftNumberFieldParser.parseUInt32Strict(text), "[$text]")
    }

    @Test
    fun `frequency display is three fixed digits`() {
        val table = mapOf(915_000u to "915.000", 869_525u to "869.525", 433_175u to "433.175", 150_000u to "150.000",
            2_500_000u to "2500.000", 7_800u to "7.800", 0u to "0.000")
        for ((khz, expected) in table) assertEquals(expected, formatFrequencyMHz(khz))
    }
}
