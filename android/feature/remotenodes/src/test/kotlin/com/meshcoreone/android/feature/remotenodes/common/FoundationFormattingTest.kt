// AndroidOnly: WP-313 Native checks of the Foundation number/ordering reproductions against swiftc oracle output.
package com.meshcoreone.android.feature.remotenodes.common

import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

/** Expected strings are swiftc output (oracle sources under `docs/android/evidence/WP-313/oracles`). */
class FoundationFormattingTest {
    @Test
    fun `fixed precision rounds half-even on the shortest decimal`() {
        val cases = listOf(
            Triple(2.675, 2, "2.68"), Triple(1.005, 2, "1.00"), Triple(0.125, 2, "0.12"), Triple(0.135, 2, "0.14"),
            Triple(3.85, 1, "3.8"), Triple(3.8505, 1, "3.9"), Triple(3.8505, 2, "3.85"), Triple(2.5, 0, "2"),
            Triple(1.5, 0, "2"), Triple(0.5, 0, "0"), Triple(1234.5, 0, "1,234"), Triple(1234.5, 2, "1,234.50"),
            Triple(99999.995, 2, "100,000.00"), Triple(12345678.25, 1, "12,345,678.2"), Triple(-122.40125, 3, "-122.401"),
            Triple(37.78475, 3, "37.785"), Triple(1e7, 0, "10,000,000"), Triple(0.1 + 0.2, 2, "0.30"),
        )
        cases.forEach { (value, digits, expected) ->
            assertEquals(expected, SwiftNumberFormat.fixed(value, digits, Locale.US), "$value/$digits")
        }
    }

    @Test
    fun `negative values rounding to zero keep their sign`() {
        assertEquals("-0", SwiftNumberFormat.fixed(-0.04, 0, Locale.US))
        assertEquals("-0.0", SwiftNumberFormat.fixed(-0.05, 1, Locale.US))
        assertEquals("-0.00", SwiftNumberFormat.fixed(-0.0, 2, Locale.US))
        assertEquals("-0.00", SwiftNumberFormat.fixed(-0.0049, 2, Locale.US))
        assertEquals("-0", SwiftNumberFormat.number(-0.0, Locale.US))
    }

    @Test
    fun `default number style caps at six fraction digits`() {
        val cases = listOf(
            1.0 / 3.0 to "0.333333", 2.0 / 3.0 to "0.666667", 123456.789012345 to "123,456.789012", 1e-7 to "0",
            0.000001234 to "0.000001", 3.0 to "3", 3.8505 to "3.8505", 0.1 + 0.2 to "0.3", 1e20 to "100,000,000,000,000,000,000",
        )
        cases.forEach { (value, expected) -> assertEquals(expected, SwiftNumberFormat.number(value, Locale.US), "$value") }
    }

    @Test
    fun `locale separators follow the locale`() {
        assertEquals("2,68", SwiftNumberFormat.fixed(2.675, 2, Locale.GERMANY))
        assertEquals("1.234,50", SwiftNumberFormat.fixed(1234.5, 2, Locale.GERMANY))
        assertEquals("12.345.678,25", SwiftNumberFormat.number(12345678.25, Locale.GERMANY))
        assertEquals("1,234,567", SwiftNumberFormat.integer(1_234_567, Locale.US))
        assertEquals("4.294.967.295", SwiftNumberFormat.integer(4_294_967_295, Locale.GERMANY))
        assertEquals("-1,234", SwiftNumberFormat.integer(-1234, Locale.US))
    }

    @Test
    fun `localized standard order matches Foundation`() {
        val words = listOf(
            "a10", "a2", "A1", "b", "Á", "a", "A", "á", "Repeater 10", "Repeater 9", "repeater 9", "Zeta",
            "alpha", "Alpha", "MCU temperature", "Temperature", "Humidity", "Voltage", "x01", "x1", "x001", "ä",
            "z", "Ab", "aB", "", " a",
        )
        val expected = listOf(
            "", " a", "a", "A", "á", "Á", "ä", "A1", "a2", "a10", "aB", "Ab", "alpha", "Alpha", "b",
            "Humidity", "MCU temperature", "repeater 9", "Repeater 9", "Repeater 10", "Temperature", "Voltage", "x1",
            "x01", "x001", "z", "Zeta",
        )
        assertEquals(expected, words.sortedWith(LocalizedStandardOrder(Locale.US)))
    }
}
