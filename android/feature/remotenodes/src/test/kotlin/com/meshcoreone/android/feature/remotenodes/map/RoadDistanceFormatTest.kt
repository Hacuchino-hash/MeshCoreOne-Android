// AndroidOnly: WP-313 Road-usage distance approximation checked against Foundation output (oracle map_road_usage.swift.txt).
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.feature.remotenodes.telemetry.MeasurementSystem
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

/** Every expected string below was printed by Foundation (macOS swiftc) for the same input. */
class RoadDistanceFormatTest {
    private fun assertFormats(locale: Locale, system: MeasurementSystem, cases: List<Pair<Double, String>>) {
        for ((meters, expected) in cases) {
            assertEquals(expected, RoadDistanceFormat.format(meters, locale, system), "$meters m ($locale, $system)")
        }
    }

    @Test
    fun metricLadderMatchesFoundation() {
        assertFormats(
            Locale.forLanguageTag("en-AU"), MeasurementSystem.METRIC,
            listOf(
                0.0 to "0 m", 0.5 to "0 m", 1.0 to "1 m", 1.5 to "2 m", 2.5 to "2 m", 3.5 to "4 m", 4.0 to "4 m",
                9.0 to "9 m", 12.0 to "10 m", 14.9 to "10 m", 15.0 to "20 m", 25.0 to "20 m", 35.0 to "40 m",
                45.0 to "40 m", 155.0 to "160 m", 250.0 to "250 m", 299.0 to "300 m", 304.0 to "300 m",
                325.0 to "300 m", 375.0 to "400 m", 425.0 to "400 m", 804.0 to "800 m", 805.0 to "800 m",
                905.0 to "0.9 km", 915.0 to "0.92 km", 950.0 to "0.95 km", 999.0 to "1 km", 1_050.0 to "1 km",
                1_150.0 to "1.2 km", 1_250.0 to "1.2 km", 1_500.0 to "1.5 km", 2_414.0 to "2.4 km",
                9_999.0 to "10 km", 10_500.0 to "10 km", 11_500.0 to "12 km", 15_123.0 to "15 km",
                15_550.0 to "16 km", 99_500.0 to "100 km", 123_456.0 to "123 km", 1_234_567.0 to "1,235 km",
            ),
        )
    }

    @Test
    fun usLadderUsesFeetThenMiles() {
        assertFormats(
            Locale.US, MeasurementSystem.US,
            listOf(
                0.0 to "0 ft", 0.1524 to "0 ft", 0.4572 to "1 ft", 1.0 to "3 ft", 4.0 to "10 ft", 4.572 to "10 ft", 5.0 to "20 ft", 7.62 to "20 ft",
                9.0 to "30 ft", 12.0 to "40 ft", 14.9 to "50 ft", 38.1 to "100 ft", 49.0 to "150 ft",
                50.0 to "150 ft", 53.34 to "200 ft", 99.0 to "300 ft", 100.0 to "350 ft", 149.0 to "500 ft",
                250.0 to "800 ft", 299.0 to "1,000 ft", 400.0 to "1,300 ft", 500.0 to "1,650 ft",
                804.0 to "2,650 ft", 805.0 to "0.5 mi", 950.0 to "0.59 mi", 1_000.0 to "0.62 mi",
                1_049.0 to "0.65 mi", 1_500.0 to "0.93 mi", 1_609.0 to "1 mi", 1_700.0 to "1.1 mi",
                2_414.0 to "1.5 mi", 5_000.0 to "3.1 mi", 15_550.0 to "9.7 mi", 100_000.0 to "62 mi",
                123_456.0 to "77 mi", 1_986_574.2336000002 to "1,234 mi",
            ),
        )
    }

    @Test
    fun ukLadderUsesYardsThenMiles() {
        assertFormats(
            Locale.UK, MeasurementSystem.UK,
            listOf(
                0.0 to "0 yd", 1.0 to "1 yd", 4.0 to "4 yd", 9.0 to "10 yd", 12.0 to "10 yd", 14.9 to "20 yd",
                49.0 to "50 yd", 99.0 to "100 yd", 149.0 to "150 yd", 250.0 to "250 yd", 299.0 to "350 yd",
                400.0 to "450 yd", 500.0 to "550 yd", 804.0 to "900 yd", 805.0 to "0.5 mi", 5_000.0 to "3.1 mi",
            ),
        )
    }

    @Test
    fun appLanguagesUseFoundationSymbolsAndSeparators() {
        val metric = MeasurementSystem.METRIC
        assertFormats(Locale.GERMANY, metric, listOf(3.0 to "3 m", 950.0 to "0,95 km", 1_500.0 to "1,5 km"))
        assertFormats(Locale.FRANCE, metric, listOf(500.0 to "500 m", 1_500.0 to "1,5 km"))
        assertFormats(Locale.KOREA, metric, listOf(500.0 to "500m", 5_000.0 to "5km"))
        assertFormats(Locale.SIMPLIFIED_CHINESE, metric, listOf(3.0 to "3米", 1_500.0 to "1.5公里"))
        assertFormats(Locale.forLanguageTag("ru-RU"), metric, listOf(500.0 to "500 м", 1_500.0 to "1,5 км"))
        assertFormats(Locale.forLanguageTag("uk-UA"), metric, listOf(500.0 to "500 м", 5_000.0 to "5 км"))
        assertFormats(Locale.forLanguageTag("fr-US"), MeasurementSystem.US, listOf(3.0 to "10 pi"))
        assertFormats(Locale.forLanguageTag("zh-Hans-US"), MeasurementSystem.US, listOf(100.0 to "350英尺"))
        assertFormats(Locale.forLanguageTag("ko-US"), MeasurementSystem.US, listOf(3.0 to "10ft"))
    }

    @Test
    fun measureExposesUnitAndRoundedValue() {
        assertEquals(RoadMeasure(0.95, RoadUnit.KILOMETER), RoadDistanceFormat.measure(950.0, MeasurementSystem.METRIC))
        assertEquals(RoadMeasure(150.0, RoadUnit.FOOT), RoadDistanceFormat.measure(49.0, MeasurementSystem.US))
        assertEquals(RoadMeasure(3.1, RoadUnit.MILE), RoadDistanceFormat.measure(5_000.0, MeasurementSystem.UK))
        assertEquals(RoadUnit.METER, RoadDistanceFormat.measure(Double.NaN, MeasurementSystem.US).unit)
    }
}
