// AndroidOnly: WP-315 native geometry tests for the TerrainProfileCanvas port, pinned to the Swift oracle (no source test file exists).
package com.meshcoreone.android.feature.tools.los

import com.meshcoreone.android.core.model.Coordinate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TerrainProfileChartTest {
    private fun hillProfile() = HillOracle.elevations.mapIndexed { i, hex ->
        ElevationSample(Coordinate(37.7749 + i * 0.001, -122.4194), bits(hex), i * 500.0)
    }

    private fun hillInput(): TerrainProfileChartInput {
        val profile = hillProfile()
        return TerrainProfileChartInput(profile, FresnelZoneRenderer.buildProfileSamples(profile, 10.0, 15.0, 906.0, 4.0 / 3.0))
    }

    @Test
    fun `ranges ticks and axis labels match the Swift oracle`() {
        val input = hillInput()
        assertEquals(0.0..10000.0, input.xRange)
        assertBits("4057f73f4127349f", input.yRange.start, "hill.yRange.lower")
        assertBits("4062b4847914c445", input.yRange.endInclusive, "hill.yRange.upper")

        val layout = input.layout(361.0, TerrainProfileChart.CHART_HEIGHT, Locale.US)
        assertEquals(listOf("100", "120", "140 m"), layout.yLabels.map { it.text })
        assertEquals(listOf("0", "2", "4", "6", "8", "10 km"), layout.xLabels.map { it.text })
        assertEquals(3, layout.gridLines.size)
        // y labels sit 8 px left of the plot edge; x labels 10 px below the plot bottom.
        assertEquals(45.0 - 8.0, layout.yLabels.first().anchor.x)
        assertEquals(layout.coordinateSpace.yPixel(input.yRange.start) + 10.0, layout.xLabels.first().anchor.y)
    }

    @Test
    fun `pixel mapping of hill samples matches the Swift oracle`() {
        val input = hillInput()
        val coords = input.coordinateSpace(361.0, 200.0)
        val samples = input.profileSamples
        assertBits("4046800000000000", coords.xPixel(samples[0].x), "hill.px.0")
        assertBits("406413b13b13b13b", coords.yPixel(samples[0].yTerrain), "hill.py.0")
        assertBits("4062c00000000000", coords.xPixel(samples[7].x), "hill.px.7")
        assertBits("c06a5c52961d4e66", coords.yPixel(samples[7].yTerrain), "hill.py.7")
        assertBits("406e000000000000", coords.xPixel(samples[13].x), "hill.px.13")
        assertBits("4075900000000000", coords.xPixel(samples[20].x), "hill.px.20")
        assertBits("406413b13b13b139", coords.yPixel(samples[20].yTerrain), "hill.py.20")
    }

    @Test
    fun `niceStep and tickValues match the Swift oracle`() {
        val steps = listOf(
            Triple(0.37, 4, "3fb999999999999a"), Triple(1.0, 5, "3fc999999999999a"), Triple(7.0, 5, "4000000000000000"),
            Triple(12.5, 4, "4014000000000000"), Triple(1234.5, 5, "406f400000000000"), Triple(19999.0, 5, "40b3880000000000"),
            Triple(3.3e-3, 4, "3f50624dd2f1a9fc"), Triple(0.0, 4, "3ff0000000000000"), Triple(-5.0, 4, "3ff0000000000000"),
            Triple(99.99, 4, "4039000000000000"), Triple(250.0, 4, "4059000000000000"), Triple(1e6, 5, "41086a0000000000"),
        )
        steps.forEach { (range, divisions, expected) ->
            assertBits(expected, TerrainProfileChart.niceStep(range, divisions), "nice.$range.$divisions")
        }
        fun ticks(lo: Double, hi: Double, step: Double) =
            TerrainProfileChart.tickValues(lo..hi, step).map { String.format("%016x", it.toRawBits()) }
        assertEquals(listOf("8000000000000000", "4039000000000000", "4049000000000000", "4052c00000000000"), ticks(-12.3, 87.6, 25.0))
        assertEquals(7, ticks(93.7, 412.9, 50.0).size)
        // Accumulated addition drifts exactly as in Swift: 11 ticks, the last one 0.9999999999999999.
        val tenths = ticks(0.0, 1.0, 0.1)
        assertEquals(11, tenths.size)
        assertEquals("3fd3333333333334", tenths[3])
        assertEquals("3fefffffffffffff", tenths[10])
        assertEquals(8, ticks(0.0, 0.7, 0.1).size)
        assertEquals(emptyList<String>(), ticks(2.5, 2.5, 1.0))
        assertEquals(emptyList<Double>(), TerrainProfileChart.tickValues(0.0..1.0, 0.0))
    }

    @Test
    fun `x axis labels switch between whole km and one decimal`() {
        assertEquals("0.2 km", TerrainProfileChart.xAxisLabel(250.0, 250.0, isLast = true, Locale.US))
        assertEquals("0,2 km", TerrainProfileChart.xAxisLabel(250.0, 250.0, isLast = true, Locale.GERMANY))
        assertEquals("2.5", TerrainProfileChart.xAxisLabel(2500.0, 1000.0, isLast = false, Locale.US))
        assertEquals("2,5", TerrainProfileChart.xAxisLabel(2500.0, 1000.0, isLast = false, Locale.GERMANY))
        assertEquals("2 km", TerrainProfileChart.xAxisLabel(2000.0, 1000.0, isLast = true, Locale.GERMANY))
        assertEquals("0.5", TerrainProfileChart.xAxisLabel(500.0, 250.0, isLast = false, Locale.US))
        assertEquals("-12", TerrainProfileChart.yAxisLabel(-12.3, isLast = false))
        assertEquals("0 m", TerrainProfileChart.yAxisLabel(-0.0, isLast = true))
    }

    @Test
    fun `default ranges apply to empty and degenerate inputs`() {
        val empty = TerrainProfileChartInput(emptyList(), emptyList())
        assertTrue(empty.showsEmptyState)
        assertEquals(0.0..1.0, empty.xRange)
        assertEquals(0.0..100.0, empty.yRange)
        val flat = listOf(ProfileSample(0.0, 50.0, 50.0, 0.0), ProfileSample(1.0, 50.0, 50.0, 0.0))
        assertEquals(0.0..100.0, TerrainProfileChart.yRange(flat, emptyList()))
        assertEquals(0.0..1.0, TerrainProfileChart.xRange(listOf(ElevationSample(Coordinate(1.0, 1.0), 1.0, 0.5))))
    }

    @Test
    fun `polygons bands markers and lines follow the canvas draw functions`() {
        val input = hillInput()
        val layout = input.layout(361.0, 200.0, Locale.US)
        val coords = layout.coordinateSpace
        val samples = input.profileSamples
        val segment = layout.fresnelSegments.single()
        assertEquals(42, segment.outerFill.size)
        assertEquals(coords.point(samples[0].x, samples[0].yTop), segment.outerFill.first())
        assertEquals(coords.point(samples[0].x, samples[0].yVisibleBottom), segment.outerFill.last())
        assertEquals(coords.point(samples[20].x, samples[20].yVisibleBottom60), segment.innerFill[21])
        assertEquals(samples.map { coords.point(it.x, it.yBottom) }, segment.bottomBoundary)
        assertEquals(23, layout.terrainFill.size)
        assertEquals(coords.point(0.0, coords.yRange.start), layout.terrainFill.first())
        assertEquals(coords.point(10000.0, coords.yRange.start), layout.terrainFill.last())
        assertEquals(21, layout.terrainStroke.size)
        // Samples 1..19 are obstructed (oracle hill.*.isObstructed): one full-height band.
        val band = layout.obstructionBands.single()
        assertEquals(coords.xPixel(500.0), band.left)
        assertEquals(coords.xPixel(9500.0), band.right)
        assertEquals(coords.yPixel(coords.yRange.endInclusive), band.top)
        assertEquals(coords.yPixel(coords.yRange.start), band.bottom)
        assertEquals(ChartSegment(coords.point(0.0, samples[0].yLOS), coords.point(10000.0, samples[20].yLOS)), layout.losLines.single())
        assertEquals(coords.point(0.0, samples[0].yLOS), layout.endpointA)
        assertEquals(coords.point(10000.0, samples[20].yLOS), layout.endpointB)
        assertNull(layout.junctionSeparator)
        assertNull(layout.repeaterMarker)
    }

    @Test
    fun `single obstructed sample gets a minimum width band`() {
        val samples = listOf(
            ProfileSample(0.0, 0.0, 10.0, 0.0), ProfileSample(5000.0, 9.0, 10.0, 5.0), ProfileSample(10000.0, 0.0, 10.0, 0.0),
        )
        val profile = samples.map { ElevationSample(Coordinate(1.0, 1.0), 0.0, it.x) }
        val layout = TerrainProfileChartInput(profile, samples).layout(361.0, 200.0, Locale.US)
        val band = layout.obstructionBands.single()
        val center = layout.coordinateSpace.xPixel(5000.0)
        assertEquals(center - 2.0, band.left)
        assertEquals(center + 2.0, band.right)
    }

    @Test
    fun `relay layout joins terrain draws both segments and places the repeater marker`() {
        val profile = hillProfile()
        val ar = FresnelZoneRenderer.buildProfileSamples(profile.subList(0, 11), 10.0, 12.0, 906.0, 1.0)
        val rb = FresnelZoneRenderer.buildProfileSamples(profile.subList(10, 21), 12.0, 15.0, 906.0, 1.0)
        val input = TerrainProfileChartInput(profile, ar, rb, repeaterPathFraction = 0.5, repeaterHeight = 12.0)
        val layout = input.layout(361.0, 200.0, Locale.US, nudgeOffset = TerrainProfileChart.NUDGE_OFFSET)
        val coords = layout.coordinateSpace
        assertEquals(2, layout.fresnelSegments.size)
        assertEquals(2, layout.losLines.size)
        assertEquals(21, layout.terrainStroke.size, "junction sample is drawn once")
        assertEquals(coords.point(10000.0, rb.last().yLOS), layout.endpointB)
        val marker = assertNotNull(layout.repeaterMarker)
        val los = coords.point(5000.0, ar.last().yLOS)
        assertEquals(ChartPoint(los.x + 4.0, los.y), marker.center)
        assertEquals(ChartPoint(los.x + 4.0, coords.yPixel(ar.last().yTerrain)), marker.stem.start)
        assertEquals(marker.center, input.repeaterMarkerCenter(361.0, 200.0, nudgeOffset = 4.0))
        assertNull(input.repeaterMarkerCenter(0.0, 0.0))
        assertFalse(input.isOffPath)
        assertNull(layout.junctionSeparator)
        assertNull(input.copy(repeaterHeight = null).layout(361.0, 200.0, Locale.US).repeaterMarker)
    }

    @Test
    fun `off-path layout draws the junction separator and indirect-route label`() {
        val profile = hillProfile()
        val input = TerrainProfileChartInput(
            profile, FresnelZoneRenderer.buildProfileSamples(profile, 10.0, 15.0, 906.0, 1.0),
            repeaterPathFraction = 0.4, repeaterHeight = 10.0, segmentARDistanceMeters = 4000.0, segmentRBDistanceMeters = 6000.0,
        )
        val layout = input.layout(361.0, 200.0, Locale.US)
        val coords = layout.coordinateSpace
        assertTrue(input.isOffPath)
        assertTrue(input.showsIndirectRouteLabel)
        assertTrue(input.showsRepeaterLegend)
        assertEquals(
            ChartSegment(coords.point(4000.0, coords.yRange.endInclusive), coords.point(4000.0, coords.yRange.start)),
            layout.junctionSeparator,
        )
    }

    @Test
    fun `drag mapping and nudge rule match the canvas`() {
        assertBits("3fc7777777777777", TerrainProfileChart.dragPathFraction(100.0, 361.0), "drag.frac.100")
        assertBits("3fa999999999999a", TerrainProfileChart.dragPathFraction(0.0, 361.0), "drag.frac.0")
        assertBits("3fee666666666666", TerrainProfileChart.dragPathFraction(400.0, 361.0), "drag.frac.400")
        assertTrue(TerrainProfileChart.shouldNudge(null, 0.5, hasAnimatedNudge = false, isOffPath = false))
        assertFalse(TerrainProfileChart.shouldNudge(0.4, 0.5, hasAnimatedNudge = false, isOffPath = false))
        assertFalse(TerrainProfileChart.shouldNudge(null, 0.5, hasAnimatedNudge = true, isOffPath = false))
        assertFalse(TerrainProfileChart.shouldNudge(null, 0.5, hasAnimatedNudge = false, isOffPath = true))
    }
}
